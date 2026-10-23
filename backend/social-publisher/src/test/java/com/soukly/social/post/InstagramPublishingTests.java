package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.common.event.ListingEvents;
import com.soukly.common.event.UserEvents;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGarage;
import com.soukly.social.support.TestGraph;
import com.soukly.social.support.TestInstagram;
import com.soukly.social.support.TestInstagram.Failure;
import com.soukly.social.support.TestInstagram.MediaCall;
import com.soukly.social.support.TestJwks;

import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publication of the listings on the Instagram professional account of the seller (SOC-05), against the simulated
 * Instagram API of {@link TestInstagram} and the Garage of the tests: the Gherkin scenarios of the ticket, from
 * {@code listing.published} to the carousel, its retries, the limit of the account and the removal.
 */
@ExtendWith(OutputCaptureExtension.class)
class InstagramPublishingTests extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String SHORT_REF = "8F3C2K9A";

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private InstagramPublisher publisher;

    @Autowired
    private FacebookPublisher facebookPublisher;

    @Autowired
    private S3Client s3;

    @Autowired
    private MeterRegistry meters;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID member;
    private String code;
    private String account;
    private CapturedOutput output;

    @BeforeEach
    void member(CapturedOutput output) {
        member = UUID.randomUUID();
        this.output = output;
    }

    @AfterEach
    void noTokenInTheLogs() {
        if (code != null) {
            assertThat(output.getAll()).doesNotContain(TestInstagram.longToken(code))
                    .doesNotContain("seller@example.tn");
        }
    }

    @Test
    void aListingWithFourPhotosIsPublishedAsACarousel() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        List<String> photos = storePhotos(listing, 4);
        double published = counter("PUBLISHED");

        publish(listing, photos, "fr", "INSTAGRAM");

        assertThat(row(listing)).containsEntry("status", "PENDING").containsEntry("attempts", 0);
        assertThat(TestInstagram.calls(account)).isEmpty();

        assertThat(publisher.publishDue().published()).isGreaterThanOrEqualTo(1);

        // 4 JPEG versions of 1080 px next to the WebP ones, fetched by Instagram from their public URL
        List<MediaCall> items = TestInstagram.calls(account, "item");
        assertThat(items).extracting(call -> call.params().get("image_url"))
                .containsExactlyElementsOf(photos.stream().map(InstagramPublishingTests::variantUrl).toList());
        photos.forEach(prefix -> assertThat(s3.headObject(head -> head.bucket(TestGarage.BUCKET)
                .key(prefix + "/ig-1080.jpg")).contentType()).isEqualTo("image/jpeg"));
        assertThat(items).allSatisfy(call -> {
            assertThat(call.params()).containsEntry("is_carousel_item", "true").doesNotContainKey("caption");
            assertThat(call.params()).containsEntry("access_token", TestInstagram.longToken(code));
        });
        List<MediaCall> carousels = TestInstagram.calls(account, "carousel");
        assertThat(carousels).hasSize(1);
        Map<String, String> carousel = carousels.getFirst().params();
        assertThat(carousel.get("children")).isEqualTo(items.stream().map(MediaCall::id)
                .collect(Collectors.joining(",")));
        assertThat(carousel.get("caption")).startsWith("Peugeot 208 Allure\n32")
                .contains("DT · Négociable\nSfax, Sfax Ville\n\nPremière main, carnet d'entretien complet.")
                .endsWith("\n\nAnnonce complète sur soukly.example.test/r/" + SHORT_REF + "\n#Soukly #Sfax #Voitures")
                .doesNotContain("https://").doesNotContain("seller@example.tn");
        assertThat(TestInstagram.calls(account, "image")).isEmpty();
        List<MediaCall> published1 = TestInstagram.calls(account, "publish");
        assertThat(published1).hasSize(1);
        assertThat(published1.getFirst().params()).containsEntry("creation_id", carousels.getFirst().id());

        Map<String, Object> row = row(listing);
        String mediaId = published1.getFirst().id();
        assertThat(row).containsEntry("status", "PUBLISHED").containsEntry("external_account_id", account)
                .containsEntry("external_post_id", mediaId)
                .containsEntry("permalink", TestInstagram.permalinkOf(mediaId));
        assertThat(row.get("published_at")).isNotNull();
        assertThat(counter("PUBLISHED")).isEqualTo(published + 1);
    }

    @Test
    void aListingWithOnePhotoIsOneImageWithoutCarousel() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        List<String> photos = storePhotos(listing, 1);

        publish(listing, photos, "ar", "INSTAGRAM");
        publisher.publishDue();

        List<MediaCall> images = TestInstagram.calls(account, "image");
        assertThat(images).hasSize(1);
        assertThat(images.getFirst().params()).containsEntry("image_url", variantUrl(photos.getFirst()))
                .doesNotContainKey("is_carousel_item");
        assertThat(images.getFirst().params().get("caption"))
                .contains("الإعلان كاملًا على soukly.example.test/r/" + SHORT_REF, "#صفاقس");
        assertThat(TestInstagram.calls(account, "item")).isEmpty();
        assertThat(TestInstagram.calls(account, "carousel")).isEmpty();
        assertThat(TestInstagram.calls(account, "publish").getFirst().params())
                .containsEntry("creation_id", images.getFirst().id());
        assertThat(row(listing)).containsEntry("status", "PUBLISHED");
    }

    @Test
    void anExistingVersionIsUsedAsIs() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        List<String> photos = storePhotos(listing, 1);
        byte[] existing = jpeg();
        s3.putObject(put -> put.bucket(TestGarage.BUCKET).key(photos.getFirst() + "/ig-1080.jpg")
                .contentType("image/jpeg"), RequestBody.fromBytes(existing));

        publish(listing, photos, "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(s3.getObjectAsBytes(get -> get.bucket(TestGarage.BUCKET).key(photos.getFirst() + "/ig-1080.jpg"))
                .asByteArray()).isEqualTo(existing);
        assertThat(row(listing)).containsEntry("status", "PUBLISHED");
    }

    @Test
    void aContainerStillInProgressAfterFiveReadingsIsTriedAgainLater() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        TestInstagram.processing(account, 5);

        publish(listing, storePhotos(listing, 2), "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(TestInstagram.calls(account, "status")).hasSize(5);
        assertThat(TestInstagram.calls(account, "publish")).isEmpty();
        Map<String, Object> row = row(listing);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error_code", "media-not-ready");
        assertThat(((Timestamp) row.get("next_attempt_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));

        // Ready at the next attempt: new containers, published
        TestInstagram.processing(account, 0);
        clock.advance(Duration.ofMinutes(1));
        publisher.publishDue();
        assertThat(row(listing)).containsEntry("status", "PUBLISHED").containsEntry("attempts", 1);
    }

    @Test
    void theDailyLimitOfTheAccountPostponesThePostWithoutCountingAnAttempt() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        TestInstagram.quotaUsage(account, TestInstagram.QUOTA_TOTAL);

        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        publisher.publishDue();

        Map<String, Object> row = row(listing);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 0);
        assertThat(((Timestamp) row.get("next_attempt_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofHours(1)));
        assertThat(TestInstagram.calls(account, "image")).isEmpty();

        // The limit read again an hour later, below the total once a post left the window
        TestInstagram.quotaUsage(account, TestInstagram.QUOTA_TOTAL - 1);
        clock.advance(Duration.ofHours(1));
        publisher.publishDue();
        assertThat(row(listing)).containsEntry("status", "PUBLISHED").containsEntry("attempts", 0);
    }

    @Test
    void theLimitReachedAtThePublicationPostponesThePostToo() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        TestInstagram.failNext(account, new Failure("publish", 400, 9, 2207042));

        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "PENDING").containsEntry("attempts", 0);
    }

    @Test
    void facebookAndInstagramArePublishedIndependently() throws Exception {
        connect();
        String pageId = String.valueOf(2_000_000 + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000));
        connectPage(pageId);
        UUID listing = UUID.randomUUID();
        TestGraph.failNext(pageId, new TestGraph.Failure(400, 368, null));

        publish(listing, storePhotos(listing, 2), "fr", "FACEBOOK", "INSTAGRAM");
        assertThat(jdbc.queryForList("SELECT network FROM social_posts WHERE listing_id = ? ORDER BY network",
                String.class, listing)).containsExactly("FACEBOOK", "INSTAGRAM");

        facebookPublisher.publishDue();
        publisher.publishDue();

        assertThat(jdbc.queryForObject("SELECT status FROM social_posts WHERE listing_id = ? AND network = 'FACEBOOK'",
                String.class, listing)).isEqualTo("FAILED");
        assertThat(row(listing)).containsEntry("status", "PUBLISHED");
    }

    @Test
    void aRefusedPhotoFailsThePost() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        TestInstagram.failNext(account, new Failure("image", 400, 36003, 2207009));

        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "FAILED").containsEntry("last_error_code", "media-rejected")
                .containsEntry("attempts", 1);
    }

    @Test
    void anInvalidTokenFailsThePostAndAsksForAReconnection() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        TestInstagram.failNext(account, new Failure("limit", 400, 190, null));

        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "FAILED").containsEntry("last_error_code", "needs-reconnect");
        assertThat(jdbc.queryForObject("SELECT status FROM social_connections WHERE user_id = ? AND network = "
                + "'INSTAGRAM'", String.class, member)).isEqualTo("NEEDS_RECONNECT");
    }

    @Test
    void aTemporaryFailureIsTriedAgain() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        TestInstagram.failNext(account, new Failure("carousel", 500, 2, null));

        publish(listing, storePhotos(listing, 2), "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error_code", "meta-2");
    }

    @Test
    void aMissingPhotoFailsThePost() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();

        publish(listing, List.of("listings/" + listing + "/" + UUID.randomUUID()), "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "FAILED").containsEntry("last_error_code",
                "photo-unavailable");
        assertThat(TestInstagram.calls(account, "image")).isEmpty();
    }

    @Test
    void aSellerWithoutInstagramIsSkipped() throws Exception {
        UUID listing = UUID.randomUUID();

        publish(listing, List.of("listings/" + listing + "/p"), "fr", "INSTAGRAM");

        assertThat(row(listing)).containsEntry("status", "SKIPPED").containsEntry("last_error_code", "not-connected");
    }

    @Test
    void aPublishedPostCannotBeDeletedThroughTheApi() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        publisher.publishDue();

        deleteListing(listing);

        assertThat(row(listing)).containsEntry("status", "PUBLISHED")
                .containsEntry("last_error_code", "delete-unsupported");
    }

    @Test
    void aPendingPostIsCancelledWithItsListing() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        double cancelled = counter("CANCELLED");

        deleteListing(listing);
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "CANCELLED");
        assertThat(TestInstagram.calls(account)).isEmpty();
        assertThat(counter("CANCELLED")).isEqualTo(cancelled + 1);
    }

    @Test
    void theRowsOfADeletedAccountAreDeleted() throws Exception {
        connect();
        UUID listing = UUID.randomUUID();
        publish(listing, storePhotos(listing, 1), "fr", "INSTAGRAM");
        publisher.publishDue();

        UUID eventId = UUID.randomUUID();
        kafka.send(new ProducerRecord<>(UserEvents.TOPIC, member.toString(), """
                {"eventId": "%s", "type": "user.deleted", "schemaVersion": 1, "occurredAt": "%s", "userId": "%s",
                 "data": {}}""".formatted(eventId, Instant.now(), member))).get();
        processed(eventId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_posts WHERE listing_id = ?", Integer.class,
                listing)).isZero();
    }

    private void connect() throws Exception {
        String body = mockMvc.perform(post("/social/instagram/connect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lang\":\"fr\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String state = UriComponentsBuilder.fromUriString(json.readTree(body).get("authorizeUrl").asString()).build()
                .getQueryParams().getFirst("state");
        code = TestInstagram.business("sfax.auto");
        account = TestInstagram.accountId(code);
        mockMvc.perform(post("/social/instagram/callback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
    }

    private void connectPage(String pageId) throws Exception {
        String body = mockMvc.perform(post("/social/facebook/connect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String state = UriComponentsBuilder.fromUriString(json.readTree(body).get("authorizeUrl").asString()).build()
                .getQueryParams().getFirst("state");
        String facebookCode = TestGraph.person(TestGraph.ALL_PERMISSIONS,
                List.of(new TestGraph.Page(pageId, "Sfax Auto", "Car dealership")));
        mockMvc.perform(post("/social/facebook/callback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + facebookCode + "\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
    }

    /** Stores the 1600 versions of {@code count} photos and returns their prefixes, in order. */
    private List<String> storePhotos(UUID listing, int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            String prefix = "listings/" + listing + "/" + UUID.randomUUID();
            s3.putObject(put -> put.bucket(TestGarage.BUCKET).key(prefix + "/1600.webp").contentType("image/webp"),
                    RequestBody.fromBytes(png(1600, i % 2 == 0 ? 1200 : 533)));
            return prefix;
        }).toList();
    }

    private static String photoUrl(String prefix) {
        return PHOTO_BASE_URL + "/" + prefix + "/1600.webp";
    }

    private static String variantUrl(String prefix) {
        return PHOTO_BASE_URL + "/" + prefix + "/ig-1080.jpg";
    }

    private void publish(UUID listing, List<String> photos, String language, String... shareTo) throws Exception {
        UUID eventId = UUID.randomUUID();
        String photoUrls = photos.stream().map(prefix -> "\"" + photoUrl(prefix) + "\"")
                .collect(Collectors.joining(","));
        String networks = Arrays.stream(shareTo).map(network -> "\"" + network + "\"")
                .collect(Collectors.joining(","));
        String data = """
                {"title": "Peugeot 208 Allure",
                 "description": "Première main, carnet d'entretien complet. Contact : seller@example.tn",
                 "categoryCode": "vehicles.cars", "kind": "SALE", "priceMode": "FIXED", "price": 32000,
                 "currency": "TND", "negotiable": true, "governorateCode": "sfax", "delegationCode": "sfax.sfax-ville",
                 "coverPhotoUrl": %s, "photoUrls": [%s],
                 "publicUrls": {"fr": "%s/fr/listings/%s", "ar": "%s/ar/listings/%s", "en": "%s/en/listings/%s"},
                 "shareTo": [%s], "shareLanguage": "%s", "expiresAt": "2026-12-11T20:00:00Z", "shortRef": "%s"}"""
                .formatted(photoUrls.split(",")[0], photoUrls, FRONT_BASE_URL, listing, FRONT_BASE_URL, listing,
                        FRONT_BASE_URL, listing, networks, language, SHORT_REF);
        send(listing, envelope(eventId, ListingEvents.PUBLISHED, listing, data));
        processed(eventId);
    }

    private void deleteListing(UUID listing) throws Exception {
        UUID eventId = UUID.randomUUID();
        send(listing, envelope(eventId, ListingEvents.DELETED, listing, "{}"));
        processed(eventId);
    }

    private void processed(UUID eventId) {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);
    }

    private void send(UUID listing, String event) throws Exception {
        kafka.send(new ProducerRecord<>(ListingEvents.TOPIC, listing.toString(), event)).get();
    }

    private String envelope(UUID eventId, String type, UUID listing, String data) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "%s", "listingId": "%s",
                 "ownerId": "%s", "data": %s}""".formatted(eventId, type, Instant.now(), listing, member, data);
    }

    private Map<String, Object> row(UUID listing) {
        return jdbc.queryForMap("SELECT * FROM social_posts WHERE listing_id = ? AND network = 'INSTAGRAM'", listing);
    }

    private double counter(String status) {
        var counter = meters.find("social.posts").tag("network", "INSTAGRAM").tag("status", status).counter();
        return counter == null ? 0 : counter.count();
    }

    private static byte[] png(int width, int height) {
        return image(width, height, "png");
    }

    private static byte[] jpeg() {
        return image(1080, 810, "jpg");
    }

    private static byte[] image(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(30, 90, 160));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
