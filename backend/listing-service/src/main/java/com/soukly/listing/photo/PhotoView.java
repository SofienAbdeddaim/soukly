package com.soukly.listing.photo;

import java.net.URI;
import java.util.UUID;

/**
 * A READY photo as answered: in {@code photos} of a listing and by its completion.
 *
 * @param url          WebP version, longest side at most 1600 px
 * @param thumbnailUrl WebP version, longest side at most 400 px
 * @param width        of the 1600 version
 * @param position     0 is the cover
 */
public record PhotoView(UUID id, PhotoStatus status, URI url, URI thumbnailUrl, int width, int height,
        int position) {
}
