package com.soukly.social.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGarage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/** Instagram versions of the photos (SOC-05) written by libvips into the Garage of the tests. */
class PhotoVariantServiceTests extends IntegrationTest {

    private static final Color PHOTO = new Color(30, 90, 160);

    @Autowired
    private PhotoVariantService variants;

    @Autowired
    private S3Client s3;

    @Test
    void aPanoramaGetsCreamMarginsAboveAndBelowAndStaysWhole() throws Exception {
        String prefix = prefix();
        put(prefix + "/1600.webp", image(1600, 533), "image/webp");

        URI url = variants.instagram(photoUrl(prefix));

        assertThat(url).hasToString(PHOTO_BASE_URL + "/" + prefix + "/ig-1080.jpg");
        HeadObjectResponse head = s3.headObject(h -> h.bucket(TestGarage.BUCKET).key(prefix + "/ig-1080.jpg"));
        assertThat(head.contentType()).isEqualTo("image/jpeg");
        assertThat(head.cacheControl()).contains("immutable");
        BufferedImage version = read(prefix + "/ig-1080.jpg");
        assertThat(version.getWidth()).isEqualTo(1080);
        assertThat(version.getHeight()).isEqualTo(566);
        assertThat((double) version.getWidth() / version.getHeight()).isBetween(0.8, 1.91);
        // Cream margins above and below, the photo in between: its four corners are there, nothing is cut
        assertCream(version, 540, 5);
        assertCream(version, 540, 560);
        assertCream(version, 5, 100);
        assertPhoto(version, 540, 283);
        assertPhoto(version, 3, 110);
        assertPhoto(version, 1076, 110);
        assertPhoto(version, 3, 455);
        assertPhoto(version, 1076, 455);
    }

    @Test
    void aTallPortraitGetsMarginsOnBothSides() throws Exception {
        String prefix = prefix();
        put(prefix + "/1600.webp", image(900, 1600), "image/webp");

        BufferedImage version = read(key(variants.instagram(photoUrl(prefix))));

        assertThat(version.getWidth()).isEqualTo(1080);
        assertThat(version.getHeight()).isEqualTo(1350);
        assertCream(version, 5, 675);
        assertCream(version, 1074, 675);
        assertPhoto(version, 540, 675);
        assertPhoto(version, 540, 3);
        assertPhoto(version, 540, 1346);
    }

    @Test
    void aPhotoWithinTheRangeHasNoMarginAndNoMetadata() throws Exception {
        String prefix = prefix();
        put(prefix + "/1600.webp", image(1600, 1200), "image/webp");

        String key = key(variants.instagram(photoUrl(prefix)));

        byte[] jpeg = s3.getObjectAsBytes(get -> get.bucket(TestGarage.BUCKET).key(key)).asByteArray();
        BufferedImage version = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(version.getWidth()).isEqualTo(1080);
        assertThat(version.getHeight()).isEqualTo(810);
        assertPhoto(version, 2, 2);
        assertPhoto(version, 1077, 807);
        // JPEG (FF D8), without EXIF (APP1 "Exif") nor XMP
        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
        assertThat(new String(jpeg, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif")
                .doesNotContain("http://ns.adobe.com/xap");
    }

    @Test
    void transparencyBecomesCream() throws Exception {
        String prefix = prefix();
        BufferedImage transparent = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", png);
        put(prefix + "/1600.webp", png.toByteArray(), "image/webp");

        BufferedImage version = read(key(variants.instagram(photoUrl(prefix))));

        assertThat(version.getWidth()).isEqualTo(1080);
        assertCream(version, 540, 540);
    }

    @Test
    void anExistingVersionIsNotWrittenAgain() throws Exception {
        String prefix = prefix();
        put(prefix + "/1600.webp", image(1600, 1200), "image/webp");
        byte[] existing = "existing version".getBytes();
        put(prefix + "/ig-1080.jpg", existing, "image/jpeg");

        URI url = variants.instagram(photoUrl(prefix));

        assertThat(url).hasToString(PHOTO_BASE_URL + "/" + prefix + "/ig-1080.jpg");
        assertThat(s3.getObjectAsBytes(get -> get.bucket(TestGarage.BUCKET).key(prefix + "/ig-1080.jpg"))
                .asByteArray()).isEqualTo(existing);
    }

    @Test
    void aMissingPhotoOrAnotherUrlIsUnavailable() {
        assertThatThrownBy(() -> variants.instagram(photoUrl(prefix())))
                .isInstanceOfSatisfying(PhotoVariantException.class, e -> {
                    assertThat(e.code()).isEqualTo(PhotoVariantException.UNAVAILABLE);
                    assertThat(e.temporary()).isFalse();
                });
        assertThatThrownBy(() -> variants.instagram(URI.create("https://elsewhere.example.test/a/b/1600.webp")))
                .isInstanceOfSatisfying(PhotoVariantException.class,
                        e -> assertThat(e.code()).isEqualTo(PhotoVariantException.UNAVAILABLE));
    }

    @Test
    void aFileThatIsNotAnImageCannotBeConverted() {
        String prefix = prefix();
        put(prefix + "/1600.webp", "not an image".getBytes(), "image/webp");

        assertThatThrownBy(() -> variants.instagram(photoUrl(prefix)))
                .isInstanceOfSatisfying(PhotoVariantException.class, e -> {
                    assertThat(e.code()).isEqualTo(PhotoVariantException.CONVERSION_FAILED);
                    assertThat(e.temporary()).isFalse();
                });
    }

    /** A photo of one colour: its edges must be in the version, a crop would remove them. */
    static byte[] image(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(PHOTO);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static String prefix() {
        return "listings/" + UUID.randomUUID() + "/" + UUID.randomUUID();
    }

    private static URI photoUrl(String prefix) {
        return URI.create(PHOTO_BASE_URL + "/" + prefix + "/1600.webp");
    }

    private static String key(URI url) {
        return url.toString().substring(PHOTO_BASE_URL.length() + 1);
    }

    private void put(String key, byte[] bytes, String contentType) {
        s3.putObject(put -> put.bucket(TestGarage.BUCKET).key(key).contentType(contentType),
                RequestBody.fromBytes(bytes));
    }

    private BufferedImage read(String key) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(
                s3.getObjectAsBytes(get -> get.bucket(TestGarage.BUCKET).key(key)).asByteArray()));
    }

    private static void assertCream(BufferedImage image, int x, int y) {
        assertColor(image, x, y, new Color(0xFF, 0xF8, 0xEC));
    }

    private static void assertPhoto(BufferedImage image, int x, int y) {
        assertColor(image, x, y, PHOTO);
    }

    /** Within the JPEG compression. */
    private static void assertColor(BufferedImage image, int x, int y, Color expected) {
        Color actual = new Color(image.getRGB(x, y));
        assertThat(Math.abs(actual.getRed() - expected.getRed()) + Math.abs(actual.getGreen() - expected.getGreen())
                + Math.abs(actual.getBlue() - expected.getBlue()))
                .as("colour at %d,%d: %s", x, y, actual).isLessThan(24);
    }
}
