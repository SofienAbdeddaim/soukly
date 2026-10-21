package com.soukly.listing.photo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageFormatTest {

    @Test
    void imagesAreRecognizedByTheirFirstBytes() {
        assertThat(ImageFormat.detect(TestImages.jpeg(400, 300))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(TestImages.png(400, 300))).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)))
                .contains(ImageFormat.WEBP);
    }

    @Test
    void anythingElseIsNotAnImage() {
        assertThat(ImageFormat.detect(TestImages.pdf())).isEmpty();
        // A RIFF container that is not WebP (a WAV file)
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect(new byte[] { (byte) 0xff, (byte) 0xd8 })).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void onlyTheThreeAcceptedTypesCanBeDeclared() {
        assertThat(ImageFormat.ofMediaType("image/jpeg")).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.ofMediaType("image/gif")).isEmpty();
        assertThat(ImageFormat.ofMediaType("IMAGE/JPEG")).isEmpty();
    }
}
