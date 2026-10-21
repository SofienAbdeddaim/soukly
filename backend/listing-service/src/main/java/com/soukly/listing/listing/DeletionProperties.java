package com.soukly.listing.listing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Deletion of listings by their seller (ANN-05), bound from {@code soukly.listing.deletion.*}.
 *
 * @param purgeAfter          a deleted listing is erased for good this long after its deletion
 *                            ({@code LISTING_PURGE_AFTER}), the time to handle a dispute or a fraud
 * @param purgeInterval       period of the purge task
 * @param purgeInitialDelay   first run of the purge after the start
 * @param batchSize           listings erased per transaction by the purge
 */
@ConfigurationProperties("soukly.listing.deletion")
public record DeletionProperties(@DefaultValue("30d") Duration purgeAfter, @DefaultValue("1d") Duration purgeInterval,
        @DefaultValue("10m") Duration purgeInitialDelay, @DefaultValue("20") int batchSize) {
}
