package com.soukly.listing.listing;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publication and expiry of listings (ANN-03), bound from {@code soukly.listing.publication.*}.
 *
 * @param ttl                visibility of a publication or a renewal ({@code LISTING_TTL})
 * @param reminderBefore     the reminder is written, and a published listing can be renewed, this long before its
 *                           expiry ({@code LISTING_REMINDER_BEFORE})
 * @param publicBaseUrl      origin of the site ({@code PUBLIC_BASE_URL}), for the page URLs of the events
 * @param checkInterval      period of the expiry and reminder task
 * @param checkInitialDelay  first run of the task after the start
 * @param batchSize          listings changed per transaction by the task
 */
@ConfigurationProperties("soukly.listing.publication")
public record PublicationProperties(@DefaultValue("60d") Duration ttl, @DefaultValue("3d") Duration reminderBefore,
        URI publicBaseUrl, @DefaultValue("1h") Duration checkInterval,
        @DefaultValue("1m") Duration checkInitialDelay, @DefaultValue("100") int batchSize) {
}
