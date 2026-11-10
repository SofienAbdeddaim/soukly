package com.soukly.social.post;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.social.connection.ConnectionStatus;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.event.ProcessedEvents;

/**
 * Effects of the listing events on the posts (SOC-04), deduplicated by {@code eventId} ({@code processed_events}).
 */
@Service
class ListingPosts {

    private static final Logger log = LoggerFactory.getLogger(ListingPosts.class);

    /** Networks published by this version: Facebook (SOC-04) and Instagram (SOC-05). */
    private static final List<Network> PUBLISHED_NETWORKS = List.of(Network.FACEBOOK, Network.INSTAGRAM);

    static final String NOT_CONNECTED = "not-connected";
    static final String NEEDS_RECONNECT = "needs-reconnect";

    private final ProcessedEvents processed;
    private final SocialPostRepository posts;
    private final SocialPostRepository housePosts;
    private final HouseProperties house;
    private final SocialConnectionRepository connections;
    private final PostRemoval removal;
    private final PostMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ListingPosts(ProcessedEvents processed, SocialPostRepository posts,
            @Qualifier(PostChannelsConfig.HOUSE) SocialPostRepository housePosts, HouseProperties house,
            SocialConnectionRepository connections, PostRemoval removal, PostMetrics metrics,
            TransactionTemplate transactions, Clock clock) {
        this.processed = processed;
        this.posts = posts;
        this.housePosts = housePosts;
        this.house = house;
        this.connections = connections;
        this.removal = removal;
        this.metrics = metrics;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * {@code listing.published}: one row per network of {@code shareTo} this version publishes, {@code PENDING} when
     * the network is connected, {@code SKIPPED} with the reason otherwise; nothing for a network not chosen.
     *
     * <p>With {@code houseShare} (SOC-07), and when Soukly's own accounts are configured: also one row per network on
     * Soukly's Page and Instagram, whatever {@code shareTo} holds, with the state of Soukly's connections. A listing of
     * the Soukly account itself gets none: it shares on those accounts through {@code shareTo} already. The rows and
     * the event are recorded in one transaction.
     *
     * @param content what the posts are made of; null when the listing is posted nowhere
     */
    void recordPublished(UUID eventId, UUID listingId, UUID userId, List<String> shareTo, boolean houseShare,
            PostContent content, String requestId) {
        transactions.executeWithoutResult(status -> {
            if (!processed.markProcessed(eventId, clock.instant())) {
                log.info("Listing event {} was processed already", eventId);
                return;
            }
            if (content == null) {
                return;
            }
            for (Network network : PUBLISHED_NETWORKS) {
                if (shareTo.contains(network.name())) {
                    record(posts, userId, listingId, userId, network, content, requestId);
                }
            }
            if (houseShare && house.enabled() && !house.userId().equals(userId)) {
                for (Network network : PUBLISHED_NETWORKS) {
                    record(housePosts, house.userId(), listingId, userId, network, content, requestId);
                }
            }
        });
    }

    /** @param accountOwner the Soukly account whose connection publishes the post */
    private void record(SocialPostRepository repository, UUID accountOwner, UUID listingId, UUID userId,
            Network network, PostContent content, String requestId) {
        Optional<ConnectionStatus> connection = connections.status(accountOwner, network);
        PostStatus status = connection.filter(ConnectionStatus.CONNECTED::equals).isPresent() ? PostStatus.PENDING
                : PostStatus.SKIPPED;
        String reason = connection.isEmpty() ? NOT_CONNECTED
                : connection.get() == ConnectionStatus.NEEDS_RECONNECT ? NEEDS_RECONNECT : null;
        if (repository.create(listingId, userId, network, content, status, reason, requestId)) {
            metrics.entered(repository.channel(), network, status);
            log.info("{}{} post of listing {}: {}{}", network.displayName(), repository.channel().label(), listingId,
                    status, reason == null ? "" : " (" + reason + ")");
        }
    }

    /**
     * {@code listing.deleted}: the posts are removed first, then the event is recorded. A crash in between delivers
     * the event again, which finds the posts already {@code CANCELLED} or {@code DELETED}.
     */
    void removeDeleted(UUID eventId, UUID listingId) {
        if (processed.isProcessed(eventId)) {
            log.info("Listing event {} was processed already", eventId);
            return;
        }
        removal.removeListing(listingId);
        transactions.executeWithoutResult(status -> processed.markProcessed(eventId, clock.instant()));
    }

    /**
     * {@code listing.deactivated}, {@code listing.expired} (SOC-07): the house posts are removed, then the event is
     * recorded, like {@link #removeDeleted}. The posts on the seller's accounts stay.
     */
    void removeFromHouse(UUID eventId, UUID listingId) {
        if (processed.isProcessed(eventId)) {
            log.info("Listing event {} was processed already", eventId);
            return;
        }
        removal.removeHouseListing(listingId);
        transactions.executeWithoutResult(status -> processed.markProcessed(eventId, clock.instant()));
    }
}
