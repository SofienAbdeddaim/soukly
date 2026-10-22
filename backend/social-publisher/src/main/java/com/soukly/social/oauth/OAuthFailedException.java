package com.soukly.social.oauth;

import com.soukly.social.connection.Network;

/**
 * A connection to a social network failed. {@link #lang()} is the language of the page that started it, to go back to
 * it; {@code null} when the state was not valid.
 */
public class OAuthFailedException extends RuntimeException {

    private final OAuthFailure failure;
    private final Network network;
    private final String lang;

    public OAuthFailedException(OAuthFailure failure, Network network, String lang) {
        super(failure.slug(), null, false, false);
        this.failure = failure;
        this.network = network;
        this.lang = lang;
    }

    public OAuthFailure failure() {
        return failure;
    }

    public String lang() {
        return lang;
    }

    public String detail() {
        return failure.detail(network.displayName());
    }
}
