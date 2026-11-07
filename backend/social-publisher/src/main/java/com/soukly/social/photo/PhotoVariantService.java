package com.soukly.social.photo;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.exception.SdkException;

/**
 * Versions of the photos for the networks that do not take the WebP versions of ANN-02 (SOC-05): Instagram publishes
 * JPEG only. The Instagram version ({@code ig-1080.jpg}) is written on demand next to the 1600 version it comes from,
 * {@value InstagramFrame#WIDTH} px wide, JPEG quality 85, without metadata, its proportions brought between
 * {@code 4:5} and {@code 1.91:1} with cream margins ({@link InstagramFrame}). An existing version is used as is: the
 * key contains the photo id, and the 1600 version never changes under it.
 */
@Service
public class PhotoVariantService {

    private static final Logger log = LoggerFactory.getLogger(PhotoVariantService.class);

    private final PhotoStorage storage;
    private final Vips vips;
    private final PhotoProperties properties;
    private final InstagramVariants variants;
    /** Each conversion holds a few tens of MB outside the JVM heap: their number is bounded. */
    private final Semaphore conversions;

    PhotoVariantService(PhotoStorage storage, Vips vips, PhotoProperties properties) {
        this.storage = storage;
        this.vips = vips;
        this.properties = properties;
        this.variants = new InstagramVariants(properties.publicBaseUrl());
        this.conversions = new Semaphore(properties.maxConcurrentConversions(), true);
    }

    /**
     * The public URL of the Instagram version of a photo, written first when it does not exist.
     *
     * @param photoUrl URL of the 1600 version, from {@code listing.published}
     * @throws PhotoVariantException the version could not be written
     */
    public URI instagram(URI photoUrl) {
        String source = variants.sourceKey(photoUrl).orElseThrow(() -> new PhotoVariantException(
                PhotoVariantException.UNAVAILABLE, false, "Not a photo URL of the bucket"));
        String target = InstagramVariants.variantKey(source);
        try {
            if (!storage.exists(target)) {
                write(source, target);
            }
        } catch (SdkException e) {
            throw new PhotoVariantException(PhotoVariantException.STORAGE_UNAVAILABLE, true,
                    "Object storage failed: " + e.getClass().getSimpleName());
        }
        return variants.url(target);
    }

    private void write(String source, String target) {
        // Wall-clock time, whatever the Clock bean says: this is a timeout, not a business date
        Instant deadline = Instant.now().plus(properties.conversionTimeout());
        Path directory = null;
        boolean acquired = false;
        try {
            directory = Files.createTempDirectory("soukly-ig-");
            Path original = directory.resolve("1600.webp");
            if (!storage.download(source, original, properties.maxSourceSize().toBytes())) {
                throw new PhotoVariantException(PhotoVariantException.UNAVAILABLE, false,
                        "1600 version missing or too large");
            }
            acquired = conversions.tryAcquire(Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis()),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new PhotoVariantException(PhotoVariantException.CONVERSION_FAILED, true,
                        "Too many conversions in progress");
            }
            Vips.Dimensions dimensions = vips.dimensions(original, deadline);
            InstagramFrame frame = InstagramFrame.of(dimensions.width(), dimensions.height());
            Path jpeg = directory.resolve("ig-1080.jpg");
            vips.instagramVersion(original, jpeg, frame, deadline);
            storage.uploadJpeg(target, jpeg);
            log.info("Instagram version written: {}x{}, {}", frame.width(), frame.height(),
                    frame.hasMargins() ? "with margins" : "without margin");
        } catch (Vips.VipsException e) {
            log.warn("Instagram version not written: {}", e.getMessage());
            throw new PhotoVariantException(PhotoVariantException.CONVERSION_FAILED, false, e.getMessage());
        } catch (IOException e) {
            throw new PhotoVariantException(PhotoVariantException.CONVERSION_FAILED, true,
                    "Temporary file: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PhotoVariantException(PhotoVariantException.CONVERSION_FAILED, true, "Interrupted");
        } finally {
            if (acquired) {
                conversions.release();
            }
            deleteRecursively(directory);
        }
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
        } catch (IOException e) {
            log.warn("Temporary directory {} not deleted", directory);
        }
    }
}
