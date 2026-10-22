package com.soukly.social.facebook;

/** {@code META_APP_ID} or {@code META_APP_SECRET} is not set in this environment. */
public class FacebookNotConfiguredException extends RuntimeException {

    FacebookNotConfiguredException() {
        super(null, null, false, false);
    }
}
