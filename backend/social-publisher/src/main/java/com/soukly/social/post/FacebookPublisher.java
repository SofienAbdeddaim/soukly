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
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.social.caption.CaptionBuilder;
import com.soukly.social.caption.CaptionInput;
import com.soukly.social.caption.CaptionLanguage;
import com.soukly.social.caption.CaptionUnavailableException;
import com.soukly.social.caption.ReferentialLabels;
import com.soukly.social.connection.ConnectionHealth;
import com.soukly.social.connection.ConnectionStatus;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.PublishingAccount;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.facebook.FacebookGraphClient;
import com.soukly.social.facebook.FacebookProperties;
import com.soukly.social.graph.GraphApiException;

/**
 * Publishes the {@code PENDING} Facebook posts that are due (SOC-04), every {@code publish-interval}: for each one,
 * its photos (at most {@code max-photos}, in the seller's order) are uploaded to the Page as unpublished photos from
 * their public URL, then one post of the Page carries the text of the {@link CaptionBuilder} and the photos
 * ({@code attached_media}). The post is then {@code PUBLISHED} with its id and permalink.
 *
 * <p>Each post is handled in its own transaction under a {@code FOR UPDATE SKIP LOCKED} row lock, like the outbox relay
 * of ANN-03: two runs never publish the same post, and a deletion of the listing waits for the publication in
 * progress. One run at a time in this instance. The pace is kept by {@link PublishRateLimiter}: a post whose Page
 * published less than {@code page-interval} ago is postponed, and the run stops when the calls of the minute are used.
 * A temporary failure is tried again after the {@code retry-delays}; an invalid token puts the connection in
 * {@code NEEDS_RECONNECT}. Tokens are never logged; the messages of Meta are logged cleaned ({@code GraphError}).
 */
@Component
@EnableScheduling
@EnableConfigurationProperties(FacebookPostingProperties.class)
public class FacebookPublisher {

    private static final Logger log = LoggerFactory.getLogger(FacebookPublisher.class);

    /** Posts handled at most by a run; the next run continues. */
    static final int MAX_PER_RUN = 100;

    static final String CAPTION_UNAVAILABLE = "caption-unavailable";

    private final SocialPostRepository posts;
    private final SocialConnectionRepository connections;
    private final ConnectionHealth connectionHealth;
    private final FacebookGraphClient facebook;
    private final FacebookProperties facebookProperties;
    private final FacebookPostingProperties properties;
    private final CaptionBuilder captions;
    private final ReferentialLabels labels;
    private final PostMetrics metrics;
    private final TransactionTemplate transactions;
    private final PublishRateLimiter limiter;
    private final PostAttempts attempts;
    private final AtomicBoolean running = new AtomicBoolean();

    FacebookPublisher(SocialPostRepository posts, SocialConnectionRepository connections,
            ConnectionHealth connectionHealth, FacebookGraphClient facebook, FacebookProperties facebookProperties,
            FacebookPostingProperties properties, CaptionBuilder captions, ReferentialLabels labels,
            PostMetrics metrics, TransactionTemplate transactions, Clock clock) {
        this.posts = posts;
        this.connections = connections;
        this.connectionHealth = connectionHealth;
        this.facebook = facebook;
        this.facebookProperties = facebookProperties;
        this.properties = properties;
        this.captions = captions;
        this.labels = labels;
        this.metrics = metrics;
        this.transactions = transactions;
        this.limiter = new PublishRateLimiter(properties.pageInterval(), properties.callsPerMinute(), clock);
        this.attempts = new PostAttempts(Network.FACEBOOK, posts, metrics, properties.retryDelays(), clock);
    }

    /** What a run did. */
    public record Result(int published, int retried, int ended, int postponed) {
    }

    @Scheduled(fixedDelayString = "${soukly.social.posts.facebook.publish-interval:PT10S}",
            initialDelayString = "${soukly.social.posts.facebook.publish-initial-delay:PT30S}")
    void scheduledRun() {
        publishDue();
    }

    /** Publishes the posts that are due; does nothing while another run of this instance is in progress. */
    public Result publishDue() {
        if (!facebookProperties.configured() || !running.compareAndSet(false, true)) {
            return new Result(0, 0, 0, 0);
        }
        try {
            int[] counts = new int[4];
            for (int i = 0; i < MAX_PER_RUN; i++) {
                Optional<PostAttempts.Outcome> outcome = transactions.execute(status -> posts.lockNextDue(Network.FACEBOOK)
                        .map(this::handle));
                if (outcome == null || outcome.isEmpty() || outcome.get() == PostAttempts.Outcome.THROTTLED) {
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
        Optional<PublishingAccount> found = connections.findForPublishing(post.userId(), Network.FACEBOOK);
        if (found.isEmpty() || found.get().status() == ConnectionStatus.NEEDS_RECONNECT) {
            // Disconnected, or Meta refused the token, since the listing was published
            return attempts.skipped(post, found.isEmpty() ? ListingPosts.NOT_CONNECTED : ListingPosts.NEEDS_RECONNECT);
        }
        PublishingAccount page = found.get();
        PostContent content = post.content();
        List<URI> photos = content.photoUrls().stream().limit(properties.maxPhotos()).toList();
        PublishRateLimiter.Permit permit = limiter.acquire(page.externalId(), photos.size() + 1);
        if (!permit.granted()) {
            posts.postpone(post.id(), permit.notBefore());
            return permit.pageBusy() ? PostAttempts.Outcome.POSTPONED : PostAttempts.Outcome.THROTTLED;
        }
        long start = System.nanoTime();
        try {
            String message = captions.build(captionInput(content, labels, trackedLink(content.listingUrl()))).text();
            List<String> photoIds = new ArrayList<>();
            for (URI photo : photos) {
                photoIds.add(facebook.uploadUnpublishedPhoto(page.externalId(), page.token(), photo));
            }
            String postId = facebook.createPost(page.externalId(), page.token(), message, photoIds);
            limiter.posted(page.externalId());
            posts.markPublished(post.id(), page.externalId(), postId, permalink(postId, page.token()));
            metrics.entered(Network.FACEBOOK, PostStatus.PUBLISHED);
            metrics.published(Network.FACEBOOK, "published", elapsed(start));
            log.info("Listing {} published on Facebook with {} photo(s)", post.listingId(), photoIds.size());
            return PostAttempts.Outcome.PUBLISHED;
        } catch (CaptionUnavailableException e) {
            return attempts.temporaryFailure(post, CAPTION_UNAVAILABLE, elapsed(start));
        } catch (GraphApiException e) {
            GraphFailure failure = GraphFailure.of(e);
            log.warn("Facebook refused the post of listing {}: {} {}", post.listingId(), e.getMessage(),
                    e.error() == null || e.error().message() == null ? "" : "- " + e.error().message());
            return switch (failure.kind()) {
                case TEMPORARY -> attempts.temporaryFailure(post, failure.errorCode(), elapsed(start));
                case TOKEN_INVALID -> {
                    connectionHealth.needsReconnect(page.id());
                    yield attempts.failed(post, failure.errorCode(), elapsed(start));
                }
                default -> attempts.failed(post, failure.errorCode(), elapsed(start));
            };
        }
    }

    /** The permalink is a convenience: the post is published even when Meta does not give it. */
    private String permalink(String postId, String token) {
        try {
            return facebook.permalink(postId, token);
        } catch (GraphApiException e) {
            log.warn("Permalink of Facebook post not read: {}", e.getMessage());
            return null;
        }
    }

    /** The input of the text of a post, with the labels of the referentials in the language of the text. */
    static CaptionInput captionInput(PostContent content, ReferentialLabels labels, URI link) {
        CaptionLanguage language = CaptionLanguage.of(content.language()).orElse(CaptionLanguage.FR);
        String category = labels.subcategory(content.categoryCode()).map(label -> label.in(language)).orElse(null);
        String governorate = labels.governorate(content.governorateCode()).map(label -> label.in(language))
                .orElse(null);
        String delegation = content.delegationCode() == null || content.governorateCode() == null ? null
                : labels.delegation(content.governorateCode(), content.delegationCode())
                        .map(label -> label.in(language)).orElse(null);
        return new CaptionInput(content.title(), content.description(), content.priceMode(),
                CaptionInput.FIXED.equals(content.priceMode()) ? content.price() : null, content.negotiable(),
                category, governorate, delegation, link, language);
    }

    /** The link of the post, with the parameters that attribute the visits to it. */
    static URI trackedLink(URI listingUrl) {
        return UriComponentsBuilder.fromUri(listingUrl)
                .queryParam("utm_source", "facebook")
                .queryParam("utm_medium", "social")
                .queryParam("utm_campaign", "soukly_share")
                .build()
                .toUri();
    }

    static Duration elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
