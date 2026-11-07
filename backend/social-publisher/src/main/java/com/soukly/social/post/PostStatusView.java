package com.soukly.social.post;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.soukly.social.connection.Network;

/**
 * A post of a listing in {@code GET /social/posts} (SOC-06).
 *
 * @param badge             what the seller sees
 * @param reason            {@link PostBadge#FAILED} only
 * @param errorCode         {@code last_error_code} of the row, a stable code ({@code media-rejected},
 *                          {@code meta-324}...), never the message of the network
 * @param retryable         whether "Try again" is offered
 * @param manualRetriesLeft "Try again" left for this post
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostStatusView(Network network, PostStatus status, PostBadge badge, PostFailureReason reason,
        String permalink, String errorCode, boolean retryable, int manualRetriesLeft, Instant updatedAt) {
}
