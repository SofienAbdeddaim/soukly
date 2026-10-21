package com.soukly.listing.photo;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Reading of the photos of a listing, for its answer ({@code ListingViews}) and the search cards (CATA-01). */
@Component
public class ListingPhotos {

    private final PhotoRepository repository;
    private final PhotoUrls urls;

    ListingPhotos(PhotoRepository repository, PhotoUrls urls) {
        this.repository = repository;
        this.urls = urls;
    }

    /** READY photos, cover first. */
    public List<PhotoView> ready(UUID listingId) {
        return repository.ready(listingId).stream().map(urls::view).toList();
    }

    /** Public URL of the 400 px version of a READY photo. */
    public URI thumbnailUrl(UUID listingId, UUID photoId) {
        return urls.thumbnail(listingId, photoId);
    }
}
