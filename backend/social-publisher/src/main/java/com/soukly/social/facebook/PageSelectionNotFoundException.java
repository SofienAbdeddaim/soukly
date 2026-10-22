package com.soukly.social.facebook;

/** No choice among several Pages is in progress, it expired, or it has no Page with this id. */
public class PageSelectionNotFoundException extends RuntimeException {

    PageSelectionNotFoundException() {
        super(null, null, false, false);
    }
}
