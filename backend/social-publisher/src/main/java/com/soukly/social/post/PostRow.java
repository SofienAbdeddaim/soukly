package com.soukly.social.post;

import java.time.Instant;
import java.util.UUID;

import com.soukly.social.connection.Network;

/**
 * A row of {@code social_posts} as the seller sees it (SOC-06).
 *
 * @param errorCode     {@code last_error_code}
 * @param attempts      failed automatic attempts since the last (re)queue
 * @param manualRetries "Try again" used so far
 * @param title         title of the listing when it was published, for the relaunch banner
 */
record PostRow(UUID listingId, Network network, PostStatus status, String permalink, String errorCode, int attempts,
        int manualRetries, Instant createdAt, Instant updatedAt, String title) {
}
