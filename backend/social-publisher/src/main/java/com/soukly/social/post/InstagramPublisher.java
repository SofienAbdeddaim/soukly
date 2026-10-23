package com.soukly.social.post;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.social.caption.CaptionBuilder;
import com.soukly.social.caption.CaptionUnavailableException;
import com.soukly.social.caption.ReferentialLabels;
import com.soukly.social.connection.ConnectionHealth;
import com.soukly.social.connection.ConnectionStatus;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.PublishingAccount;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.graph.GraphApiException;
import com.soukly.social.instagram.InstagramGraphClient;
import com.soukly.social.instagram.InstagramProperties;
import com.soukly.social.photo.PhotoVariantException;
import com.soukly.social.photo.PhotoVariantService;

/**
 * Publishes the {@code PENDING} Instagram posts that are due (SOC-05), every {@code publish-interval}, with the
 * content publishing of the Instagram API with Instagram Login:
 *
 * <ol>
 * <li>the limit of the account is read ({@code content_publishing_limit}): once reached, the post waits
 * {@code quota-retry-delay} without counting an attempt;</li>
 * <li>each photo (at most {@code max-photos}, in the seller's order) gets its JPEG version
 * ({@link PhotoVariantService}), which Instagram fetches from its public URL;</li>
 * <li>one photo: one image container with the caption; 2 to 10: one container per photo marked as a carousel item,
 * then a carousel container with the children and the caption;</li>
 * <li>the status of that container is read until it is {@code FINISHED}, {@code status-checks} times at most over
 * less than a minute; still in progress, the post is tried again later;</li>
 * <li>the container is published, and the post is {@code PUBLISHED} with the media id and its permalink.</li>
 * </ol>
 *
 * <p>Same rules as {@link FacebookPublisher}: one transaction per post under a {@code FOR UPDATE SKIP LOCKED} row lock,
 * skipped when the account is not connected or needs a reconnection, retries after a temporary failure, an invalid
 * token puts the connection in {@code NEEDS_RECONNECT}. The caption comes from
 * {@link CaptionBuilder#buildForInstagram}: no link, the short address of the listing instead. Tokens are never
 * logged.
 */
@Component
@EnableScheduling
@EnableConfigurationProperties(InstagramPostingProperties.class)
public class InstagramPublisher {

    private static final Logger log = LoggerFactory.getLogger(InstagramPublisher.class);

    /** Posts handled at most by a run; the next run continues. */
    static final int MAX_PER_RUN = 100;

    static final String NOT_READY = "media-not-ready";
    static final String MEDIA_ERROR = "media-error";
    static final String FINISHED = "FINISHED";
    private static final List<String> FAILED_STATUSES = List.of("ERROR", "EXPIRED");

    private final SocialPostRepository posts;
    private final SocialConnectionRepository connections;
    private final ConnectionHealth connectionHealth;
    private final InstagramGraphClient instagram;
    private final InstagramProperties instagramProperties;
    private final InstagramPostingProperties properties;
    private final CaptionBuilder captions;
    private final ReferentialLabels labels;
    private final PhotoVariantService variants;
    private final PostMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final PostAttempts attempts;
    private final AtomicBoolean running = new AtomicBoolean();

    InstagramPublisher(SocialPostRepository posts, SocialConnectionRepository connections,
            ConnectionHealth connectionHealth, InstagramGraphClient instagram, InstagramProperties instagramProperties,
            InstagramPostingProperties properties, CaptionBuilder captions, ReferentialLabels labels,
            PhotoVariantService variants, PostMetrics metrics, TransactionTemplate transactions, Clock clock) {
        this.posts = posts;
        this.connections = connections;
        this.connectionHealth = connectionHealth;
        this.instagram = instagram;
        this.instagramProperties = instagramProperties;
        this.properties = properties;
        this.captions = captions;
        this.labels = labels;
        this.variants = variants;
        this.metrics = metrics;
        this.transactions = transactions;
        this.clock = clock;
        this.attempts = new PostAttempts(Network.INSTAGRAM, posts, metrics, properties.retryDelays(), clock);
    }

    /** What a run did. */
    public record Result(int published, int retried, int ended, int postponed) {
    }

    @Scheduled(fixedDelayString = "${soukly.social.posts.instagram.publish-interval:PT10S}",
            initialDelayString = "${soukly.social.posts.instagram.publish-initial-delay:PT30S}")
    void scheduledRun() {
        publishDue();
    }

    /** Publishes the posts that are due; does nothing while another run of this instance is in progress. */
    public Result publishDue() {
        if (!instagramProperties.configured() || !running.compareAndSet(false, true)) {
            return new Result(0, 0, 0, 0);
        }
        try {
            int[] counts = new int[4];
            for (int i = 0; i < MAX_PER_RUN; i++) {
                Optional<PostAttempts.Outcome> outcome = transactions.execute(status -> posts
                        .lockNextDue(Network.INSTAGRAM).map(this::handle));
                if (outcome == null || outcome.isEmpty()) {
                    break;
                }
                counts[outcome.get().ordinal()]++;
            }
            return new Result(counts[0], counts[1], counts[2], counts[3]);
        } finally {
            running.set(false);
        }
    }

    private PostAttempts.Outcome handle(SocialPost post) {
        MDC.put("listingId", post.listingId().toString());
        if (post.requestId() != null) {
            MDC.put("requestId", post.requestId());
        }
        try {
            return publish(post);
        } finally {
            MDC.remove("listingId");
            MDC.remove("requestId");
        }
    }

    private PostAttempts.Outcome publish(SocialPost post) {
        Optional<PublishingAccount> found = connections.findForPublishing(post.userId(), Network.INSTAGRAM);
        if (found.isEmpty() || found.get().status() == ConnectionStatus.NEEDS_RECONNECT) {
            return attempts.skipped(post, found.isEmpty() ? ListingPosts.NOT_CONNECTED : ListingPosts.NEEDS_RECONNECT);
        }
        PublishingAccount account = found.get();
        PostContent content = post.content();
        long start = System.nanoTime();
        try {
            if (instagram.publishingLimit(account.externalId(), account.token()).reached()) {
                return quotaReached(post);
            }
            String caption = captions.buildForInstagram(
                    FacebookPublisher.captionInput(content, labels, content.listingUrl()), content.shortRef()).text();
            List<URI> images = new ArrayList<>();
            for (URI photo : content.photoUrls().stream().limit(properties.maxPhotos()).toList()) {
                images.add(variants.instagram(photo));
            }
            String container = container(account, images, caption);
            String status = waitUntilReady(container, account.token());
            if (FAILED_STATUSES.contains(status)) {
                log.warn("Instagram container of listing {} ended in {}", post.listingId(), status);
                return "EXPIRED".equals(status)
                        ? attempts.temporaryFailure(post, NOT_READY, FacebookPublisher.elapsed(start))
                        : attempts.failed(post, MEDIA_ERROR, FacebookPublisher.elapsed(start));
            }
            if (!FINISHED.equals(status)) {
                return attempts.temporaryFailure(post, NOT_READY, FacebookPublisher.elapsed(start));
            }
            String mediaId = instagram.publish(account.externalId(), account.token(), container);
            posts.markPublished(post.id(), account.externalId(), mediaId, permalink(mediaId, account.token()));
            metrics.entered(Network.INSTAGRAM, PostStatus.PUBLISHED);
            metrics.published(Network.INSTAGRAM, "published", FacebookPublisher.elapsed(start));
            log.info("Listing {} published on Instagram with {} photo(s)", post.listingId(), images.size());
            return PostAttempts.Outcome.PUBLISHED;
        } catch (CaptionUnavailableException e) {
            return attempts.temporaryFailure(post, FacebookPublisher.CAPTION_UNAVAILABLE,
                    FacebookPublisher.elapsed(start));
        } catch (PhotoVariantException e) {
            log.warn("Instagram version of a photo of listing {} not written: {}", post.listingId(), e.getMessage());
            return e.temporary() ? attempts.temporaryFailure(post, e.code(), FacebookPublisher.elapsed(start))
                    : attempts.failed(post, e.code(), FacebookPublisher.elapsed(start));
        } catch (GraphApiException e) {
            GraphFailure failure = GraphFailure.ofInstagram(e);
            log.warn("Instagram refused the post of listing {}: {} {}", post.listingId(), e.getMessage(),
                    e.error() == null || e.error().message() == null ? "" : "- " + e.error().message());
            return switch (failure.kind()) {
                case QUOTA -> quotaReached(post);
                case TEMPORARY -> attempts.temporaryFailure(post, failure.errorCode(),
                        FacebookPublisher.elapsed(start));
                case TOKEN_INVALID -> {
                    connectionHealth.needsReconnect(account.id());
                    yield attempts.failed(post, failure.errorCode(), FacebookPublisher.elapsed(start));
                }
                default -> attempts.failed(post, failure.errorCode(), FacebookPublisher.elapsed(start));
            };
        }
    }

    /** A limit of Instagram is not a failure: the post waits, its attempts unchanged. */
    private PostAttempts.Outcome quotaReached(SocialPost post) {
        return attempts.postponed(post, clock.instant().plus(properties.quotaRetryDelay()), GraphFailure.QUOTA_REACHED);
    }

    /** The container to publish: an image for one photo, a carousel of item containers for more. */
    private String container(PublishingAccount account, List<URI> images, String caption) {
        if (images.size() == 1) {
            return instagram.createImageContainer(account.externalId(), account.token(), images.getFirst(), caption);
        }
        List<String> children = new ArrayList<>();
        for (URI image : images) {
            children.add(instagram.createCarouselItem(account.externalId(), account.token(), image));
        }
        return instagram.createCarousel(account.externalId(), account.token(), children, caption);
    }

    /** The last status read: {@code FINISHED} as soon as it is, otherwise after {@code status-checks} readings. */
    private String waitUntilReady(String container, String token) {
        String status = null;
        for (int check = 0; check < properties.statusChecks(); check++) {
            if (check > 0) {
                pause(properties.statusInterval());
            }
            status = instagram.containerStatus(container, token);
            if (FINISHED.equals(status) || FAILED_STATUSES.contains(status)) {
                return status;
            }
        }
        return status;
    }

    /** The permalink is a convenience: the post is published even when Instagram does not give it. */
    private String permalink(String mediaId, String token) {
        try {
            return instagram.permalink(mediaId, token);
        } catch (GraphApiException e) {
            log.warn("Permalink of Instagram media not read: {}", e.getMessage());
            return null;
        }
    }

    private static void pause(Duration interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
