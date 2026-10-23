package com.soukly.social.post;

/** Status of a post of a listing on a social network (SOC-04), column {@code social_posts.status}. */
public enum PostStatus {
    /** To publish, at {@code next_attempt_at}. */
    PENDING,
    /** Published: {@code external_post_id} and {@code permalink} known. */
    PUBLISHED,
    /** Not published, the reason in {@code last_error_code}: {@code not-connected}, {@code needs-reconnect}. */
    SKIPPED,
    /** Definitive failure, the reason in {@code last_error_code}. */
    FAILED,
    /** The listing was deleted before the publication. */
    CANCELLED,
    /** The post was removed from the network after the listing was deleted. */
    DELETED
}
