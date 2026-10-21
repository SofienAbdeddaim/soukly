package com.soukly.listing.photo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks an uploaded original, then writes its two WebP versions to the bucket. The checks run from the cheapest to
 * the most expensive, and nothing is decoded before the size, the signature and the dimensions are known to be
 * acceptable: an image bomb never reaches the conversion. Works on temporary files only.
 */
@Component
class PhotoProcessor {

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessor.class);

    static final int LARGE_SIDE = 1600;
    static final int THUMBNAIL_SIDE = 400;

    private final PhotoStorage storage;
    private final Vips vips;
    private final PhotoProperties properties;
    /** Each conversion holds a few tens of MB outside the JVM heap: their number is bounded. */
    private final Semaphore conversions;

    PhotoProcessor(PhotoStorage storage, Vips vips, PhotoProperties properties) {
        this.storage = storage;
        this.vips = vips;
        this.properties = properties;
        this.conversions = new Semaphore(properties.maxConcurrentConversions(), true);
    }

    /**
     * Writes {@code listings/{listingId}/{photoId}/1600.webp} and {@code 400.webp} from the original in
     * {@code incoming/}, which is left in place.
     *
     * @throws PhotoRejectedException the file cannot become a photo
     */
    Converted process(UUID listingId, UUID photoId) {
        // Wall-clock time, whatever the Clock bean says: this is a timeout, not a business date
        Instant deadline = Instant.now().plus(properties.conversionTimeout());
        long maxBytes = properties.maxSize().toBytes();
        String incoming = PhotoKeys.incoming(listingId, photoId);

        OptionalLong size = storage.size(incoming);
        if (size.isEmpty()) {
            throw new PhotoRejectedException(PhotoRejection.NOT_UPLOADED);
        }
        if (size.getAsLong() > maxBytes) {
            throw new PhotoRejectedException(PhotoRejection.TOO_LARGE);
        }
        Path directory = null;
        boolean acquired = false;
        try {
            directory = Files.createTempDirectory("soukly-photo-");
            Path original = directory.resolve("original");
            if (!storage.download(incoming, original, maxBytes)) {
                throw new PhotoRejectedException(PhotoRejection.TOO_LARGE);
            }
            ImageFormat format = format(original).orElseThrow(
                    () -> new PhotoRejectedException(PhotoRejection.UNSUPPORTED_FORMAT));
            // libvips reads the content, not the name; the extension only keeps the file recognizable.
            Path source = Files.move(original, directory.resolve("original." + format.extension()));

            acquired = conversions.tryAcquire(remainingMillis(deadline), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Photo {} not converted: too many conversions in progress", photoId);
                throw new PhotoRejectedException(PhotoRejection.PROCESSING_FAILED);
            }
            Vips.Dimensions dimensions = vips.dimensions(source, deadline);
            if (dimensions.pixels() > properties.maxPixels()) {
                throw new PhotoRejectedException(PhotoRejection.TOO_MANY_PIXELS);
            }
            if (dimensions.longSide() < properties.minLongSide()
                    || dimensions.shortSide() < properties.minShortSide()) {
                throw new PhotoRejectedException(PhotoRejection.TOO_SMALL);
            }
            Path large = directory.resolve("1600.webp");
            Path thumbnail = directory.resolve("400.webp");
            vips.thumbnail(source, large, LARGE_SIDE, deadline);
            // From the 1600 version: already rotated and stripped, and much faster to read than the original
            vips.thumbnail(large, thumbnail, THUMBNAIL_SIDE, deadline);
            Vips.Dimensions result = vips.dimensions(large, deadline);

            storage.uploadWebp(PhotoKeys.large(listingId, photoId), large);
            storage.uploadWebp(PhotoKeys.thumbnail(listingId, photoId), thumbnail);
            return new Converted(result.width(), result.height(), size.getAsLong());
        } catch (Vips.VipsException e) {
            log.warn("Photo {} not converted: {}", photoId, e.getMessage());
            throw new PhotoRejectedException(PhotoRejection.PROCESSING_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PhotoRejectedException(PhotoRejection.PROCESSING_FAILED);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (acquired) {
                conversions.release();
            }
            deleteRecursively(directory);
        }
    }

    private static Optional<ImageFormat> format(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return ImageFormat.detect(in.readNBytes(ImageFormat.SIGNATURE_LENGTH));
        }
    }

    private static long remainingMillis(Instant deadline) {
        return Math.max(0, deadline.toEpochMilli() - System.currentTimeMillis());
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

    /**
     * @param width of the 1600 version
     * @param bytes real size of the original
     */
    record Converted(int width, int height, long bytes) {
    }
}
