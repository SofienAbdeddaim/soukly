package com.soukly.user.listing;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.event.ProcessedEvents;
import com.soukly.user.mail.ListingReminderMailer;

/**
 * Reminder email of a listing that expires soon (ANN-03), in the language of the seller. The event is recorded in
 * {@code processed_events} in the same transaction: a second delivery sends nothing, and a failed email rolls the
 * record back so that the retry sends it.
 */
@Service
class ExpiryReminders {

    private static final Logger log = LoggerFactory.getLogger(ExpiryReminders.class);

    private final ProcessedEvents processed;
    private final UserAccountRepository accounts;
    private final ListingReminderMailer mailer;
    private final Clock clock;

    ExpiryReminders(ProcessedEvents processed, UserAccountRepository accounts, ListingReminderMailer mailer,
            Clock clock) {
        this.processed = processed;
        this.accounts = accounts;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void remind(UUID eventId, UUID ownerId, UUID listingId, String title, Instant expiresAt) {
        if (!processed.markProcessed(eventId, clock.instant())) {
            log.info("Listing event {} was processed already: no second reminder", eventId);
            return;
        }
        UserAccount account = accounts.findById(ownerId).orElse(null);
        if (account == null || account.status() != AccountStatus.ACTIVE) {
            log.info("No reminder for event {}: account {} is not active", eventId, ownerId);
            return;
        }
        mailer.sendExpiryReminder(account, listingId, title, expiresAt);
    }
}
