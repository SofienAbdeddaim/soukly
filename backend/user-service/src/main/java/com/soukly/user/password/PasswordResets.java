package com.soukly.user.password;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.config.UserProperties;
import com.soukly.user.mail.AccountMail;
import com.soukly.user.registration.FieldError;
import com.soukly.user.registration.InvalidRequestException;
import com.soukly.user.registration.PasswordPolicy;
import com.soukly.user.token.OpaqueTokens;

/**
 * Forgotten password (AUTH-04): the reset links and the change of the password. Tokens are 32 random bytes in
 * Base64 URL, valid 1 h, single use; only their SHA-256 reaches the database, and a new token replaces the unused
 * ones of the account. The sessions are handled by {@code SessionService}, which calls {@link #reset} in its own
 * transaction.
 */
@Service
public class PasswordResets {

    private static final Logger log = LoggerFactory.getLogger(PasswordResets.class);

    /** Field of the new password in the reset request, used in the validation errors. */
    static final String NEW_PASSWORD = "newPassword";

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final UserProperties properties;
    private final Clock clock;

    PasswordResets(UserAccountRepository users, PasswordResetTokenRepository tokens, PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher events, UserProperties properties,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sends a reset link to the account of this email, if there is one and it is under the hourly limit. Nothing
     * tells the caller which case happened; the email is sent after the commit, on another thread.
     *
     * @param email normalized email
     */
    @Transactional
    public void requestReset(String email) {
        users.findByEmail(email)
                .filter(account -> account.status() == AccountStatus.ACTIVE
                        || account.status() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(account -> {
                    Instant now = clock.instant();
                    if (tokens.countByUserIdAndCreatedAtAfter(account.id(), now.minus(ONE_HOUR))
                            >= properties.passwordResetsPerHour()) {
                        log.info("Password reset ignored for user {}: hourly limit reached", account.id());
                        return;
                    }
                    tokens.invalidateUnused(account.id(), null, now);
                    String token = OpaqueTokens.generate();
                    tokens.save(new PasswordResetToken(account.id(), OpaqueTokens.sha256Hex(token), now,
                            now.plus(properties.passwordResetTokenTtl())));
                    events.publishEvent(new AccountMail.PasswordReset(account.id(), account.email(),
                            account.displayName(), account.preferredLanguage(), token));
                    log.info("Password reset link sent to user {}", account.id());
                });
    }

    /**
     * Uses a reset token: checks the new password, stores its hash, uses the token and invalidates the others, and
     * activates a pending account (the link proves that the user owns the mailbox). A password that breaks a rule
     * leaves the token usable.
     *
     * @return the account, for the caller to revoke its sessions and open a new one
     * @throws InvalidResetTokenException the token is unknown, expired, used or replaced
     * @throws InvalidRequestException    the new password breaks a rule
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserAccount reset(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken found = OpaqueTokens.isPlausible(token)
                ? tokens.findByTokenHash(OpaqueTokens.sha256Hex(token)).filter(t -> t.isUsable(now)).orElse(null)
                : null;
        if (found == null) {
            throw new InvalidResetTokenException();
        }
        UserAccount account = users.findById(found.userId()).orElseThrow(InvalidResetTokenException::new);
        passwordPolicy.violation(newPassword, account.email()).ifPresent(code -> {
            throw new InvalidRequestException(List.of(new FieldError(NEW_PASSWORD, code)));
        });
        if (tokens.markUsed(found.id(), now) != 1) {
            // Used by a parallel request first
            throw new InvalidResetTokenException();
        }
        tokens.invalidateUnused(account.id(), found.id(), now);
        account.changePassword(passwordEncoder.encode(newPassword), now);
        account.activate(now);
        events.publishEvent(new AccountMail.PasswordChanged(account.id(), account.email(), account.displayName(),
                account.preferredLanguage(), now));
        log.info("Password of user {} reset", account.id());
        return account;
    }

    /** Invalidates every unused reset link of the account (deletion of the account, AUTH-05). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateAll(UUID userId) {
        tokens.invalidateUnused(userId, null, clock.instant());
    }
}
