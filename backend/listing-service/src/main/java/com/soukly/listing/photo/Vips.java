package com.soukly.listing.photo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * libvips through its command-line tools ({@code vipsheader} and {@code vipsthumbnail}, package
 * {@code libvips-tools}), run with {@link ProcessBuilder}. Every argument is a separate string, never a shell
 * command, and every path is a temporary file named by the service: nothing typed by a user reaches a command.
 *
 * <p>{@code VIPS_BLOCK_UNTRUSTED} blocks the loaders that libvips does not consider safe for untrusted input, on top
 * of the signature check of {@link ImageFormat}.
 */
@Component
class Vips {

    /** Output of a tool kept for the error message, enough for the libvips error line. */
    private static final int MAX_OUTPUT = 4096;

    private final String header;
    private final String thumbnail;
    private final int quality;

    Vips(PhotoProperties properties) {
        String directory = properties.vipsBinDirectory();
        this.header = directory.isEmpty() ? "vipsheader" : Path.of(directory, "vipsheader").toString();
        this.thumbnail = directory.isEmpty() ? "vipsthumbnail" : Path.of(directory, "vipsthumbnail").toString();
        this.quality = properties.webpQuality();
    }

    /** Width and height read from the header of the file, without decoding the pixels. */
    Dimensions dimensions(Path image, Instant deadline) throws VipsException {
        return new Dimensions(field(image, "width", deadline), field(image, "height", deadline));
    }

    /**
     * Writes {@code target} as WebP with its longest side at most {@code maxSide}, never enlarged. The image is
     * rotated according to its EXIF orientation and every metadata is removed ({@code keep=none}: EXIF, XMP, IPTC,
     * ICC profile), after conversion to sRGB.
     */
    void thumbnail(Path source, Path target, int maxSide, Instant deadline) throws VipsException {
        run(deadline, thumbnail, source.toString(), "--size", maxSide + "x" + maxSide + ">",
                "-o", target.toAbsolutePath() + "[Q=" + quality + ",keep=none]");
    }

    private int field(Path image, String name, Instant deadline) throws VipsException {
        String value = run(deadline, header, "-f", name, image.toString()).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new VipsException("Unexpected " + name + " from vipsheader");
        }
    }

    /** Standard output of the command; any other exit code than 0, or the deadline, is a {@link VipsException}. */
    private static String run(Instant deadline, String... command) throws VipsException {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("soukly-vips-", ".out");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            builder.environment().put("VIPS_BLOCK_UNTRUSTED", "1");
            process = builder.start();
            long remaining = Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
            if (!process.waitFor(remaining, TimeUnit.MILLISECONDS)) {
                throw new VipsException(Path.of(command[0]).getFileName() + " timed out");
            }
            String text = read(output);
            if (process.exitValue() != 0) {
                throw new VipsException(Path.of(command[0]).getFileName() + " failed: " + firstLine(text));
            }
            return text;
        } catch (IOException e) {
            throw new VipsException("Cannot run " + command[0] + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VipsException("Interrupted");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(output);
        }
    }

    private static String read(Path output) throws IOException {
        byte[] bytes = Files.readAllBytes(output);
        return new String(bytes, 0, Math.min(bytes.length, MAX_OUTPUT), StandardCharsets.UTF_8);
    }

    private static String firstLine(String text) {
        return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("no output");
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // a temporary file, removed by the system later
            }
        }
    }

    record Dimensions(int width, int height) {

        long pixels() {
            return (long) width * height;
        }

        int longSide() {
            return Math.max(width, height);
        }

        int shortSide() {
            return Math.min(width, height);
        }
    }

    /** libvips could not read or convert the file, or took too long. The message never contains user input. */
    static class VipsException extends Exception {

        VipsException(String message) {
            super(message, null, false, false);
        }
    }
}
