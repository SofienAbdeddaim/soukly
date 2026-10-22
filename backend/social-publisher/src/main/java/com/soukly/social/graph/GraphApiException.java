package com.soukly.social.graph;

/**
 * A Graph API call failed (Facebook or Instagram). The message names the operation and the HTTP status or the error
 * class only: never the URL (it may carry a token or the app secret) nor the response body.
 */
public class GraphApiException extends RuntimeException {

    private final int status;

    public GraphApiException(String message, int status) {
        super(message, null, false, false);
        this.status = status;
    }

    /** HTTP status of the Graph API, {@code 0} when it did not answer. */
    public int status() {
        return status;
    }
}
