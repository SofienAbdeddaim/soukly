package com.soukly.social.post;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.social.connection.Network;
import com.soukly.social.web.FieldError;
import com.soukly.social.web.InvalidRequestException;

/**
 * The posts of the member's listings on "My listings" (SOC-06): their statuses, "Try again" on a failure, and the
 * relaunch of the posts blocked while a network was to reconnect. Every answer concerns the member's own posts only:
 * the listings of other members are ignored, never revealed.
 */
@RestController
@RequestMapping("/social/posts")
class SocialPostController {

    /** Listings at most in one {@code GET /social/posts}: a page of "My listings". */
    static final int MAX_LISTINGS = 50;

    private final PostFollowUp followUp;

    SocialPostController(PostFollowUp followUp) {
        this.followUp = followUp;
    }

    /** {@code ?listingIds=id1,id2}: the posts of these listings that belong to the member. */
    @GetMapping
    ResponseEntity<StatusesResponse> statuses(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<String> listingIds) {
        Set<UUID> ids = listingIds(listingIds);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new StatusesResponse(followUp.statuses(member(jwt), ids)));
    }

    /** {@code {network}} is {@code FACEBOOK} or {@code INSTAGRAM}, in any case. */
    @PostMapping("/{listingId}/{network}/retry")
    PostStatusView retry(@AuthenticationPrincipal Jwt jwt, @PathVariable String listingId,
            @PathVariable String network) {
        UUID id;
        try {
            id = UUID.fromString(listingId);
        } catch (IllegalArgumentException e) {
            throw new PostNotFoundException();
        }
        return followUp.retry(member(jwt), id, parse(network).orElseThrow(PostNotFoundException::new));
    }

    /** {@code ?network=FACEBOOK}: the listings whose post on this network is blocked, for the relaunch banner. */
    @GetMapping("/blocked")
    ResponseEntity<BlockedResponse> blocked(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String network) {
        Network parsed = network(network);
        List<PostFollowUp.BlockedListing> listings = followUp.blocked(member(jwt), parsed);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new BlockedResponse(parsed, listings.size(), listings));
    }

    /** The body is {@code {"network": "FACEBOOK"}}. */
    @PostMapping("/relaunch")
    RelaunchResponse relaunch(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) RelaunchRequest body) {
        return new RelaunchResponse(followUp.relaunch(member(jwt), network(body == null ? null : body.network())));
    }

    private static UUID member(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static Set<UUID> listingIds(List<String> values) {
        Set<UUID> ids = new LinkedHashSet<>();
        List<String> parts = new ArrayList<>();
        if (values != null) {
            values.forEach(value -> parts.addAll(List.of(value.split(","))));
        }
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException(List.of(new FieldError("listingIds", "listing-ids.invalid")));
            }
        }
        if (ids.size() > MAX_LISTINGS) {
            throw new InvalidRequestException(List.of(new FieldError("listingIds", "listing-ids.too-many")));
        }
        return ids;
    }

    private static Network network(String value) {
        return parse(value).orElseThrow(
                () -> new InvalidRequestException(List.of(new FieldError("network", "network.invalid"))));
    }

    private static Optional<Network> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Network.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    record StatusesResponse(List<PostFollowUp.ListingPosts> listings) {
    }

    record BlockedResponse(Network network, int total, List<PostFollowUp.BlockedListing> listings) {
    }

    record RelaunchRequest(String network) {
    }

    record RelaunchResponse(int relaunched) {
    }
}
