package com.soukly.user.session;

import com.soukly.user.account.UserAccount;
import com.soukly.user.jwt.AccessTokens.AccessToken;

/**
 * An open session: the access token for the body, the refresh token for the cookie only.
 */
record Session(AccessToken accessToken, UserAccount user, String refreshToken) {

    @Override
    public String toString() {
        return "Session[userId=" + user.id() + "]";
    }
}
