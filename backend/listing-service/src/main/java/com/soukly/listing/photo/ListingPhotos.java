package com.soukly.listing.photo;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Reading of the photos of a listing, for its answer ({@code ListingViews}). */
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
}
