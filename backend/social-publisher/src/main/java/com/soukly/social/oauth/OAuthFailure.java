package com.soukly.social.oauth;

import org.springframework.http.HttpStatus;

/** Why a connection to a social network failed, with its problem type (SOC-01, SOC-02). */
public enum OAuthFailure {

    /** The state is unknown, expired, used already or was started by another member. */
    STATE_INVALID("oauth-state-invalid", HttpStatus.BAD_REQUEST, "OAuth state invalid",
            "This connection link is unknown, expired or used already. Start again."),
    /** The member cancelled on the network ({@code error=access_denied}). */
    CANCELLED("oauth-cancelled", HttpStatus.BAD_REQUEST, "Connection cancelled",
            "The connection was cancelled on %s."),
    /** A permission needed to post was not granted. */
    PERMISSION_MISSING("oauth-permission-missing", HttpStatus.BAD_REQUEST, "Permission missing",
            "Soukly needs the permission to post on your %s account."),
    /** The person manages no Page they can post on. */
    NO_PAGE("facebook-no-page", HttpStatus.BAD_REQUEST, "No Facebook Page",
            "You manage no Facebook Page that Soukly can post on."),
    /** Facebook refused the code or did not answer. */
    FACEBOOK_UNAVAILABLE("facebook-unavailable", HttpStatus.BAD_GATEWAY, "Facebook unavailable",
            "Facebook did not complete the connection. Try again."),
    /** The Instagram account is a personal one: only Business and Creator accounts can be posted to (SOC-02). */
    INSTAGRAM_NOT_PROFESSIONAL("instagram-not-professional", HttpStatus.BAD_REQUEST,
            "Instagram account not professional",
            "Only an Instagram professional account (Business or Creator) can be connected."),
    /** Instagram refused the code or did not answer. */
    INSTAGRAM_UNAVAILABLE("instagram-unavailable", HttpStatus.BAD_GATEWAY, "Instagram unavailable",
            "Instagram did not complete the connection. Try again.");

    private final String slug;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    OAuthFailure(String slug, HttpStatus status, String title, String detail) {
        this.slug = slug;
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public String slug() {
        return slug;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** The detail of the problem; {@code %s} is the name of the network, for the failures they share. */
    public String detail(String networkName) {
        return detail.formatted(networkName);
    }
}
