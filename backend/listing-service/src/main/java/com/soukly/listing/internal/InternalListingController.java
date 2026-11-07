package com.soukly.listing.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.listing.listing.FieldError;
import com.soukly.listing.listing.InvalidListingException;
import com.soukly.listing.listing.ListingNotFoundException;
import com.soukly.listing.listing.ListingStatuses;
import com.soukly.listing.listing.ListingStatuses.StatusView;

/**
 * Status of listings, read by social-publisher before it puts a post back in the queue (SOC-06). Never routed by the
 * gateway, and protected by {@link InternalTokenFilter}. Answers drafts and deleted listings too: the caller only
 * needs to know whether a listing is online.
 */
@RestController
@RequestMapping("/internal/listings")
class InternalListingController {

    /** Listings at most in one {@code POST /internal/listings/statuses}. */
    static final int MAX_IDS = 50;

    private final ListingStatuses statuses;

    InternalListingController(ListingStatuses statuses) {
        this.statuses = statuses;
    }

    /** {@code 404} for an unknown id. */
    @GetMapping("/{id}/status")
    ResponseEntity<StatusView> status(@PathVariable String id) {
        UUID listingId;
        try {
            listingId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ListingNotFoundException();
        }
        StatusView status = statuses.of(List.of(listingId)).stream().findFirst()
                .orElseThrow(ListingNotFoundException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);
    }

    /** The statuses of at most {@value #MAX_IDS} listings; unknown ids are left out of the answer. */
    @PostMapping("/statuses")
    ResponseEntity<StatusesResponse> statuses(@RequestBody StatusesRequest request) {
        if (request == null || request.ids() == null || request.ids().size() > MAX_IDS
                || request.ids().stream().anyMatch(id -> id == null)) {
            throw new InvalidListingException(List.of(new FieldError("ids", "ids.invalid")));
        }
        Set<UUID> ids = new LinkedHashSet<>(request.ids());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new StatusesResponse(ids.isEmpty() ? List.of() : statuses.of(ids)));
    }

    record StatusesRequest(List<UUID> ids) {
    }

    record StatusesResponse(List<StatusView> statuses) {
    }
}
