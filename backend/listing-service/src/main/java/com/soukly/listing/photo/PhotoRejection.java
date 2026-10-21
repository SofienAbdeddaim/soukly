package com.soukly.listing.photo;

import java.util.Arrays;

/** Why a photo was rejected at its completion; {@code code} is the stable key answered to the front end. */
public enum PhotoRejection {

    /** No object was uploaded, or the upload was not completed in time. */
    NOT_UPLOADED("photo.not-uploaded"),
    TOO_LARGE("photo.too-large"),
    /** Not a JPEG, PNG or WebP file, whatever its name and declared type. */
    UNSUPPORTED_FORMAT("photo.unsupported-format"),
    TOO_SMALL("photo.too-small"),
    TOO_MANY_PIXELS("photo.too-many-pixels"),
    /** libvips could not read or convert the file, or took too long. */
    PROCESSING_FAILED("photo.processing-failed");

    private final String code;

    PhotoRejection(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    static PhotoRejection ofCode(String code) {
        return Arrays.stream(values()).filter(rejection -> rejection.code.equals(code)).findFirst()
                .orElse(PROCESSING_FAILED);
    }
}
