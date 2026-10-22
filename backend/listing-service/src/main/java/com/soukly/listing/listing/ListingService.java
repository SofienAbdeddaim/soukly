package com.soukly.listing.listing;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.listing.outbox.Outbox;
import com.soukly.listing.photo.ListingPhotos;
import com.soukly.listing.seller.SellerDirectory;
import com.soukly.listing.seller.SellerProfile;
import com.soukly.listing.seller.SellerUnavailableException;

import tools.jackson.databind.JsonNode;

/**
 * Listings of their owner: drafts (ANN-01) created and changed step by step, then published and renewed (ANN-03),
 * edited, deactivated and listed on "My listings" (ANN-05); the deletion is {@link ListingDeletion}. Every status
 * change is checked by {@link ListingTransitions} with the listing locked.
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
    private final SellerDirectory sellers;
    private final ListingTransitions transitions;
    private final ListingPhotos photos;
    private final Clock clock;

    ListingService(ListingRepository repository, DraftEditor editor, ListingViews views, ListingEventFactory events,
            Outbox outbox, DraftProperties properties, PublicationProperties publication, SellerDirectory sellers,
            ListingTransitions transitions, ListingPhotos photos, Clock clock) {
        this.repository = repository;
        this.editor = editor;
        this.views = views;
        this.events = events;
        this.outbox = outbox;
        this.properties = properties;
        this.publication = publication;
        this.sellers = sellers;
        this.transitions = transitions;
        this.photos = photos;
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
                null, null, null, null);
        repository.insert(listing);
        return views.view(listing, edit.warnings());
    }

    /**
     * Applies a JSON Merge Patch to a listing of {@code ownerId} (ANN-01), a draft or, since ANN-05, a published,
     * expired or deactivated one. A listing that was published keeps its category and stays complete; the change of
     * a published listing is visible at once and writes {@code listing.updated}, never {@code listing.published}.
     *
     * @param ifMatch the {@code If-Match} header
     * @throws ListingNotFoundException    unknown or deleted listing, or a listing of another account
     * @throws IfMatchRequiredException    no {@code If-Match}
     * @throws VersionConflictException    {@code If-Match} names another version
     * @throws InvalidListingException     an invalid value
     * @throws CategoryLockedException     another category for a listing that was published
     * @throws ListingIncompleteException  the change would empty a required field of a listing that was published
     * @throws ShareLockedException        {@code shareTo} or {@code shareLanguage} for a listing that is not a draft
     */
    @Transactional
    public ListingView update(UUID ownerId, UUID id, String ifMatch, JsonNode patch) {
        Listing listing = lockOwned(ownerId, id);
        Instant now = now();
        transitions.target(listing, ListingAction.EDIT, now);
        if (!Versions.matches(ifMatch, listing.version())) {
            throw new VersionConflictException();
        }
        boolean wasPublished = listing.publishedAt() != null;
        if (wasPublished && changesCategory(listing, patch)) {
            throw new CategoryLockedException();
        }
        if (listing.status() != ListingStatus.DRAFT && changesShare(patch)) {
            throw new ShareLockedException();
        }
        DraftEditor.Edit edit = editor.update(listing.content(), patch);
        Listing updated = listing.edited(edit.content(), now);
        ListingView view = views.view(updated, edit.warnings());
        if (wasPublished && !view.missingFields().isEmpty()) {
            throw new ListingIncompleteException(view.missingFields());
        }
        repository.update(updated);
        List<String> changed = ListingChanges.between(listing.content(), updated.content());
        if (updated.status() == ListingStatus.PUBLISHED && !changed.isEmpty()) {
            outbox.write(events.updated(view, changed, now));
        }
        return view;
    }

    /**
     * Publishes a complete draft of {@code ownerId} for {@link PublicationProperties#ttl()} and writes
     * {@code listing.published}.
     *
     * @throws ListingNotFoundException    unknown or deleted listing, or a listing of another account
     * @throws AlreadyPublishedException   the listing is not a draft any more
     * @throws ListingIncompleteException  {@code missingFields} is not empty
     */
    @Transactional
    public ListingView publish(UUID ownerId, UUID id) {
        Listing listing = lockOwned(ownerId, id);
        Instant now = now();
        transitions.target(listing, ListingAction.PUBLISH, now);
        ListingView draft = views.view(listing, List.of());
        if (!draft.missingFields().isEmpty()) {
            throw new ListingIncompleteException(draft.missingFields());
        }
        Listing published = listing.published(now, publication.ttl());
        repository.updateStatus(published);
        ListingView view = views.view(published, List.of());
        outbox.write(events.published(view, now));
        return view;
    }

    /**
     * Puts a listing of {@code ownerId} back online for {@link PublicationProperties#ttl()} and writes
     * {@code listing.renewed}: an expired or deactivated listing, or a published one that expires within
     * {@link PublicationProperties#reminderBefore()}. The listing is not posted again on the social networks.
     *
     * @throws ListingNotFoundException  unknown or deleted listing, or a listing of another account
     * @throws RenewNotAllowedException  any other listing
     */
    @Transactional
    public ListingView renew(UUID ownerId, UUID id) {
        Listing listing = lockOwned(ownerId, id);
        Instant now = now();
        transitions.target(listing, ListingAction.RENEW, now);
        Listing renewed = listing.renewed(now, publication.ttl());
        repository.updateStatus(renewed);
        outbox.write(events.renewed(renewed, now));
        return views.view(renewed, List.of());
    }

    /**
     * Hides a published listing of {@code ownerId} from the search and its page ({@code 410}) and writes
     * {@code listing.deactivated} (ANN-05). It does not expire while it is deactivated; a renewal puts it back.
     *
     * @throws ListingNotFoundException       unknown or deleted listing, or a listing of another account
     * @throws TransitionNotAllowedException  the listing is not published
     */
    @Transactional
    public ListingView deactivate(UUID ownerId, UUID id) {
        Listing listing = lockOwned(ownerId, id);
        Instant now = now();
        transitions.target(listing, ListingAction.DEACTIVATE, now);
        Listing deactivated = listing.deactivated(now);
        repository.updateStatus(deactivated);
        outbox.write(events.deactivated(deactivated, now));
        return views.view(deactivated, List.of());
    }

    /**
     * Writes {@code listing.updated} with {@code changedFields: ["photos"]} when the READY photos of a published
     * listing changed (ANN-05), in the transaction of the change, with the listing locked by
     * {@link #lockForPhotos(UUID, UUID)}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void photosChanged(Listing listing) {
        if (listing.status() == ListingStatus.PUBLISHED) {
            outbox.write(events.updated(views.view(listing, List.of()), List.of(ListingViews.PHOTOS), now()));
        }
    }

    /**
     * A page of the listings of {@code ownerId} in {@code status} (ANN-05), the latest changed first; the number of
     * listings in each status on the first page.
     *
     * @param cursor {@code nextCursor} of the previous page, or null
     * @throws InvalidListingException a cursor that is malformed or of another status
     */
    @Transactional(readOnly = true)
    public OwnListingsPage mine(UUID ownerId, ListingStatus status, int limit, String cursor) {
        OwnListingsCursor after = cursor == null ? null : OwnListingsCursor.decode(cursor, status);
        // One more row says whether there is a next page
        List<OwnListingRow> rows = repository.ownPage(ownerId, status, after, limit + 1);
        boolean more = rows.size() > limit;
        List<OwnListingRow> page = more ? rows.subList(0, limit) : rows;
        List<OwnListingCard> items = page.stream().map(this::card).toList();
        String next = more ? OwnListingsCursor.after(page.getLast().listing()).encode() : null;
        Map<ListingStatus, Long> counts = null;
        if (after == null) {
            Map<ListingStatus, Long> found = repository.countByStatus(ownerId);
            counts = new EnumMap<>(ListingStatus.class);
            for (ListingStatus listed : OwnListingsPage.LISTED) {
                counts.put(listed, found.getOrDefault(listed, 0L));
            }
        }
        return new OwnListingsPage(items, next, counts);
    }

    private OwnListingCard card(OwnListingRow row) {
        Listing listing = row.listing();
        // Only drafts can miss fields: a listing that was published always stays complete
        List<String> missing = listing.status() == ListingStatus.DRAFT
                ? views.view(listing, List.of()).missingFields()
                : null;
        return OwnListingCard.of(listing, row.coverId() == null ? null
                : photos.thumbnailUrl(listing.id(), row.coverId()), missing, row.contactReveals());
    }

    /**
     * Reads a listing (ANN-01, CATA-03): its owner reads it in any status but deleted; anyone else reads a published
     * listing whose seller is active.
     *
     * @param viewerId the account reading it, or null without a token
     * @throws ListingNotFoundException   unknown listing, or a draft of another account
     * @throws ListingGoneException       a listing that is not online any more, for anyone but its owner; a deleted
     *                                    listing for everyone
     * @throws SellerUnavailableException user-service did not answer
     */
    @Transactional(readOnly = true)
    public ListingReading read(UUID viewerId, UUID id) {
        Listing listing = repository.find(id).orElseThrow(ListingNotFoundException::new);
        if (listing.status() == ListingStatus.DELETED) {
            // Gone for everyone, its owner included (ANN-05)
            throw new ListingGoneException(listing);
        }
        ListingView view = views.view(listing, List.of());
        if (viewerId != null && listing.isOwnedBy(viewerId)) {
            SellerProfile seller = listing.status() == ListingStatus.DRAFT ? null
                    : sellers.profile(listing.ownerId()).orElse(null);
            return new ListingReading(view, true, seller);
        }
        requireOnline(listing);
        SellerProfile seller = sellers.profile(listing.ownerId()).orElseThrow(() -> new ListingGoneException(listing));
        return new ListingReading(view, false, seller);
    }

    /**
     * A listing whose seller {@code viewerId} may contact (CATA-03).
     *
     * @throws ListingNotFoundException unknown listing, or a draft of another account
     * @throws OwnListingException      a listing of {@code viewerId}
     * @throws ListingGoneException     a listing that is not online any more
     */
    @Transactional(readOnly = true)
    public Listing contactable(UUID viewerId, UUID id) {
        Listing listing = repository.find(id).orElseThrow(ListingNotFoundException::new);
        if (listing.isOwnedBy(viewerId)) {
            throw new OwnListingException();
        }
        requireOnline(listing);
        return listing;
    }

    /** Hides a draft ({@code 404}) and a listing that is not online any more ({@code 410}). */
    private void requireOnline(Listing listing) {
        switch (listing.status()) {
            case DRAFT -> throw new ListingNotFoundException();
            case EXPIRED, INACTIVE, DELETED -> throw new ListingGoneException(listing);
            // The expiry task runs every hour: a listing past its expiry is already gone, as in the search
            case PUBLISHED -> {
                if (!listing.expiresAt().isAfter(clock.instant())) {
                    throw new ListingGoneException(listing);
                }
            }
        }
    }

    /**
     * Locks a listing of {@code ownerId} whose photos may change until the end of the current transaction (ANN-02):
     * any listing that can be edited (ANN-05). A listing that was published must keep a photo.
     *
     * @throws ListingNotFoundException unknown or deleted listing, or a listing of another account
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Listing lockForPhotos(UUID ownerId, UUID id) {
        Listing listing = lockOwned(ownerId, id);
        transitions.target(listing, ListingAction.EDIT, now());
        return listing;
    }

    /**
     * Locks a listing of {@code ownerId}; a deleted listing is answered like an unknown one, to its owner too
     * (ANN-05).
     */
    Listing lockOwned(UUID ownerId, UUID id) {
        return repository.findForUpdate(id)
                .filter(found -> found.isOwnedBy(ownerId) && found.status() != ListingStatus.DELETED)
                .orElseThrow(ListingNotFoundException::new);
    }

    /** Whether {@code patch} names the networks or the language of the sharing, locked once published (SOC-03). */
    private static boolean changesShare(JsonNode patch) {
        return patch.isObject() && (patch.has(DraftEditor.SHARE_TO) || patch.has(DraftEditor.SHARE_LANGUAGE));
    }

    /** Whether {@code patch} names a category other than the current one. */
    private static boolean changesCategory(Listing listing, JsonNode patch) {
        if (!patch.isObject() || !patch.has(DraftEditor.CATEGORY_CODE)) {
            return false;
        }
        JsonNode value = patch.get(DraftEditor.CATEGORY_CODE);
        return !value.isString() || !value.stringValue().equals(listing.content().categoryCode());
    }

    /** Truncated to the precision of PostgreSQL, so that an answer and a later reading agree. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
