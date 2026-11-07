package com.soukly.social.post;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a row of {@code social_posts} into what the seller sees on "My listings" (SOC-06):
 *
 * <table>
 * <caption>Badges</caption>
 * <tr><th>Row</th><th>Badge</th></tr>
 * <tr><td>{@code PUBLISHED}</td><td>{@link PostBadge#PUBLISHED}</td></tr>
 * <tr><td>{@code PENDING}</td><td>{@link PostBadge#PENDING}</td></tr>
 * <tr><td>{@code FAILED} or {@code SKIPPED} with {@code needs-reconnect} or {@code permission-missing}, the network
 * not connected again since</td><td>{@link PostBadge#TO_RECONNECT}, never tried again by hand</td></tr>
 * <tr><td>the same, connected again since</td><td>{@link PostBadge#FAILED}, reason
 * {@link PostFailureReason#NOT_CONNECTED}</td></tr>
 * <tr><td>{@code FAILED}, any other code</td><td>{@link PostBadge#FAILED}</td></tr>
 * <tr><td>{@code SKIPPED} {@code not-connected}, {@code CANCELLED}, {@code DELETED}</td><td>none</td></tr>
 * </table>
 *
 * <p>A {@code FAILED} post can be tried again by hand {@code maxManualRetries} times. The reason of a failure is
 * {@link PostFailureReason#MEDIA_REJECTED} for {@code media-rejected}, {@link PostFailureReason#RETRIES_EXHAUSTED}
 * when every automatic attempt was used ({@code attempts} reached the attempts of the network), and
 * {@link PostFailureReason#REJECTED} otherwise.
 */
final class PostBadges {

    /** Codes that only a new connection solves. */
    static final Set<String> CONNECTION_CODES = Set.of(ListingPosts.NEEDS_RECONNECT, GraphFailure.PERMISSION);

    private final int maxManualRetries;
    private final int facebookAttempts;
    private final int instagramAttempts;

    /**
     * @param facebookAttempts  automatic attempts of a Facebook post, the first one and its new attempts
     * @param instagramAttempts the same for Instagram
     */
    PostBadges(int maxManualRetries, int facebookAttempts, int instagramAttempts) {
        this.maxManualRetries = maxManualRetries;
        this.facebookAttempts = facebookAttempts;
        this.instagramAttempts = instagramAttempts;
    }

    /**
     * @param connectedSince when the network of the row was last connected, empty when it is not connected now
     * @return empty when the post shows no badge
     */
    Optional<PostStatusView> view(PostRow row, Optional<Instant> connectedSince) {
        int left = Math.max(0, maxManualRetries - row.manualRetries());
        return switch (row.status()) {
            case PUBLISHED -> Optional.of(view(row, PostBadge.PUBLISHED, null, false, left));
            case PENDING -> Optional.of(view(row, PostBadge.PENDING, null, false, left));
            case CANCELLED, DELETED -> Optional.empty();
            case SKIPPED, FAILED -> {
                if (CONNECTION_CODES.contains(row.errorCode())) {
                    yield Optional.of(reconnectedSince(row, connectedSince)
                            ? view(row, PostBadge.FAILED, PostFailureReason.NOT_CONNECTED, left > 0, left)
                            : view(row, PostBadge.TO_RECONNECT, null, false, left));
                }
                if (row.status() == PostStatus.SKIPPED) {
                    // not-connected: the seller had not connected this network, nothing to show
                    yield Optional.empty();
                }
                yield Optional.of(view(row, PostBadge.FAILED, reason(row), left > 0, left));
            }
        };
    }

    /** Whether the network was connected again after the post was blocked. */
    static boolean reconnectedSince(PostRow row, Optional<Instant> connectedSince) {
        return connectedSince.filter(since -> since.isAfter(row.updatedAt())).isPresent();
    }

    private PostFailureReason reason(PostRow row) {
        if (GraphFailure.MEDIA_REJECTED.equals(row.errorCode())) {
            return PostFailureReason.MEDIA_REJECTED;
        }
        int attempts = switch (row.network()) {
            case FACEBOOK -> facebookAttempts;
            case INSTAGRAM -> instagramAttempts;
        };
        return row.attempts() >= attempts ? PostFailureReason.RETRIES_EXHAUSTED : PostFailureReason.REJECTED;
    }

    private static PostStatusView view(PostRow row, PostBadge badge, PostFailureReason reason, boolean retryable,
            int left) {
        return new PostStatusView(row.network(), row.status(), badge, reason,
                badge == PostBadge.PUBLISHED ? row.permalink() : null, row.errorCode(), retryable, left,
                row.updatedAt());
    }
}
