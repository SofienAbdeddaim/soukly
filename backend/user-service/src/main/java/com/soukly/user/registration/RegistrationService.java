package com.soukly.user.registration;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.mail.AccountMail;
import com.soukly.user.verification.EmailVerificationTokens;
import com.soukly.user.verification.TokenReason;

/**
 * Registration, email verification and resend. The outcome is never visible to the caller: whether the email is
 * new or known, the answer is the same and only the mailbox owner learns more.
 */
@Service
class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserAccountRepository users;
    private final EmailVerificationTokens tokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RegistrationService(UserAccountRepository users, EmailVerificationTokens tokens, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void register(Registration registration) {
        // Hashed in every case so that the response time does not reveal whether the email is known.
        String passwordHash = passwordEncoder.encode(registration.password());
        Instant now = clock.instant();
        UserAccount existing = users.findByEmail(registration.email()).orElse(null);

        if (existing == null) {
            UserAccount account = users.save(UserAccount.pending(registration.email(), passwordHash,
                    registration.displayName(), registration.phone(), registration.language(), now));
            sendVerification(account, TokenReason.REGISTRATION);
            log.info("Registered user {}", account.id());
        } else if (existing.status() == AccountStatus.ACTIVE) {
            events.publishEvent(new AccountMail.AccountExists(existing.id(), existing.email(),
                    existing.displayName(), existing.preferredLanguage()));
            log.info("Registration attempt for active user {}", existing.id());
        } else if (tokens.canResend(existing.id())) {
            // The latest registration wins: a stranger who registered this email first cannot keep their
            // password once the owner registers and verifies.
            existing.updateRegistration(passwordHash, registration.displayName(), registration.phone(),
                    registration.language(), now);
            sendVerification(existing, TokenReason.RESEND);
            log.info("Registration renewed for pending user {}", existing.id());
        } else {
            log.info("Registration ignored for pending user {}: hourly resend limit reached", existing.id());
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        UserAccount account = tokens.consume(token).flatMap(users::findById)
                .orElseThrow(InvalidVerificationTokenException::new);
        account.activate(clock.instant());
        log.info("Verified email of user {}", account.id());
    }

    /** @param email normalized email */
    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(email)
                .filter(account -> account.status() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(account -> {
                    if (tokens.canResend(account.id())) {
                        sendVerification(account, TokenReason.RESEND);
                        log.info("Resent verification email to user {}", account.id());
                    } else {
                        log.info("Resend ignored for user {}: hourly limit reached", account.id());
                    }
                });
    }

    private void sendVerification(UserAccount account, TokenReason reason) {
        String token = tokens.issue(account.id(), reason);
        events.publishEvent(new AccountMail.Verification(account.id(), account.email(), account.displayName(),
                account.preferredLanguage(), token));
    }
}
