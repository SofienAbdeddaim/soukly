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

import tools.jackson.databind.JsonNode;

/**
 * Drafts of listings (ANN-01): creation, change step by step and reading by the owner. A draft of another account
 * is answered exactly like an unknown listing.
 */
@Service
@EnableConfigurationProperties(DraftProperties.class)
public class ListingService {

    private final ListingRepository repository;
    private final DraftEditor editor;
    private final ListingViews views;
    private final DraftProperties properties;
    private final Clock clock;

    ListingService(ListingRepository repository, DraftEditor editor, ListingViews views, DraftProperties properties,
            Clock clock) {
        this.repository = repository;
        this.editor = editor;
        this.views = views;
        this.properties = properties;
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
                null);
        repository.insert(listing);
        return views.view(listing, edit.warnings());
    }

    /**
     * Applies a JSON Merge Patch to a draft of {@code ownerId}.
     *
     * @param ifMatch the {@code If-Match} header
     * @throws ListingNotFoundException  unknown listing or listing of another account
     * @throws IfMatchRequiredException  no {@code If-Match}
     * @throws VersionConflictException  {@code If-Match} names another version
     * @throws InvalidListingException   an invalid value
     */
    @Transactional
    public ListingView update(UUID ownerId, UUID id, String ifMatch, JsonNode patch) {
        Listing listing = repository.findForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId))
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
     * @param viewerId the account reading it, or null without a token
     * @throws ListingNotFoundException unknown listing, or a draft that {@code viewerId} does not own
     */
    @Transactional(readOnly = true)
    public ListingView get(UUID viewerId, UUID id) {
        // Every listing is a draft until ANN-03; the public reading of published listings is CATA-03.
        return repository.find(id)
                .filter(found -> viewerId != null && found.isOwnedBy(viewerId))
                .map(found -> views.view(found, List.of()))
                .orElseThrow(ListingNotFoundException::new);
    }

    /**
     * Locks a listing of {@code ownerId} whose photos may change until the end of the current transaction (ANN-02):
     * a draft, and a published listing once ANN-03 adds them.
     *
     * @throws ListingNotFoundException unknown listing, or a listing of another account
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Listing lockForPhotos(UUID ownerId, UUID id) {
        return repository.findForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId) && found.status() == ListingStatus.DRAFT)
                .orElseThrow(ListingNotFoundException::new);
    }

    /** Truncated to the precision of PostgreSQL, so that an answer and a later reading agree. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
