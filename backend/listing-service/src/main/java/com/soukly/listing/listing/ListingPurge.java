package com.soukly.listing.listing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the purge of the deleted listings once a day ({@code soukly.listing.deletion.purge-interval}). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ListingPurge {

    private static final Logger log = LoggerFactory.getLogger(ListingPurge.class);

    private final ListingDeletion deletion;

    ListingPurge(ListingDeletion deletion) {
        this.deletion = deletion;
    }

    @Scheduled(fixedDelayString = "${soukly.listing.deletion.purge-interval:P1D}",
            initialDelayString = "${soukly.listing.deletion.purge-initial-delay:PT10M}")
    void purge() {
        int erased = deletion.purge();
        if (erased > 0) {
            log.info("Listing purge: {} deleted listing(s) erased", erased);
        }
    }
}
