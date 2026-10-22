package com.soukly.social.caption;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Calls to listing-service for the labels of the referentials (SOC-03), bound from
 * {@code soukly.social.listing-service.*}.
 *
 * @param baseUrl        listing-service as social-publisher reaches it ({@code LISTING_SERVICE_URL}), never the gateway
 * @param labelsCacheTtl how long the labels are kept in memory; listing-service lets them be cached for one hour
 */
@ConfigurationProperties("soukly.social.listing-service")
public record ListingServiceProperties(URI baseUrl, @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout, @DefaultValue("1h") Duration labelsCacheTtl) {
}
