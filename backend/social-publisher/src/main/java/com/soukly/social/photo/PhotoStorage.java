package com.soukly.social.photo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** The objects of the photos in the bucket of listing-service. Keys come from {@link InstagramVariants} only. */
@Component
class PhotoStorage {

    /** Like the WebP versions: the key contains the photo id, the object never changes under it. */
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    private final S3Client s3;
    private final String bucket;

    PhotoStorage(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    boolean exists(String key) {
        try {
            s3.headObject(head -> head.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // HEAD has no body: some storages answer 404 without the NoSuchKey code
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Copies the object to {@code target}, at most {@code maxBytes}.
     *
     * @return {@code false} when the object does not exist or holds more than {@code maxBytes}
     */
    boolean download(String key, Path target, long maxBytes) throws IOException {
        try (ResponseInputStream<GetObjectResponse> in = s3.getObject(get -> get.bucket(bucket).key(key));
                OutputStream out = Files.newOutputStream(target)) {
            return copy(in, out, maxBytes);
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    void uploadJpeg(String key, Path file) {
        s3.putObject(put -> put.bucket(bucket).key(key).contentType("image/jpeg").cacheControl(IMMUTABLE),
                RequestBody.fromFile(file));
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
}
