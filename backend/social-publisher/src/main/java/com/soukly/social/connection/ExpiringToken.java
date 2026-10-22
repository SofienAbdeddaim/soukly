package com.soukly.social.connection;

import java.time.Instant;
import java.util.UUID;

/** A token that expires soon, read back in clear to refresh it: never logged. */
public record ExpiringToken(UUID id, UUID userId, Network network, String token, Instant expiresAt) {

    /** The token is never part of the string form. */
    @Override
    public String toString() {
        return "ExpiringToken[id=" + id + ", network=" + network + ", expiresAt=" + expiresAt + "]";
    }
}
