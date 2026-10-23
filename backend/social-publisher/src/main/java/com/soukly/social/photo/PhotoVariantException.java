package com.soukly.social.photo;

/**
 * The Instagram version of a photo could not be written (SOC-05). The message names the cause, never a URL or a key.
 */
public class PhotoVariantException extends RuntimeException {

    /** {@code photo-unavailable}: the 1600 version is not in the bucket, or not a photo URL of the bucket. */
    public static final String UNAVAILABLE = "photo-unavailable";
    /** {@code photo-conversion-failed}: libvips could not convert it. */
    public static final String CONVERSION_FAILED = "photo-conversion-failed";
    /** {@code storage-unavailable}: the bucket did not answer; may succeed later. */
    public static final String STORAGE_UNAVAILABLE = "storage-unavailable";

    private final String code;
    private final boolean temporary;

    PhotoVariantException(String code, boolean temporary, String message) {
        super(message, null, false, false);
        this.code = code;
        this.temporary = temporary;
    }

    /** The {@code last_error_code} of the post. */
    public String code() {
        return code;
    }

    /** A new attempt later may succeed. */
    public boolean temporary() {
        return temporary;
    }
}
