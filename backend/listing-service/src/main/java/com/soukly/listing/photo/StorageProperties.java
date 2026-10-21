package com.soukly.listing.photo;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 object storage of the photos (Garage locally and in dev, Oracle Object Storage in prod), bound from
 * {@code soukly.listing.storage.*}. Path-style access is always used.
 *
 * @param endpoint       S3 API used by the service ({@code S3_ENDPOINT})
 * @param publicEndpoint S3 API reached by the browsers, host of the presigned URLs ({@code S3_PUBLIC_ENDPOINT});
 *                       the same as {@code endpoint} when both see the storage at the same address
 * @param secretKey      never logged
 */
@ConfigurationProperties("soukly.listing.storage")
public record StorageProperties(URI endpoint, URI publicEndpoint, String region, String bucket, String accessKey,
        String secretKey) {

    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", publicEndpoint=" + publicEndpoint + ", region=" + region
                + ", bucket=" + bucket + "]";
    }
}
