package com.soukly.social.graph;

/**
 * A Graph API call failed (Facebook or Instagram). The message names the operation and the HTTP status or the error
 * class only: never the URL (it may carry a token or the app secret) nor the response body. The error object of Meta,
 * when the answer had one, is kept in {@link #error()} with its message already cleaned of tokens and personal data.
 */
public class GraphApiException extends RuntimeException {

    private final int status;
    private final GraphError error;

    public GraphApiException(String message, int status) {
        this(message, status, null);
    }

    public GraphApiException(String message, int status, GraphError error) {
        super(message, null, false, false);
        this.status = status;
        this.error = error;
    }

    /** HTTP status of the Graph API, {@code 0} when it did not answer. */
    public int status() {
        return status;
    }

    /** The {@code error} object of the answer, or {@code null} when there was none (no answer, not JSON). */
    public GraphError error() {
        return error;
    }
}
