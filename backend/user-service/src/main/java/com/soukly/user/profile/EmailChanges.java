package com.soukly.user.profile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.config.UserProperties;
import com.soukly.user.mail.AccountMail;
import com.soukly.user.registration.FieldError;
import com.soukly.user.registration.InvalidRequestException;
import com.soukly.user.registration.RegistrationValidator;
import com.soukly.user.session.CurrentPasswordCheck;
import com.soukly.user.token.OpaqueTokens;

/**
 * Change of the email address in two steps (AUTH-05): the request sends a link to the new address and a notice to the
 * current one; the address changes only when the link is opened. Until then the login keeps the current address.
 *
 * <p>Whether the new address already has an account is never revealed: the answer is the same, and that address gets
 * the "you already have an account" email of AUTH-01 instead of the link. Tokens are 32 random bytes in Base64 URL,
 * valid 24 h, single use; only their SHA-256 reaches the database, and a new request replaces the previous one.
 */
@Service
class EmailChanges {

    private static final Logger log = LoggerFactory.getLogger(EmailChanges.class);

    static final String NEW_EMAIL = "newEmail";

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final UserAccountRepository users;
    private final EmailChangeTokenRepository tokens;
    private final RegistrationValidator validator;
    private final CurrentPasswordCheck currentPassword;
    private final ApplicationEventPublisher events;
    private final UserProperties properties;
    private final Clock clock;

    EmailChanges(UserAccountRepository users, EmailChangeTokenRepository tokens, RegistrationValidator validator,
            CurrentPasswordCheck currentPassword, ApplicationEventPublisher events, UserProperties properties,
            Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.validator = validator;
        this.currentPassword = currentPassword;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Asks to change the address of the account. Beyond the hourly limit nothing happens, with the same answer.
     *
     * @throws InvalidRequestException the new address is malformed or is the current one
     */
    @Transactional
    public void request(UUID userId, String rawNewEmail, String current) {
        UserAccount account = ProfileService.active(users, userId);
        currentPassword.verify(account, current);
        String newEmail = validator.validateEmail(rawNewEmail, NEW_EMAIL);
        if (newEmail.equals(account.email())) {
            throw new InvalidRequestException(List.of(new FieldError(NEW_EMAIL, "email.unchanged")));
        }
        Instant now = clock.instant();
        if (tokens.countByUserIdAndCreatedAtAfter(userId, now.minus(ONE_HOUR)) >= properties.emailChangesPerHour()) {
            log.info("Email change ignored for user {}: hourly limit reached", userId);
            return;
        }
        tokens.invalidateUnused(userId, now);
        // Issued even when the address is taken (and then never sent), so that the hourly limit counts every request
        String token = OpaqueTokens.generate();
        tokens.save(new EmailChangeToken(userId, OpaqueTokens.sha256Hex(token), newEmail, now,
                now.plus(properties.emailChangeTokenTtl())));
        account.requestEmailChange(newEmail, now);

        Optional<UserAccount> owner = users.findByEmail(newEmail);
        if (owner.isPresent()) {
            UserAccount existing = owner.get();
            events.publishEvent(new AccountMail.AccountExists(existing.id(), existing.email(),
                    existing.displayName(), existing.preferredLanguage()));
            log.info("Email change of user {} asked for the address of user {}", userId, existing.id());
        } else {
            events.publishEvent(new AccountMail.EmailChangeConfirmation(userId, newEmail, account.displayName(),
                    account.preferredLanguage(), token));
            log.info("Email change link sent for user {}", userId);
        }
        events.publishEvent(new AccountMail.EmailChangeRequested(userId, account.email(), account.displayName(),
                account.preferredLanguage(), newEmail));
    }

    /** Cancels the pending change: its link stops working. Nothing happens when there is none. */
    @Transactional
    public void cancel(UUID userId) {
        UserAccount account = ProfileService.active(users, userId);
        Instant now = clock.instant();
        tokens.invalidateUnused(userId, now);
        account.cancelEmailChange(now);
        log.info("Email change of user {} cancelled", userId);
    }

    /**
     * Uses a confirmation link: the account takes the new address. Public, as the link may be opened on another
     * device.
     *
     * @throws InvalidEmailChangeTokenException the token is unknown, expired, used, replaced or cancelled
     * @throws EmailTakenException              another account took the address since the request
     */
    @Transactional
    public void confirm(String token) {
        Instant now = clock.instant();
        EmailChangeToken found = OpaqueTokens.isPlausible(token)
                ? tokens.findByTokenHash(OpaqueTokens.sha256Hex(token)).filter(t -> t.isUsable(now)).orElse(null)
                : null;
        if (found == null) {
            throw new InvalidEmailChangeTokenException();
        }
        UserAccount account = users.findById(found.userId())
                .filter(user -> user.status() == AccountStatus.ACTIVE)
                .orElseThrow(InvalidEmailChangeTokenException::new);
        if (users.findByEmail(found.newEmail()).isPresent()) {
            log.info("Email change of user {} refused: the address was taken since the request", account.id());
            throw new EmailTakenException();
        }
        if (tokens.markUsed(found.id(), now) != 1) {
            // Used by a parallel request first
            throw new InvalidEmailChangeTokenException();
        }
        account.changeEmail(found.newEmail(), now);
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            // Registered in the meantime (unique email)
            throw new EmailTakenException();
        }
        log.info("Email of user {} changed", account.id());
    }
}
