package com.soukly.listing.photo;

/** Unknown photo, or a photo of another listing ({@code 404}). */
public class PhotoNotFoundException extends RuntimeException {

    public PhotoNotFoundException() {
        super("Photo not found", null, false, false);
    }
}
