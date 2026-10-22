package com.soukly.social.connection;

import java.time.Instant;

/**
 * An account of a social network to connect to a member, with its token in clear: encrypted by
 * {@link SocialConnectionRepository#save}, never logged.
 *
 * @param externalId     Facebook: id of the Page; Instagram: id of the professional account
 * @param externalUserId Facebook: app-scoped id of the person who connected it; Instagram: app-scoped id of the
 *                       account; or {@code null}
 * @param displayName    Facebook: name of the Page; Instagram: {@code @username}
 * @param pictureUrl     picture of the account, or {@code null}
 * @param token          Facebook: Page access token; Instagram: long-lived user access token
 * @param tokenExpiresAt when the token expires, or {@code null} when it does not expire on its own (a Page token)
 */
public record NewConnection(Network network, String externalId, String externalUserId, String displayName,
        String pictureUrl, String token, Instant tokenExpiresAt) {

    /** The token is never part of the string form. */
    @Override
    public String toString() {
        return "NewConnection[network=" + network + ", externalId=" + externalId + "]";
    }
}
