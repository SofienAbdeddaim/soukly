package com.soukly.social.photo;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Instagram versions of the photos (SOC-05), bound from {@code soukly.social.photos.*}.
 *
 * @param publicBaseUrl            public URL of the bucket root ({@code PHOTO_PUBLIC_BASE_URL}, the same as
 *                                 listing-service): the photo URLs of {@code listing.published} start with it, and
 *                                 the object key follows
 * @param jpegQuality              JPEG quality factor (1 to 100)
 * @param maxSourceSize            of the 1600 version read from the bucket
 * @param conversionTimeout        of the libvips calls of one photo
 * @param maxConcurrentConversions libvips conversions running at the same time; the others wait
 * @param vipsBinDirectory         directory of {@code vips} and {@code vipsheader}; empty = the PATH
 */
@ConfigurationProperties("soukly.social.photos")
public record PhotoProperties(URI publicBaseUrl, @DefaultValue("85") int jpegQuality,
        @DefaultValue("10MB") DataSize maxSourceSize, @DefaultValue("30s") Duration conversionTimeout,
        @DefaultValue("2") int maxConcurrentConversions, @DefaultValue("") String vipsBinDirectory) {
}
