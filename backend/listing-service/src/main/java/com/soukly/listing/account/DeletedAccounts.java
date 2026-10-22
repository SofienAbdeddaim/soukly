package com.soukly.listing.account;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.listing.listing.ListingDeletion;

/**
 * Deletes the listings of a deleted account (AUTH-05) with the rules of ANN-05. The event is recorded in
 * {@code processed_events} in the same transaction: a second delivery does nothing, and a failure rolls the record
 * back so that the retry deletes the listings.
 */
@Service
class DeletedAccounts {

    private static final Logger log = LoggerFactory.getLogger(DeletedAccounts.class);

    private final ProcessedEvents processed;
    private final ListingDeletion deletion;
    private final Clock clock;

    DeletedAccounts(ProcessedEvents processed, ListingDeletion deletion, Clock clock) {
        this.processed = processed;
        this.deletion = deletion;
        this.clock = clock;
    }

    @Transactional
    public void deleteListings(UUID eventId, UUID userId) {
        if (!processed.markProcessed(eventId, clock.instant())) {
            log.info("User event {} was processed already: listings not deleted again", eventId);
            return;
        }
        int deleted = deletion.deleteAllOf(userId);
        log.info("Account {} deleted: {} listing(s) deleted", userId, deleted);
    }
}
