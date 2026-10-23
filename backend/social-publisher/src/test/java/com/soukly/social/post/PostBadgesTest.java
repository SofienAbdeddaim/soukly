package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.soukly.social.connection.Network;

/** What the seller sees of each row of {@code social_posts} (SOC-06). */
class PostBadgesTest {

    private static final Instant UPDATED = Instant.parse("2026-10-20T10:00:00Z");
    private static final Optional<Instant> RECONNECTED = Optional.of(UPDATED.plusSeconds(3600));
    private static final Optional<Instant> CONNECTED_BEFORE = Optional.of(UPDATED.minusSeconds(3600));

    private final PostBadges badges = new PostBadges(3, 6, 6);

    @Test
    void aPublishedPostLinksToItsPermalink() {
        PostStatusView view = badges.view(row(Network.FACEBOOK, PostStatus.PUBLISHED, null, 0, 0),
                CONNECTED_BEFORE).orElseThrow();

        assertThat(view.badge()).isEqualTo(PostBadge.PUBLISHED);
        assertThat(view.permalink()).isEqualTo("https://www.facebook.com/1/posts/2");
        assertThat(view.retryable()).isFalse();
    }

    @Test
    void aPendingPostIsInProgressWithoutLink() {
        PostStatusView view = badges.view(row(Network.INSTAGRAM, PostStatus.PENDING, "meta-2", 2, 0),
                CONNECTED_BEFORE).orElseThrow();

        assertThat(view.badge()).isEqualTo(PostBadge.PENDING);
        assertThat(view.permalink()).isNull();
        assertThat(view.reason()).isNull();
    }

    @Test
    void aPostBlockedByTheConnectionAsksToReconnectWithoutRetry() {
        for (PostStatus status : new PostStatus[] { PostStatus.FAILED, PostStatus.SKIPPED }) {
            for (String code : new String[] { "needs-reconnect", "permission-missing" }) {
                PostStatusView toReconnect = badges.view(row(Network.FACEBOOK, status, code, 1, 0), Optional.empty())
                        .orElseThrow();
                assertThat(toReconnect.badge()).isEqualTo(PostBadge.TO_RECONNECT);
                assertThat(toReconnect.retryable()).isFalse();
                // Connected before the post was blocked: still to reconnect
                assertThat(badges.view(row(Network.FACEBOOK, status, code, 1, 0), CONNECTED_BEFORE).orElseThrow()
                        .badge()).isEqualTo(PostBadge.TO_RECONNECT);
            }
        }
    }

    @Test
    void onceReconnectedABlockedPostIsAFailureThatCanBeTriedAgain() {
        PostStatusView view = badges.view(row(Network.FACEBOOK, PostStatus.SKIPPED, "needs-reconnect", 0, 0),
                RECONNECTED).orElseThrow();

        assertThat(view.badge()).isEqualTo(PostBadge.FAILED);
        assertThat(view.reason()).isEqualTo(PostFailureReason.NOT_CONNECTED);
        assertThat(view.retryable()).isTrue();
        assertThat(view.manualRetriesLeft()).isEqualTo(3);
    }

    @Test
    void theReasonOfAFailureIsThePhotosTheExhaustedAttemptsOrARefusal() {
        assertThat(badges.view(row(Network.INSTAGRAM, PostStatus.FAILED, "media-rejected", 1, 0), CONNECTED_BEFORE)
                .orElseThrow().reason()).isEqualTo(PostFailureReason.MEDIA_REJECTED);
        assertThat(badges.view(row(Network.INSTAGRAM, PostStatus.FAILED, "instagram-unavailable", 6, 0),
                CONNECTED_BEFORE).orElseThrow().reason()).isEqualTo(PostFailureReason.RETRIES_EXHAUSTED);
        assertThat(badges.view(row(Network.FACEBOOK, PostStatus.FAILED, "meta-324", 1, 0), CONNECTED_BEFORE)
                .orElseThrow().reason()).isEqualTo(PostFailureReason.REJECTED);
    }

    @Test
    void aFailureCanBeTriedAgainThreeTimes() {
        PostStatusView twoUsed = badges.view(row(Network.INSTAGRAM, PostStatus.FAILED, "meta-1", 6, 2),
                CONNECTED_BEFORE).orElseThrow();
        PostStatusView threeUsed = badges.view(row(Network.INSTAGRAM, PostStatus.FAILED, "meta-1", 6, 3),
                CONNECTED_BEFORE).orElseThrow();

        assertThat(twoUsed.retryable()).isTrue();
        assertThat(twoUsed.manualRetriesLeft()).isEqualTo(1);
        assertThat(threeUsed.badge()).isEqualTo(PostBadge.FAILED);
        assertThat(threeUsed.retryable()).isFalse();
        assertThat(threeUsed.manualRetriesLeft()).isZero();
    }

    @Test
    void aNetworkNotChosenOrAPostCancelledShowsNothing() {
        assertThat(badges.view(row(Network.FACEBOOK, PostStatus.SKIPPED, "not-connected", 0, 0), RECONNECTED))
                .isEmpty();
        assertThat(badges.view(row(Network.FACEBOOK, PostStatus.CANCELLED, null, 0, 0), RECONNECTED)).isEmpty();
        assertThat(badges.view(row(Network.FACEBOOK, PostStatus.DELETED, null, 0, 0), RECONNECTED)).isEmpty();
    }

    private static PostRow row(Network network, PostStatus status, String code, int attempts, int manualRetries) {
        return new PostRow(UUID.randomUUID(), network, status,
                status == PostStatus.PUBLISHED ? "https://www.facebook.com/1/posts/2" : null, code, attempts,
                manualRetries, UPDATED.minusSeconds(60), UPDATED, "Peugeot 208");
    }
}
