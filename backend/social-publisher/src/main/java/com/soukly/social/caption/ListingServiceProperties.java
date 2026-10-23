package com.soukly.social.caption;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Calls to listing-service for the labels of the referentials (SOC-03) and the statuses of the listings (SOC-06),
 * bound from {@code soukly.social.listing-service.*}.
 *
 * @param baseUrl          listing-service as social-publisher reaches it ({@code LISTING_SERVICE_URL}), never the
 *                         gateway
 * @param internalApiToken shared token sent in {@code X-Internal-Token} to {@code /internal/**}
 *                         ({@code INTERNAL_API_TOKEN})
 * @param labelsCacheTtl   how long the labels are kept in memory; listing-service lets them be cached for one hour
 */
@ConfigurationProperties("soukly.social.listing-service")
public record ListingServiceProperties(URI baseUrl, String internalApiToken,
        @DefaultValue("2s") Duration connectTimeout, @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("1h") Duration labelsCacheTtl) {
}
