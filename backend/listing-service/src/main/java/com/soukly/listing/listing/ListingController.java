package com.soukly.listing.listing;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * Listings of their owner: drafts (ANN-01), publication and renewal (ANN-03), "My listings", deactivation and
 * deletion (ANN-05), under {@code /api/listings} at the gateway. The owner is the {@code sub} of the access token,
 * validated by {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/listings")
class ListingController {

    /** JSON Merge Patch (RFC 7396); plain JSON is accepted too. */
    static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    /** Page size of {@code GET /listings/mine} without {@code limit}, and the highest one allowed. */
    static final int MINE_DEFAULT_LIMIT = 20;
    static final int MINE_MAX_LIMIT = 50;

    private final ListingService listings;
    private final ListingDeletion deletion;

    ListingController(ListingService listings, ListingDeletion deletion) {
        this.listings = listings;
        this.deletion = deletion;
    }

    /**
     * The listings of the caller in one status (ANN-05), {@code PUBLISHED} by default; never the deleted ones.
     * Declared as a literal path, it wins over {@code GET /listings/{id}}.
     */
    @GetMapping("/mine")
    ResponseEntity<OwnListingsPage> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status, @RequestParam(required = false) String limit,
            @RequestParam(required = false) String cursor) {
        List<FieldError> errors = new ArrayList<>();
        ListingStatus listed = listedStatus(status, errors);
        int size = mineLimit(limit, errors);
        if (!errors.isEmpty()) {
            throw new InvalidListingException(errors);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(listings.mine(ownerId(jwt), listed, size, cursor == null || cursor.isEmpty() ? null : cursor));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ListingResponse> create(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        ListingView view = listings.create(ownerId(jwt), body);
        // A relative reference, resolved against the URL the client called (RFC 9110): /api/listings gives
        // /api/listings/{id} through the gateway, which strips /api without rewriting this header.
        URI location = URI.create("listings/" + view.listing().id());
        return answer(ResponseEntity.status(HttpStatus.CREATED).location(location), view);
    }

    @PatchMapping(path = "/{id}", consumes = { MERGE_PATCH_JSON, MediaType.APPLICATION_JSON_VALUE })
    ResponseEntity<ListingResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody JsonNode patch) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        return answer(ResponseEntity.ok(), listings.update(ownerId(jwt), listingId, ifMatch, patch));
    }

    /** Publishes a complete draft (ANN-03); no {@code If-Match}: publishing never overwrites a change. */
    @PostMapping("/{id}/publish")
    ResponseEntity<ListingResponse> publish(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        return answer(ResponseEntity.ok(), listings.publish(ownerId(jwt), listingId));
    }

    /** Hides a published listing until it is renewed (ANN-05). */
    @PostMapping("/{id}/deactivate")
    ResponseEntity<ListingResponse> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        return answer(ResponseEntity.ok(), listings.deactivate(ownerId(jwt), listingId));
    }

    /** Erases a draft, or deletes any other listing until its purge (ANN-05). */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        deletion.delete(ownerId(jwt), listingId);
        return ResponseEntity.noContent().build();
    }

    /** Puts an expired or deactivated listing, or one that expires soon, back online (ANN-03, ANN-05). */
    @PostMapping("/{id}/renew")
    ResponseEntity<ListingResponse> renew(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        return answer(ResponseEntity.ok(), listings.renew(ownerId(jwt), listingId));
    }

    /**
     * The listing of a short reference, in any case (SOC-05): public, for the redirection {@code /r/{ref}} of the
     * front end. Declared as a literal path, it wins over {@code GET /listings/{id}}.
     */
    @GetMapping("/by-ref/{ref}")
    ResponseEntity<ListingId> byRef(@PathVariable String ref) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(new ListingId(listings.idByShortRef(ref)));
    }

    /** Body of {@code GET /listings/by-ref/{ref}}. */
    record ListingId(UUID id) {
    }

    /**
     * Public at the gateway: the token, when there is one, says whether the reader owns the listing. The owner gets
     * the full view in any status; anyone else the public view of a published listing (CATA-03), never stored by a
     * shared cache and revalidated, since it disappears with the listing.
     */
    @GetMapping("/{id}")
    ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        ListingReading reading = listings.read(jwt == null ? null : ownerId(jwt), listingId);
        if (reading.owner()) {
            return ResponseEntity.ok()
                    .eTag(Versions.etag(reading.view().listing().version()))
                    .cacheControl(CacheControl.noStore())
                    .body(ListingResponse.of(reading));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(PublicListingResponse.of(reading));
    }

    /** The owner's view is private: never stored by a cache. */
    private static ResponseEntity<ListingResponse> answer(ResponseEntity.BodyBuilder builder, ListingView view) {
        return builder.eTag(Versions.etag(view.listing().version()))
                .cacheControl(CacheControl.noStore())
                .body(ListingResponse.of(view));
    }

    private static ListingStatus listedStatus(String value, List<FieldError> errors) {
        if (value == null || value.isEmpty()) {
            return ListingStatus.PUBLISHED;
        }
        for (ListingStatus status : OwnListingsPage.LISTED) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        errors.add(new FieldError("status", "status.invalid"));
        return null;
    }

    private static int mineLimit(String value, List<FieldError> errors) {
        if (value == null || value.isEmpty()) {
            return MINE_DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit >= 1 && limit <= MINE_MAX_LIMIT) {
                return limit;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        errors.add(new FieldError("limit", "limit.invalid"));
        return 0;
    }

    /** The {@code sub} is checked to be a UUID when the token is decoded. */
    private static UUID ownerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** An id that is not a UUID cannot exist: {@code 404}, like an unknown one. */
    private static Optional<UUID> listingId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
