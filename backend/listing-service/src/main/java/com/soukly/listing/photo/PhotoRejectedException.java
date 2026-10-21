package com.soukly.listing.photo;

/** The uploaded file cannot become a photo ({@code 400}); the photo is {@code REJECTED}. */
public class PhotoRejectedException extends RuntimeException {

    private final PhotoRejection rejection;

    public PhotoRejectedException(PhotoRejection rejection) {
        super("Photo rejected: " + rejection.code(), null, false, false);
        this.rejection = rejection;
    }

    public PhotoRejection rejection() {
        return rejection;
    }
}
