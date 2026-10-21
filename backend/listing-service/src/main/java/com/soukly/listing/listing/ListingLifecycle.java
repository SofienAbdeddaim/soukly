package com.soukly.listing.listing;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.common.event.EventEnvelope;
import com.soukly.listing.outbox.Outbox;

/**
 * Expiry and reminder task (ANN-03), every hour: published listings whose expiry date is reached become
 * {@code EXPIRED} with {@code listing.expired}, then those that expire within
 * {@link PublicationProperties#reminderBefore()} get {@code listing.expiring-soon} once (until a renewal). Each batch
 * is one transaction that locks its listings with {@code SKIP LOCKED}, so several instances can run it at the same
 * time.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ListingLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ListingLifecycle.class);

    private final ListingRepository repository;
    private final ListingEventFactory events;
    private final Outbox outbox;
    private final PublicationProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    ListingLifecycle(ListingRepository repository, ListingEventFactory events, Outbox outbox,
            PublicationProperties properties, PlatformTransactionManager transactions, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.outbox = outbox;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${soukly.listing.publication.check-interval:PT1H}",
            initialDelayString = "${soukly.listing.publication.check-initial-delay:PT1M}")
    public void run() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int expired = inBatches(limit -> repository.expireDue(now, limit), listing -> events.expired(listing, now));
        Instant until = now.plus(properties.reminderBefore());
        int reminded = inBatches(limit -> repository.markReminderDue(now, until, limit),
                listing -> events.expiringSoon(listing, now));
        if (expired > 0 || reminded > 0) {
            log.info("Listing lifecycle: {} listing(s) expired, {} expiry reminder(s) written", expired, reminded);
        }
    }

    private int inBatches(Function<Integer, List<Listing>> change,
            Function<Listing, EventEnvelope<?>> event) {
        int total = 0;
        int changed;
        do {
            Integer count = transaction.execute(status -> {
                List<Listing> listings = change.apply(properties.batchSize());
                listings.forEach(listing -> outbox.write(event.apply(listing)));
                return listings.size();
            });
            changed = count == null ? 0 : count;
            total += changed;
        } while (changed == properties.batchSize());
        return total;
    }
}
