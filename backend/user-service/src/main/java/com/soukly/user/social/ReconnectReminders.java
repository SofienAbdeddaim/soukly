package com.soukly.user.social;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.event.ProcessedEvents;
import com.soukly.user.mail.SocialReconnectMailer;

/**
 * Email "connect again" of a social network connection the network refused (SOC-06), in the language of the member.
 * The event is recorded in {@code processed_events} in the same transaction: a second delivery sends nothing, and a
 * failed email rolls the record back so that the retry sends it. social-publisher already limits the events to one
 * per connection and per 24 hours.
 */
@Service
class ReconnectReminders {

    private static final Logger log = LoggerFactory.getLogger(ReconnectReminders.class);

    private final ProcessedEvents processed;
    private final UserAccountRepository accounts;
    private final SocialReconnectMailer mailer;
    private final Clock clock;

    ReconnectReminders(ProcessedEvents processed, UserAccountRepository accounts, SocialReconnectMailer mailer,
            Clock clock) {
        this.processed = processed;
        this.accounts = accounts;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void remind(UUID eventId, UUID userId, String network, String accountName) {
        if (!processed.markProcessed(eventId, clock.instant())) {
            log.info("Social event {} was processed already: no second email", eventId);
            return;
        }
        UserAccount account = accounts.findById(userId).orElse(null);
        if (account == null || account.status() != AccountStatus.ACTIVE) {
            log.info("No reconnect email for event {}: account {} is not active", eventId, userId);
            return;
        }
        mailer.sendReconnect(account, network, accountName);
    }
}
