package com.soukly.user.profile;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.common.event.UserEventEnvelope;
import com.soukly.common.event.UserEvents;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.mail.AccountMail;
import com.soukly.user.outbox.Outbox;
import com.soukly.user.password.PasswordResets;
import com.soukly.user.session.CurrentPasswordCheck;
import com.soukly.user.session.SessionRevocation;
import com.soukly.user.verification.EmailVerificationTokens;

/**
 * Deletion of the account by its owner (AUTH-05), in one transaction: the account becomes {@code DELETED} and its
 * address is free at once, every session and pending link stops working, and {@code user.deleted} goes to the outbox
 * for listing-service to delete the listings. The row stays until {@link AccountPurge} erases it.
 */
@Service
class AccountDeletion {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);

    private final UserAccountRepository users;
    private final CurrentPasswordCheck currentPassword;
    private final SessionRevocation sessions;
    private final EmailVerificationTokens verificationTokens;
    private final PasswordResets passwordResets;
    private final EmailChangeTokenRepository emailChangeTokens;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AccountDeletion(UserAccountRepository users, CurrentPasswordCheck currentPassword, SessionRevocation sessions,
            EmailVerificationTokens verificationTokens, PasswordResets passwordResets,
            EmailChangeTokenRepository emailChangeTokens, Outbox outbox, ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.currentPassword = currentPassword;
        this.sessions = sessions;
        this.verificationTokens = verificationTokens;
        this.passwordResets = passwordResets;
        this.emailChangeTokens = emailChangeTokens;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void delete(UUID userId, String current) {
        UserAccount account = ProfileService.active(users, userId);
        currentPassword.verify(account, current);
        Instant now = clock.instant();
        String email = account.email();
        account.delete(now);
        int revoked = sessions.revokeAll(userId);
        verificationTokens.expireAll(userId);
        passwordResets.invalidateAll(userId);
        emailChangeTokens.invalidateUnused(userId, now);
        // No personal data in the event: the account id only
        outbox.write(new UserEventEnvelope<>(UUID.randomUUID(), UserEvents.DELETED, UserEvents.SCHEMA_VERSION, now,
                userId, Map.of()));
        events.publishEvent(new AccountMail.AccountDeleted(userId, email, account.displayName(),
                account.preferredLanguage()));
        log.info("User {} deleted their account, {} session token(s) revoked", userId, revoked);
    }
}
