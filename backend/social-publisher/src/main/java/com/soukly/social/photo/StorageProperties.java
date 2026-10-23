package com.soukly.social.photo;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 object storage of the photos (SOC-05), the bucket of listing-service: Garage locally and in dev, Oracle Object
 * Storage in prod. Bound from {@code soukly.social.storage.*}; path-style access is always used. social-publisher reads
 * the 1600 versions and writes the Instagram ones next to them.
 *
 * @param endpoint  S3 API ({@code S3_ENDPOINT})
 * @param secretKey never logged
 */
@ConfigurationProperties("soukly.social.storage")
public record StorageProperties(URI endpoint, String region, String bucket, String accessKey, String secretKey) {

    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + "]";
    }
}
