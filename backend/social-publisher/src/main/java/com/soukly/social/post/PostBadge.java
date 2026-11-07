package com.soukly.social.post;

/** What the seller sees of a post on "My listings" (SOC-06). */
public enum PostBadge {
    /** Published, with a link to the post. */
    PUBLISHED,
    /** Waiting for its publication, or for a new attempt. */
    PENDING,
    /** Blocked until the member connects the network again: link to "My networks". */
    TO_RECONNECT,
    /** Failed: the reason, and "Try again" while manual attempts are left. */
    FAILED
}
