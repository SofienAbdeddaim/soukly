package com.soukly.listing.photo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** The objects of the photos in the bucket. Keys are built by {@link PhotoKeys} only, never from user input. */
@Component
class PhotoStorage {

    /** The 1600 and 400 versions never change under their key, which contains the photo id. */
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    /** DeleteObjects accepts at most 1,000 keys per call. */
    private static final int DELETE_BATCH = 1000;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    PhotoStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    /** A presigned {@code PUT}: the browser must send the same {@code Content-Type}, which the signature covers. */
    PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(ttl)
                .putObjectRequest(put -> put.bucket(bucket).key(key).contentType(contentType)));
        return new PresignedUpload(URI.create(presigned.url().toString()), presigned.httpRequest().method().name(),
                Map.of("Content-Type", contentType), presigned.expiration());
    }

    /** Size of the object, empty when it does not exist. */
    OptionalLong size(String key) {
        try {
            return OptionalLong.of(s3.headObject(head -> head.bucket(bucket).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            return OptionalLong.empty();
        } catch (S3Exception e) {
            // HEAD has no body: some storages answer 404 without the NoSuchKey code
            if (e.statusCode() == 404) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    /**
     * Copies the object to {@code target}, at most {@code maxBytes}.
     *
     * @return false when the object holds more than {@code maxBytes} (replaced since its size was read)
     */
    boolean download(String key, Path target, long maxBytes) throws IOException {
        try (ResponseInputStream<GetObjectResponse> in = s3.getObject(get -> get.bucket(bucket).key(key));
                OutputStream out = Files.newOutputStream(target)) {
            return copy(in, out, maxBytes);
        }
    }

    void uploadWebp(String key, Path file) {
        s3.putObject(put -> put.bucket(bucket).key(key).contentType("image/webp").cacheControl(IMMUTABLE),
                RequestBody.fromFile(file));
    }

    /** Deleting a key that does not exist is not an error. */
    void delete(Collection<String> keys) {
        List<ObjectIdentifier> ids = keys.stream().map(key -> ObjectIdentifier.builder().key(key).build()).toList();
        for (int from = 0; from < ids.size(); from += DELETE_BATCH) {
            List<ObjectIdentifier> batch = ids.subList(from, Math.min(from + DELETE_BATCH, ids.size()));
            s3.deleteObjects(delete -> delete.bucket(bucket).delete(Delete.builder().objects(batch).quiet(true).build()));
        }
    }

    /** Keys under {@code prefix} last modified before {@code before}. */
    List<String> keysOlderThan(String prefix, Instant before) {
        List<String> keys = new ArrayList<>();
        s3.listObjectsV2Paginator(list -> list.bucket(bucket).prefix(prefix)).contents().stream()
                .filter(object -> object.lastModified().isBefore(before))
                .map(S3Object::key)
                .forEach(keys::add);
        return keys;
    }

    private static boolean copy(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return false;
            }
            out.write(buffer, 0, read);
        }
        return true;
    }

    /**
     * @param headers to send with the {@code PUT}, exactly
     */
    record PresignedUpload(URI url, String method, Map<String, String> headers, Instant expiresAt) {
    }
}
