package com.soukly.social.photo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * libvips through its command-line tools ({@code vips} and {@code vipsheader}, package {@code libvips-tools}), like
 * listing-service (ANN-02): run with {@link ProcessBuilder}, every argument a separate string, never a shell command,
 * every path a temporary file named by the service. {@code VIPS_BLOCK_UNTRUSTED} blocks the loaders libvips does not
 * consider safe; the sources are the 1600 versions listing-service wrote anyway.
 */
@Component
class Vips {

    /** Output of a tool kept for the error message, enough for the libvips error line. */
    private static final int MAX_OUTPUT = 4096;

    /** The cream of the margins, token {@code --color-cream} {@code #FFF8EC} of SETUP-04, as libvips reads it. */
    static final String CREAM = "255 248 236";

    /** Fast PNG for the steps: they are read once, by the next step. */
    private static final String INTERMEDIATE = "[compression=1]";

    private final String vips;
    private final String header;
    private final int quality;

    Vips(PhotoProperties properties) {
        String directory = properties.vipsBinDirectory();
        this.vips = directory.isEmpty() ? "vips" : Path.of(directory, "vips").toString();
        this.header = directory.isEmpty() ? "vipsheader" : Path.of(directory, "vipsheader").toString();
        this.quality = properties.jpegQuality();
    }

    /** Width and height read from the header of the file, without decoding the pixels. */
    Dimensions dimensions(Path image, Instant deadline) throws VipsException {
        return new Dimensions(field(image, "width", deadline), field(image, "height", deadline));
    }

    /**
     * Writes {@code target} as a JPEG of {@code frame.width() × frame.height()}: the photo scaled to
     * {@code frame.imageWidth() × frame.imageHeight()} (enlarged when it is smaller), in sRGB, its transparency on
     * cream, framed by cream margins. No metadata ({@code keep=none}).
     */
    void instagramVersion(Path source, Path target, InstagramFrame frame, Instant deadline) throws VipsException {
        Path directory = target.toAbsolutePath().getParent();
        // Steps in lossless PNG: the native .v format of libvips is among the loaders VIPS_BLOCK_UNTRUSTED blocks
        Path scaled = directory.resolve("scaled.png");
        Path srgb = directory.resolve("srgb.png");
        run(deadline, vips, "thumbnail", source.toString(), scaled + INTERMEDIATE,
                String.valueOf(frame.imageWidth()), "--height", String.valueOf(frame.imageHeight()), "--size", "force");
        run(deadline, vips, "colourspace", scaled.toString(), srgb + INTERMEDIATE, "srgb");
        Path opaque = srgb;
        if (field(srgb, "bands", deadline) > 3) {
            opaque = directory.resolve("opaque.png");
            run(deadline, vips, "flatten", srgb.toString(), opaque + INTERMEDIATE, "--background", CREAM);
        }
        run(deadline, vips, "embed", opaque.toString(), target.toAbsolutePath() + "[Q=" + quality + ",keep=none]",
                String.valueOf(frame.left()), String.valueOf(frame.top()), String.valueOf(frame.width()),
                String.valueOf(frame.height()), "--extend", "background", "--background", CREAM);
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
                throw new VipsException(Path.of(command[0]).getFileName() + " " + command[1] + " failed: "
                        + firstLine(text));
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
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException e) {
                    // a temporary file, removed by the system later
                }
            }
        }
    }

    private static String read(Path output) throws IOException {
        byte[] bytes = Files.readAllBytes(output);
        return new String(bytes, 0, Math.min(bytes.length, MAX_OUTPUT), StandardCharsets.UTF_8);
    }

    private static String firstLine(String text) {
        return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("no output");
    }

    record Dimensions(int width, int height) {
    }

    /** libvips could not read or convert the file, or took too long. */
    static class VipsException extends Exception {

        VipsException(String message) {
            super(message, null, false, false);
        }
    }
}
