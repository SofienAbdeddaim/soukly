package com.soukly.listing.seller;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Calls to the internal endpoints of user-service (CATA-03), bound from {@code soukly.listing.user-service.*}.
 *
 * @param baseUrl          user-service as listing-service reaches it ({@code USER_SERVICE_URL}), never the gateway
 * @param internalApiToken shared token sent in {@code X-Internal-Token} ({@code INTERNAL_API_TOKEN})
 * @param connectTimeout   then the seller is unavailable ({@code 503})
 * @param readTimeout      then the seller is unavailable ({@code 503})
 * @param profileCacheTtl  how long a public profile is kept in memory; the phone number is never kept
 * @param profileCacheSize most profiles kept in memory
 */
@ConfigurationProperties("soukly.listing.user-service")
public record UserServiceProperties(URI baseUrl, String internalApiToken,
        @DefaultValue("2s") Duration connectTimeout, @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("5m") Duration profileCacheTtl, @DefaultValue("10000") long profileCacheSize) {
}
