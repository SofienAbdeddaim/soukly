package com.soukly.user.session;

import java.util.UUID;

/**
 * Body of a successful login or refresh. The refresh token is never part of it: it travels in the cookie only.
 *
 * @param expiresIn validity of the access token in seconds
 * @param firstLogin {@code true} on the first login of the account (the front end then offers the welcome screen),
 *            always {@code false} on a refresh
 */
public record SessionResponse(String accessToken, String tokenType, long expiresIn, User user, boolean firstLogin) {

    /** Minimal profile for the front end; {@code preferredLanguage} is {@code fr}, {@code ar} or {@code en}. */
    public record User(UUID id, String displayName, String preferredLanguage) {
    }

    static SessionResponse of(Session session) {
        return new SessionResponse(session.accessToken().value(), "Bearer", session.accessToken().expiresIn(),
                new User(session.user().id(), session.user().displayName(),
                        session.user().preferredLanguage().code()),
                session.firstLogin());
    }

    @Override
    public String toString() {
        return "SessionResponse[userId=" + user.id() + "]";
    }
}
