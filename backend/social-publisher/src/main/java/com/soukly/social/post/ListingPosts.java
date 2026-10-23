package com.soukly.social.post;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final SocialConnectionRepository connections;
    private final PostRemoval removal;
    private final PostMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ListingPosts(ProcessedEvents processed, SocialPostRepository posts, SocialConnectionRepository connections,
            PostRemoval removal, PostMetrics metrics, TransactionTemplate transactions, Clock clock) {
        this.processed = processed;
        this.posts = posts;
        this.connections = connections;
        this.removal = removal;
        this.metrics = metrics;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * {@code listing.published}: one row per network of {@code shareTo} this version publishes, {@code PENDING} when
     * the network is connected, {@code SKIPPED} with the reason otherwise; nothing for a network not chosen. The rows
     * and the event are recorded in one transaction.
     */
    void recordPublished(UUID eventId, UUID listingId, UUID userId, List<String> shareTo, PostContent content,
            String requestId) {
        transactions.executeWithoutResult(status -> {
            if (!processed.markProcessed(eventId, clock.instant())) {
                log.info("Listing event {} was processed already", eventId);
                return;
            }
            for (Network network : PUBLISHED_NETWORKS) {
                if (shareTo.contains(network.name())) {
                    record(listingId, userId, network, content, requestId);
                }
            }
        });
    }

    private void record(UUID listingId, UUID userId, Network network, PostContent content, String requestId) {
        Optional<ConnectionStatus> connection = connections.status(userId, network);
        PostStatus status = connection.filter(ConnectionStatus.CONNECTED::equals).isPresent() ? PostStatus.PENDING
                : PostStatus.SKIPPED;
        String reason = connection.isEmpty() ? NOT_CONNECTED
                : connection.get() == ConnectionStatus.NEEDS_RECONNECT ? NEEDS_RECONNECT : null;
        if (posts.create(listingId, userId, network, content, status, reason, requestId)) {
            metrics.entered(network, status);
            log.info("{} post of listing {}: {}{}", network.displayName(), listingId, status,
                    reason == null ? "" : " (" + reason + ")");
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
}
