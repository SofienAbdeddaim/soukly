package com.soukly.social.post;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.soukly.social.connection.ConnectionStatus;
import com.soukly.social.connection.ConnectionView;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.SocialConnectionRepository;

/**
 * The posts as the seller follows them on "My listings" (SOC-06): their badges, "Try again" on a failure, and the
 * relaunch of the posts blocked while a network was to reconnect. A post goes back to the queue of the publishers of
 * SOC-04 and SOC-05, with the same content and pace; never for a listing that is not online.
 */
@Service
@EnableConfigurationProperties(PostFollowUpProperties.class)
class PostFollowUp {

    private static final Logger log = LoggerFactory.getLogger(PostFollowUp.class);

    private final SocialPostRepository posts;
    private final SocialConnectionRepository connections;
    private final ListingAvailability listings;
    private final PostFollowUpProperties properties;
    private final PostBadges badges;
    private final Clock clock;

    PostFollowUp(SocialPostRepository posts, SocialConnectionRepository connections, ListingAvailability listings,
            PostFollowUpProperties properties, FacebookPostingProperties facebook,
            InstagramPostingProperties instagram, Clock clock) {
        this.posts = posts;
        this.connections = connections;
        this.listings = listings;
        this.properties = properties;
        this.badges = new PostBadges(properties.maxManualRetries(), facebook.retryDelays().size() + 1,
                instagram.retryDelays().size() + 1);
        this.clock = clock;
    }

    /** A listing and its posts with a badge, Facebook first. */
    record ListingPosts(UUID listingId, List<PostStatusView> posts) {
    }

    /** The posts of these listings of the member; a listing without any badge is left out. */
    List<ListingPosts> statuses(UUID userId, Collection<UUID> listingIds) {
        Map<Network, Instant> connected = connectedSince(userId);
        Map<UUID, List<PostStatusView>> byListing = new LinkedHashMap<>();
        for (PostRow row : posts.rowsOfListings(userId, listingIds)) {
            badges.view(row, Optional.ofNullable(connected.get(row.network()))).ifPresent(view -> byListing
                    .computeIfAbsent(row.listingId(), id -> new ArrayList<>()).add(view));
        }
        return byListing.entrySet().stream().map(entry -> new ListingPosts(entry.getKey(), entry.getValue())).toList();
    }

    /**
     * "Try again" a failed post.
     *
     * @throws PostNotFoundException             the member has no post of this listing on this network
     * @throws PostActionRefusedException        not a failure that can be tried again, no attempt left, or the listing
     *                                           is not online
     * @throws ListingStatusUnavailableException listing-service did not answer
     */
    PostStatusView retry(UUID userId, UUID listingId, Network network) {
        PostRow row = posts.row(userId, listingId, network).orElseThrow(PostNotFoundException::new);
        PostStatusView view = badges.view(row, Optional.ofNullable(connectedSince(userId).get(network)))
                .orElseThrow(() -> new PostActionRefusedException(PostActionRefusedException.Reason.NOT_RETRYABLE));
        if (view.badge() != PostBadge.FAILED) {
            throw new PostActionRefusedException(PostActionRefusedException.Reason.NOT_RETRYABLE);
        }
        if (view.manualRetriesLeft() == 0) {
            throw new PostActionRefusedException(PostActionRefusedException.Reason.RETRY_LIMIT_REACHED);
        }
        if (!listings.isOnline(listingId)) {
            throw new PostActionRefusedException(PostActionRefusedException.Reason.LISTING_NOT_ONLINE);
        }
        if (!posts.retryByHand(userId, listingId, network, properties.maxManualRetries())) {
            // Changed in the meantime (another click, a deletion of the listing)
            throw new PostActionRefusedException(PostActionRefusedException.Reason.NOT_RETRYABLE);
        }
        log.info("{} post of listing {} tried again by hand ({} left)", network.displayName(), listingId,
                view.manualRetriesLeft() - 1);
        return posts.row(userId, listingId, network)
                .flatMap(updated -> badges.view(updated, Optional.ofNullable(connectedSince(userId).get(network))))
                .orElseThrow(PostNotFoundException::new);
    }

    /** A post blocked while the network was to reconnect, offered again after the reconnection. */
    record BlockedListing(UUID listingId, String title) {
    }

    /**
     * The posts of this network blocked by a connection problem within the relaunch window, whose listing is still
     * online.
     *
     * @throws ListingStatusUnavailableException listing-service did not answer
     */
    List<BlockedListing> blocked(UUID userId, Network network) {
        List<PostRow> rows = posts.blocked(userId, network, PostBadges.CONNECTION_CODES,
                clock.instant().minus(properties.relaunchWindow()));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> online = listings.online(rows.stream().map(PostRow::listingId).toList());
        return rows.stream().filter(row -> online.contains(row.listingId()))
                .map(row -> new BlockedListing(row.listingId(), row.title())).toList();
    }

    /**
     * Puts the blocked posts of this network back in the queue, without counting a manual attempt.
     *
     * @return the posts put back in the queue
     * @throws PostActionRefusedException        the network is not connected
     * @throws ListingStatusUnavailableException listing-service did not answer
     */
    int relaunch(UUID userId, Network network) {
        if (!connectedSince(userId).containsKey(network)) {
            throw new PostActionRefusedException(PostActionRefusedException.Reason.NETWORK_NOT_CONNECTED);
        }
        List<UUID> ids = blocked(userId, network).stream().map(BlockedListing::listingId).toList();
        int relaunched = posts.relaunch(userId, network, ids, PostBadges.CONNECTION_CODES);
        log.info("{} relaunched {} blocked post(s)", network.displayName(), relaunched);
        return relaunched;
    }

    /** When each network the member has connected now was connected. */
    private Map<Network, Instant> connectedSince(UUID userId) {
        Map<Network, Instant> connected = new EnumMap<>(Network.class);
        for (ConnectionView connection : connections.findAll(userId)) {
            if (connection.status() == ConnectionStatus.CONNECTED) {
                connected.put(connection.network(), connection.connectedAt());
            }
        }
        return connected;
    }
}
