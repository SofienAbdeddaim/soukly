package com.soukly.social.connection;

import java.time.Instant;

/** A connection as {@code GET /social/connections} returns it: never the token. */
public record ConnectionView(Network network, ConnectionStatus status, String displayName, String pictureUrl,
        Instant connectedAt) {
}
