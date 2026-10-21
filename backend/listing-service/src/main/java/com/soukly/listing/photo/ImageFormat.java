package com.soukly.listing.photo;

import java.util.Arrays;
import java.util.Optional;

/**
 * Accepted formats, recognized by the first bytes of the file: the extension and the declared {@code Content-Type}
 * are never trusted.
 */
enum ImageFormat {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    /** Bytes {@link #detect(byte[])} needs. */
    static final int SIGNATURE_LENGTH = 12;

    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    private final String mediaType;
    private final String extension;

    ImageFormat(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    String mediaType() {
        return mediaType;
    }

    /** Extension of the temporary file given to libvips. */
    String extension() {
        return extension;
    }

    static Optional<ImageFormat> ofMediaType(String mediaType) {
        return Arrays.stream(values()).filter(format -> format.mediaType.equals(mediaType)).findFirst();
    }

    /** The format of a file from its first {@link #SIGNATURE_LENGTH} bytes, empty for anything else. */
    static Optional<ImageFormat> detect(byte[] head) {
        // JPEG: SOI marker followed by another marker
        if (head.length >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff) {
            return Optional.of(JPEG);
        }
        if (head.length >= PNG_SIGNATURE.length
                && Arrays.equals(head, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            return Optional.of(PNG);
        }
        // WebP: RIFF container ("RIFF", 4 bytes of size, "WEBP")
        if (head.length >= 12 && ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
