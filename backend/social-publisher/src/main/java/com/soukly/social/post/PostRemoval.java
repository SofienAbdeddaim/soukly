package com.soukly.social.post;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.soukly.social.connection.ConnectionHealth;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.PublishingAccount;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.facebook.FacebookGraphClient;
import com.soukly.social.graph.GraphApiException;

/**
 * Removes the posts of a deleted listing or of a deleted account from the networks (SOC-04). The {@code PENDING}
 * posts are cancelled first: a publication in progress ends before (row lock), so its post is then removed too. A
 * {@code PUBLISHED} post is deleted with the token of its account in at most {@code delete-attempts} attempts, then
 * becomes {@code DELETED}; when it cannot be, it stays {@code PUBLISHED} with the reason in {@code last_error_code}
 * and the error is logged. Every step is idempotent: a second delivery of the event finds nothing left to do.
 *
 * <p>Instagram (SOC-05): the Instagram API with Instagram Login offers no deletion of a published media (the deletion
 * of the Instagram API is documented for Facebook Login only). A published Instagram post stays {@code PUBLISHED} with
 * {@code delete-unsupported}; the seller removes it in the app, as the deletion screen of the account says ("as far
 * as possible").
 *
 * <p>The house posts (SOC-07), on Soukly's own Page and Instagram account, follow the same rules with the connection
 * of the Soukly account of {@link HouseProperties}. They are also removed when the listing leaves the site for a while
 * ({@code listing.deactivated}, {@code listing.expired}): Soukly's Page shows only listings one can buy.
 */
@Service
@EnableConfigurationProperties(FacebookPostingProperties.class)
public class PostRemoval {

    private static final Logger log = LoggerFactory.getLogger(PostRemoval.class);

    static final String NOT_CONNECTED = "not-connected";
    static final String ACCOUNT_CHANGED = "account-changed";
    static final String DELETE_UNSUPPORTED = "delete-unsupported";

    private final SocialPostRepository posts;
    private final SocialPostRepository housePosts;
    private final HouseProperties house;
    private final SocialConnectionRepository connections;
    private final ConnectionHealth connectionHealth;
    private final FacebookGraphClient facebook;
    private final FacebookPostingProperties properties;
    private final PostMetrics metrics;

    PostRemoval(SocialPostRepository posts, @Qualifier(PostChannelsConfig.HOUSE) SocialPostRepository housePosts,
            HouseProperties house, SocialConnectionRepository connections, ConnectionHealth connectionHealth,
            FacebookGraphClient facebook, FacebookPostingProperties properties, PostMetrics metrics) {
        this.posts = posts;
        this.housePosts = housePosts;
        this.house = house;
        this.connections = connections;
        this.connectionHealth = connectionHealth;
        this.facebook = facebook;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** {@code listing.deleted}: cancels the posts to come, removes the published ones, on every channel. */
    public void removeListing(UUID listingId) {
        removeListing(posts, listingId, "deleted");
        removeListing(housePosts, listingId, "deleted");
    }

    /**
     * {@code listing.deactivated}, {@code listing.expired}: only the house posts (SOC-07); the posts on the seller's
     * accounts stay, as in SOC-04.
     */
    public void removeHouseListing(UUID listingId) {
        removeListing(housePosts, listingId, "off the site");
    }

    /** {@code user.deleted}, before the connections are deleted: same for every listing of the member. */
    public void removeUser(UUID userId) {
        removeUser(posts, userId);
        removeUser(housePosts, userId);
    }

    /** Deletes every post row of the member, in the transaction that deletes their connections. */
    public void forget(UUID userId) {
        posts.deleteAll(userId);
        housePosts.deleteAll(userId);
    }

    private void removeListing(SocialPostRepository repository, UUID listingId, String why) {
        List<Network> cancelled = repository.cancelPendingOfListing(listingId);
        if (!cancelled.isEmpty()) {
            log.info("Listing {} {}: {}{} post(s) cancelled before publication", listingId, why, cancelled.size(),
                    repository.channel().label());
            cancelled.forEach(network -> metrics.entered(repository.channel(), network, PostStatus.CANCELLED));
        }
        remove(repository, repository.publishedOfListing(listingId));
    }

    private void removeUser(SocialPostRepository repository, UUID userId) {
        List<Network> cancelled = repository.cancelPendingOfUser(userId);
        if (!cancelled.isEmpty()) {
            log.info("Account {} deleted: {}{} post(s) cancelled before publication", userId, cancelled.size(),
                    repository.channel().label());
            cancelled.forEach(network -> metrics.entered(repository.channel(), network, PostStatus.CANCELLED));
        }
        remove(repository, repository.publishedOfUser(userId));
    }

    private void remove(SocialPostRepository repository, List<SocialPost> published) {
        for (SocialPost post : published) {
            if (post.network() == Network.FACEBOOK) {
                removeFromFacebook(repository, post);
            } else if (post.network() == Network.INSTAGRAM) {
                repository.markNotDeleted(post.id(), DELETE_UNSUPPORTED);
                log.warn("Instagram{} post of listing {} not deleted: the API offers no deletion",
                        repository.channel().label(), post.listingId());
            }
        }
    }

    private void removeFromFacebook(SocialPostRepository repository, SocialPost post) {
        Optional<PublishingAccount> account = repository.channel() == PostChannel.HOUSE && !house.enabled()
                ? Optional.empty()
                : connections.findForPublishing(FacebookPublisher.accountOwner(post, repository.channel(), house),
                        Network.FACEBOOK);
        if (account.isEmpty()) {
            notDeleted(repository, post, NOT_CONNECTED);
            return;
        }
        if (post.externalAccountId() != null && !post.externalAccountId().equals(account.get().externalId())) {
            // Another Page was connected since: its token cannot delete a post of the previous one
            notDeleted(repository, post, ACCOUNT_CHANGED);
            return;
        }
        String reason = null;
        for (int attempt = 1; attempt <= properties.deleteAttempts(); attempt++) {
            try {
                facebook.deletePost(post.externalPostId(), account.get().token());
                deleted(repository, post);
                return;
            } catch (GraphApiException e) {
                GraphFailure failure = GraphFailure.of(e);
                if (failure.kind() == GraphFailure.Kind.NOT_FOUND) {
                    // Already deleted on Facebook (by the seller, or by a previous attempt)
                    deleted(repository, post);
                    return;
                }
                reason = failure.errorCode();
                log.warn("Facebook{} post of listing {} not deleted (attempt {}): {} {}", repository.channel().label(),
                        post.listingId(), attempt, e.getMessage(), e.error() == null ? "" : e.error().message());
                if (failure.kind() == GraphFailure.Kind.TOKEN_INVALID) {
                    connectionHealth.needsReconnect(account.get().id());
                    break;
                }
                if (failure.kind() != GraphFailure.Kind.TEMPORARY) {
                    break;
                }
                if (attempt < properties.deleteAttempts()) {
                    pause();
                }
            }
        }
        notDeleted(repository, post, reason);
    }

    private void deleted(SocialPostRepository repository, SocialPost post) {
        repository.markDeleted(post.id());
        metrics.entered(repository.channel(), post.network(), PostStatus.DELETED);
        log.info("Facebook{} post of listing {} deleted", repository.channel().label(), post.listingId());
    }

    private void notDeleted(SocialPostRepository repository, SocialPost post, String reason) {
        repository.markNotDeleted(post.id(), reason);
        log.error("Facebook{} post of listing {} could not be deleted: {}", repository.channel().label(),
                post.listingId(), reason);
    }

    private void pause() {
        try {
            Thread.sleep(properties.deleteRetryInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
