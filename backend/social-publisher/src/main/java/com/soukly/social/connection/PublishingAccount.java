package com.soukly.social.connection;

import java.util.UUID;

/**
 * A connection as the publication reads it (SOC-04): the account to post on and its token, decrypted. Never logged nor
 * returned by the API.
 *
 * @param id         the row of {@code social_connections}, for {@link SocialConnectionRepository#markNeedsReconnect}
 * @param externalId Facebook: id of the Page
 */
public record PublishingAccount(UUID id, String externalId, ConnectionStatus status, String token) {

    /** The token is never part of the string form. */
    @Override
    public String toString() {
        return "PublishingAccount[id=" + id + ", externalId=" + externalId + ", status=" + status + "]";
    }
}
