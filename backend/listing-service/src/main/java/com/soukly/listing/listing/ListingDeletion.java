package com.soukly.listing.listing;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.listing.outbox.Outbox;
import com.soukly.listing.photo.PhotoService;

/**
 * Deletion of a listing by its seller and purge of the deleted listings (ANN-05). A draft is erased at once with its
 * photos and no event; any other listing becomes {@code DELETED} with {@code listing.deleted}, disappears for everyone
 * ({@code 404} on the private endpoints, {@code 410} on its page) and is erased by {@link #purge()} after
 * {@link DeletionProperties#purgeAfter()}. Apart from {@link ListingService} because it needs the photos, which
 * depend on the listings.
 */
@Service
@EnableConfigurationProperties(DeletionProperties.class)
public class ListingDeletion {

    private final ListingService listings;
    private final ListingRepository repository;
    private final ListingTransitions transitions;
    private final ListingEventFactory events;
    private final Outbox outbox;
    private final PhotoService photos;
    private final DeletionProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    ListingDeletion(ListingService listings, ListingRepository repository, ListingTransitions transitions,
            ListingEventFactory events, Outbox outbox, PhotoService photos, DeletionProperties properties,
            PlatformTransactionManager transactions, Clock clock) {
        this.listings = listings;
        this.repository = repository;
        this.transitions = transitions;
        this.events = events;
        this.outbox = outbox;
        this.photos = photos;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * Deletes a listing of {@code ownerId}.
     *
     * @throws ListingNotFoundException unknown or deleted listing, or a listing of another account
     */
    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Listing listing = listings.lockOwned(ownerId, id);
        Instant now = now();
        transitions.target(listing, ListingAction.DELETE, now);
        if (listing.status() == ListingStatus.DRAFT) {
            erase(id);
            return;
        }
        Listing deleted = listing.deleted(now);
        repository.updateStatus(deleted);
        outbox.write(events.deleted(deleted, now));
    }

    /**
     * Erases for good the listings deleted for longer than {@link DeletionProperties#purgeAfter()}: their photos
     * (originals and WebP versions), their contact reveals and their row. The events already written are left as
     * they are. Each batch is one transaction that locks its listings with {@code SKIP LOCKED}, so several instances
     * can run it at the same time.
     *
     * @return number of listings erased
     */
    public int purge() {
        Instant before = now().minus(properties.purgeAfter());
        int total = 0;
        int erased;
        do {
            Integer count = transaction.execute(status -> {
                List<UUID> ids = repository.lockDeletedBefore(before, properties.batchSize());
                ids.forEach(this::erase);
                return ids.size();
            });
            erased = count == null ? 0 : count;
            total += erased;
        } while (erased == properties.batchSize());
        return total;
    }

    /** Photos first, then the row: the reveals go with it ({@code ON DELETE CASCADE}). */
    private void erase(UUID id) {
        photos.deleteAllPhotos(id);
        repository.delete(id);
    }

    /** Truncated to the precision of PostgreSQL, so that an answer and a later reading agree. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
