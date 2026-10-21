package com.soukly.listing.photo;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Photos of a listing, bound from {@code soukly.listing.photos.*}.
 *
 * @param publicBaseUrl            public URL of the bucket root ({@code PHOTO_PUBLIC_BASE_URL}): the object key is
 *                                 appended to it
 * @param maxPerListing            READY and PENDING_UPLOAD photos of a listing; one more is answered with {@code 409}
 * @param maxSize                  of the uploaded original
 * @param maxPixels                width × height of the original, checked from its header before any decoding
 * @param minLongSide              minimum of the longest side of the original, in pixels
 * @param minShortSide             minimum of the shortest side of the original, in pixels
 * @param uploadUrlTtl             validity of a presigned upload URL
 * @param pendingTtl               an upload not completed after this is rejected by the cleanup
 * @param webpQuality              WebP quality factor (1 to 100) of both versions
 * @param conversionTimeout        of the checks and conversion of one photo, libvips calls included
 * @param maxConcurrentConversions libvips conversions running at the same time; the others wait
 * @param vipsBinDirectory         directory of {@code vipsheader} and {@code vipsthumbnail}; empty = the PATH
 */
@ConfigurationProperties("soukly.listing.photos")
public record PhotoProperties(URI publicBaseUrl, @DefaultValue("10") int maxPerListing,
        @DefaultValue("5MB") DataSize maxSize, @DefaultValue("50000000") long maxPixels,
        @DefaultValue("400") int minLongSide, @DefaultValue("300") int minShortSide,
        @DefaultValue("10m") Duration uploadUrlTtl, @DefaultValue("1h") Duration pendingTtl,
        @DefaultValue("80") int webpQuality, @DefaultValue("30s") Duration conversionTimeout,
        @DefaultValue("2") int maxConcurrentConversions, @DefaultValue("") String vipsBinDirectory) {
}
