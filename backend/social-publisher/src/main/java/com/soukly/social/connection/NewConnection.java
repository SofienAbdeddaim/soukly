package com.soukly.social.connection;

/**
 * An account of a social network to connect to a member, with its token in clear: encrypted by
 * {@link SocialConnectionRepository#save}, never logged.
 *
 * @param externalId     Facebook: id of the Page
 * @param externalUserId Facebook: app-scoped id of the person who connected it, or {@code null}
 * @param displayName    Facebook: name of the Page
 * @param pictureUrl     picture of the account, or {@code null}
 * @param token          Facebook: Page access token
 */
public record NewConnection(Network network, String externalId, String externalUserId, String displayName,
        String pictureUrl, String token) {

    /** The token is never part of the string form. */
    @Override
    public String toString() {
        return "NewConnection[network=" + network + ", externalId=" + externalId + "]";
    }
}
