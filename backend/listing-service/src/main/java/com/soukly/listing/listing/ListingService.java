package com.soukly.listing.listing;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.listing.outbox.Outbox;

import tools.jackson.databind.JsonNode;

/**
 * Listings of their owner: drafts (ANN-01) created and changed step by step, then published and renewed (ANN-03).
 * A listing of another account is answered exactly like an unknown listing. Every event is written in the outbox in
 * the transaction of its change.
 */
@Service
@EnableConfigurationProperties({ DraftProperties.class, PublicationProperties.class })
public class ListingService {

    private final ListingRepository repository;
    private final DraftEditor editor;
    private final ListingViews views;
    private final ListingEventFactory events;
    private final Outbox outbox;
    private final DraftProperties properties;
    private final PublicationProperties publication;
    private final Clock clock;

    ListingService(ListingRepository repository, DraftEditor editor, ListingViews views, ListingEventFactory events,
            Outbox outbox, DraftProperties properties, PublicationProperties publication, Clock clock) {
        this.repository = repository;
        this.editor = editor;
        this.views = views;
        this.events = events;
        this.outbox = outbox;
        this.properties = properties;
        this.publication = publication;
        this.clock = clock;
    }

    /**
     * @throws InvalidListingException    {@code categoryCode} missing, or an invalid value
     * @throws DraftLimitReachedException the account already has the maximum number of drafts
     */
    @Transactional
    public ListingView create(UUID ownerId, JsonNode body) {
        DraftEditor.Edit edit = editor.create(body);
        repository.lockOwner(ownerId);
        if (repository.countByOwnerAndStatus(ownerId, ListingStatus.DRAFT) >= properties.maxPerOwner()) {
            throw new DraftLimitReachedException(properties.maxPerOwner());
        }
        Instant now = now();
        Listing listing = new Listing(UUID.randomUUID(), ownerId, ListingStatus.DRAFT, edit.content(), 1, now, now,
                null, null, null);
        repository.insert(listing);
        return views.view(listing, edit.warnings());
    }

    /**
     * Applies a JSON Merge Patch to a draft of {@code ownerId}. Published and expired listings become editable with
     * ANN-05; until then they are answered like an unknown listing, as by the photo endpoints.
     *
     * @param ifMatch the {@code If-Match} header
     * @throws ListingNotFoundException  unknown listing, listing of another account, or not a draft
     * @throws IfMatchRequiredException  no {@code If-Match}
     * @throws VersionConflictException  {@code If-Match} names another version
     * @throws InvalidListingException   an invalid value
     */
    @Transactional
    public ListingView update(UUID ownerId, UUID id, String ifMatch, JsonNode patch) {
        Listing listing = repository.findForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId) && found.status() == ListingStatus.DRAFT)
                .orElseThrow(ListingNotFoundException::new);
        if (!Versions.matches(ifMatch, listing.version())) {
            throw new VersionConflictException();
        }
        DraftEditor.Edit edit = editor.update(listing.content(), patch);
        Listing updated = listing.edited(edit.content(), now());
        repository.update(updated);
        return views.view(updated, edit.warnings());
    }

    /**
     * Publishes a complete draft of {@code ownerId} for {@link PublicationProperties#ttl()} and writes
     * {@code listing.published}.
     *
     * @throws ListingNotFoundException    unknown listing, or a listing of another account
     * @throws AlreadyPublishedException   the listing is not a draft any more
     * @throws ListingIncompleteException  {@code missingFields} is not empty
     */
    @Transactional
    public ListingView publish(UUID ownerId, UUID id) {
        Listing listing = lockOwned(ownerId, id);
        if (listing.status() != ListingStatus.DRAFT) {
            throw new AlreadyPublishedException();
        }
        ListingView draft = views.view(listing, List.of());
        if (!draft.missingFields().isEmpty()) {
            throw new ListingIncompleteException(draft.missingFields());
        }
        Instant now = now();
        Listing published = listing.published(now, publication.ttl());
        repository.updateStatus(published);
        ListingView view = views.view(published, List.of());
        outbox.write(events.published(view, now));
        return view;
    }

    /**
     * Puts a listing of {@code ownerId} back online for {@link PublicationProperties#ttl()} and writes
     * {@code listing.renewed}: an expired listing, or a published one that expires within
     * {@link PublicationProperties#reminderBefore()}. The listing is not posted again on the social networks.
     *
     * @throws ListingNotFoundException  unknown listing, or a listing of another account
     * @throws RenewNotAllowedException  any other listing
     */
    @Transactional
    public ListingView renew(UUID ownerId, UUID id) {
        Listing listing = lockOwned(ownerId, id);
        Instant now = now();
        boolean allowed = switch (listing.status()) {
            case EXPIRED -> true;
            case PUBLISHED -> listing.expiresAt().isBefore(now.plus(publication.reminderBefore()));
            case DRAFT -> false;
        };
        if (!allowed) {
            throw new RenewNotAllowedException();
        }
        Listing renewed = listing.renewed(now, publication.ttl());
        repository.updateStatus(renewed);
        outbox.write(events.renewed(renewed, now));
        return views.view(renewed, List.of());
    }

    /**
     * @param viewerId the account reading it, or null without a token
     * @throws ListingNotFoundException unknown listing, or a listing that {@code viewerId} does not own
     */
    @Transactional(readOnly = true)
    public ListingView get(UUID viewerId, UUID id) {
        // The public reading of published listings is CATA-03.
        return repository.find(id)
                .filter(found -> viewerId != null && found.isOwnedBy(viewerId))
                .map(found -> views.view(found, List.of()))
                .orElseThrow(ListingNotFoundException::new);
    }

    /**
     * Locks a listing of {@code ownerId} whose photos may change until the end of the current transaction (ANN-02):
     * a draft; ANN-05 opens the photos of published listings, with the rule that they stay complete.
     *
     * @throws ListingNotFoundException unknown listing, or a listing of another account
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Listing lockForPhotos(UUID ownerId, UUID id) {
        return repository.findForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId) && found.status() == ListingStatus.DRAFT)
                .orElseThrow(ListingNotFoundException::new);
    }

    private Listing lockOwned(UUID ownerId, UUID id) {
        return repository.findForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId))
                .orElseThrow(ListingNotFoundException::new);
    }

    /** Truncated to the precision of PostgreSQL, so that an answer and a later reading agree. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
