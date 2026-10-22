package com.soukly.social.facebook;

import org.springframework.http.HttpStatus;

/** Why a connection to Facebook failed, with its problem type (SOC-01). */
public enum OAuthFailure {

    /** The state is unknown, expired, used already or was started by another member. */
    STATE_INVALID("oauth-state-invalid", HttpStatus.BAD_REQUEST, "OAuth state invalid",
            "This connection link is unknown, expired or used already. Start again."),
    /** The member cancelled on Facebook ({@code error=access_denied}). */
    CANCELLED("oauth-cancelled", HttpStatus.BAD_REQUEST, "Connection cancelled",
            "The connection was cancelled on Facebook."),
    /** A permission needed to list the Pages or post on them was not granted. */
    PERMISSION_MISSING("oauth-permission-missing", HttpStatus.BAD_REQUEST, "Permission missing",
            "Soukly needs the permission to post on your Page."),
    /** The person manages no Page they can post on. */
    NO_PAGE("facebook-no-page", HttpStatus.BAD_REQUEST, "No Facebook Page",
            "You manage no Facebook Page that Soukly can post on."),
    /** Facebook refused the code or did not answer. */
    FACEBOOK_UNAVAILABLE("facebook-unavailable", HttpStatus.BAD_GATEWAY, "Facebook unavailable",
            "Facebook did not complete the connection. Try again.");

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

    public String detail() {
        return detail;
    }
}
