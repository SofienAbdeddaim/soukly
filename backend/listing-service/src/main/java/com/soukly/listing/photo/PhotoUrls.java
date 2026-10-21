package com.soukly.listing.photo;

import java.net.URI;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Public URLs of the photos: {@code PHOTO_PUBLIC_BASE_URL} followed by the object key. */
@Component
class PhotoUrls {

    private final String base;

    PhotoUrls(PhotoProperties properties) {
        String url = properties.publicBaseUrl().toString();
        this.base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    PhotoView view(ListingPhoto photo) {
        return new PhotoView(photo.id(), photo.status(), url(PhotoKeys.large(photo.listingId(), photo.id())),
                url(PhotoKeys.thumbnail(photo.listingId(), photo.id())), photo.width(), photo.height(),
                photo.position());
    }

    /** The 400 px version of a READY photo. */
    URI thumbnail(UUID listingId, UUID photoId) {
        return url(PhotoKeys.thumbnail(listingId, photoId));
    }

    private URI url(String key) {
        return URI.create(base + "/" + key);
    }
}
