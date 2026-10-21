package com.soukly.listing.photo;

/** Life cycle of a photo: {@code PENDING_UPLOAD} until its completion, then {@code READY} or {@code REJECTED}. */
public enum PhotoStatus {
    PENDING_UPLOAD, READY, REJECTED
}
