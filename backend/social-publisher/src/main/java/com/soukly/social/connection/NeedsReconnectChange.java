package com.soukly.social.connection;

import java.util.UUID;

/**
 * A connection that just went from {@code CONNECTED} to {@code NEEDS_RECONNECT} (SOC-06).
 *
 * @param displayName name of the Page or {@code @username} of the Instagram account
 * @param sendEmail   whether the member is told by email: no email for this connection within the last 24 hours
 */
record NeedsReconnectChange(UUID userId, Network network, String displayName, boolean sendEmail) {
}
