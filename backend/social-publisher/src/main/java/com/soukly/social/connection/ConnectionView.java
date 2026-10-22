package com.soukly.social.connection;

import java.time.Instant;

/**
 * A connection as {@code GET /social/connections} returns it: never the token.
 *
 * @param autoShare whether the network is ticked by default on the preview of a new listing (SOC-03)
 */
public record ConnectionView(Network network, ConnectionStatus status, String displayName, String pictureUrl,
        Instant connectedAt, boolean autoShare) {
}
