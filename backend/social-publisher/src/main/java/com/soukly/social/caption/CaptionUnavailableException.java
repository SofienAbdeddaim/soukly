package com.soukly.social.caption;

/** The referentials of listing-service did not answer ({@code 503 caption-unavailable}). */
public class CaptionUnavailableException extends RuntimeException {

    public CaptionUnavailableException(Throwable cause) {
        super("Referentials unavailable", cause, false, false);
    }
}
