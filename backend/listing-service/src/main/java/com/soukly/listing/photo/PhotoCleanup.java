package com.soukly.listing.photo;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the cleanup of the abandoned uploads every hour ({@code soukly.listing.photos.cleanup-interval}), the first
 * time one interval after the start. Every step is idempotent, so two instances running it at the same time do no
 * harm.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class PhotoCleanup {

    private final PhotoService photos;

    PhotoCleanup(PhotoService photos) {
        this.photos = photos;
    }

    @Scheduled(fixedDelayString = "${soukly.listing.photos.cleanup-interval:PT1H}",
            initialDelayString = "${soukly.listing.photos.cleanup-interval:PT1H}")
    void cleanUp() {
        photos.cleanUp();
    }
}
