package com.soukly.social.facebook;

/**
 * A connection to Facebook failed. {@link #lang()} is the language of the page that started it, to go back to it;
 * {@code null} when the state was not valid.
 */
public class OAuthFailedException extends RuntimeException {

    private final OAuthFailure failure;
    private final String lang;

    OAuthFailedException(OAuthFailure failure, String lang) {
        super(failure.slug(), null, false, false);
        this.failure = failure;
        this.lang = lang;
    }

    public OAuthFailure failure() {
        return failure;
    }

    public String lang() {
        return lang;
    }
}
