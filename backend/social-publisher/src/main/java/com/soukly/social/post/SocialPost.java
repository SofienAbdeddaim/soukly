package com.soukly.social.post;

import java.time.Instant;
import java.util.UUID;

import com.soukly.social.connection.Network;

/**
 * A row of {@code social_posts} or {@code house_posts}, as the publication and the removal read it.
 *
 * @param attempts  failed attempts so far
 * @param createdAt when the post was recorded, at the consumption of {@code listing.published}
 */
record SocialPost(UUID id, UUID listingId, UUID userId, Network network, PostStatus status, PostContent content,
        String externalAccountId, String externalPostId, int attempts, String requestId, Instant createdAt) {
}
