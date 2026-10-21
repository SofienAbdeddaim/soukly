package com.soukly.listing.listing;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * Drafts of listings (ANN-01), under {@code /api/listings} at the gateway. The owner is the {@code sub} of the
 * access token, validated by {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/listings")
class ListingController {

    /** JSON Merge Patch (RFC 7396); plain JSON is accepted too. */
    static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    private final ListingService listings;

    ListingController(ListingService listings) {
        this.listings = listings;
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

    /** Public at the gateway: the token, when there is one, says whether the reader owns the draft. */
    @GetMapping("/{id}")
    ResponseEntity<ListingResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId = listingId(id).orElseThrow(ListingNotFoundException::new);
        return answer(ResponseEntity.ok(), listings.get(jwt == null ? null : ownerId(jwt), listingId));
    }

    /** A draft is private: never stored by a cache. */
    private static ResponseEntity<ListingResponse> answer(ResponseEntity.BodyBuilder builder, ListingView view) {
        return builder.eTag(Versions.etag(view.listing().version()))
                .cacheControl(CacheControl.noStore())
                .body(ListingResponse.of(view));
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
