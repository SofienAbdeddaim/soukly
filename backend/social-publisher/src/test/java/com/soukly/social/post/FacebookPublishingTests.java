package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import com.soukly.common.web.SouklyHeaders;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGraph;
import com.soukly.social.support.TestJwks;
import com.soukly.social.support.TestRedpanda;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publication of the listings on the Facebook Page of the seller (SOC-04), against the simulated Graph API of
 * {@link TestGraph}: the Gherkin scenarios of the ticket, from {@code listing.published} read on Redpanda to the post,
 * its retries and its removal.
 */
@ExtendWith(OutputCaptureExtension.class)
class FacebookPublishingTests extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private FacebookPublisher publisher;

    @Autowired
    private MeterRegistry meters;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID member;
    private String pageId;
    private CapturedOutput output;

    @BeforeEach
    void member(CapturedOutput output) {
        member = UUID.randomUUID();
        // A new Page for every test: the pace of a Page is kept in memory between the tests
        pageId = String.valueOf(1_000_000 + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000));
        this.output = output;
    }

    @AfterEach
    void noTokenInTheLogs() {
        assertThat(output.getAll()).doesNotContain("EAApage" + pageId).doesNotContain("seller@example.tn");
    }

    @Test
    void aListingIsPublishedWithItsPhotosAndText() throws Exception {
        String code = connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        double published = counter("PUBLISHED");

        publish(listing, 3, "fr", "FACEBOOK");

        assertThat(row(listing)).containsEntry("status", "PENDING").containsEntry("attempts", 0);
        assertThat(TestGraph.calls(pageId)).isEmpty();

        assertThat(publisher.publishDue().published()).isGreaterThanOrEqualTo(1);

        List<TestGraph.PageCall> photos = TestGraph.calls(pageId, "photo");
        assertThat(photos).extracting(call -> call.params().get("url")).containsExactly(photoUrl(listing, 1),
                photoUrl(listing, 2), photoUrl(listing, 3));
        assertThat(photos).allSatisfy(call -> {
            assertThat(call.params()).containsEntry("published", "false");
            assertThat(call.params()).containsEntry("access_token", TestGraph.pageToken(code, pageId));
        });
        List<TestGraph.PageCall> posts = TestGraph.calls(pageId, "post");
        assertThat(posts).hasSize(1);
        Map<String, String> post = posts.get(0).params();
        assertThat(post).containsKeys("attached_media[0]", "attached_media[1]", "attached_media[2]")
                .doesNotContainKey("attached_media[3]");
        assertThat(post.get("attached_media[0]")).matches("\\{\"media_fbid\":\"photo-\\d+\"}");
        assertThat(post.get("message")).startsWith("Peugeot 208 Allure\n32")
                .contains("DT · Négociable\nSfax, Sfax Ville\n\nPremière main, carnet d'entretien complet.")
                .endsWith("\n\nVoir l'annonce : " + trackedUrl(listing, "fr") + "\n#Soukly #Sfax #Voitures")
                .doesNotContain("seller@example.tn");

        Map<String, Object> row = row(listing);
        assertThat(row).containsEntry("status", "PUBLISHED").containsEntry("external_account_id", pageId);
        assertThat((String) row.get("external_post_id")).startsWith(pageId + "_");
        assertThat((String) row.get("permalink")).startsWith("https://www.facebook.com/" + pageId + "/posts/");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(counter("PUBLISHED")).isEqualTo(published + 1);
        assertThat(meters.find("social.posts.publish").tag("outcome", "published").timer()).isNotNull();
    }

    @Test
    void theLinkOfThePostIsTrackedAndInTheLanguageOfTheText() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();

        publish(listing, 1, "ar", "FACEBOOK", "INSTAGRAM");
        publisher.publishDue();

        String message = TestGraph.calls(pageId, "post").get(0).params().get("message");
        assertThat(message).contains("/ar/listings/" + listing + "?utm_source=facebook&utm_medium=social"
                + "&utm_campaign=soukly_share").contains("#صفاقس", "#سيارات", "قابل للتفاوض");
    }

    @Test
    void anEventDeliveredTwiceGivesOnePost() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String event = published(eventId, listing, 2, "fr", "FACEBOOK");

        send(listing, event, null);
        send(listing, event, null);
        // Same key, so same partition: once the marker is handled, both copies were consumed before it
        send(listing, envelope(UUID.randomUUID(), "listing.future-type", listing, "{}"), null);
        send(listing, envelope(UUID.randomUUID(), ListingEvents.UPDATED, listing, "{}"), null);
        UUID marker = UUID.randomUUID();
        send(listing, published(marker, listing, 1, "fr"), null);
        publishedEvent(marker, listing);
        publisher.publishDue();
        publisher.publishDue();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_posts WHERE listing_id = ?", Integer.class,
                listing)).isEqualTo(1);
        assertThat(TestGraph.calls(pageId, "post")).hasSize(1);
    }

    @Test
    void withoutAFacebookPageThePostIsSkipped() throws Exception {
        UUID listing = UUID.randomUUID();

        publish(listing, 1, "fr", "FACEBOOK");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "SKIPPED").containsEntry("last_error_code", "not-connected");
    }

    @Test
    void aPageToReconnectIsSkippedAndAListingNotSharedHasNoPost() throws Exception {
        connectPage(member, pageId);
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ?", member);
        UUID listing = UUID.randomUUID();
        UUID notShared = UUID.randomUUID();

        publish(listing, 1, "fr", "FACEBOOK");
        publish(notShared, 1, "fr", "INSTAGRAM");
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "SKIPPED").containsEntry("last_error_code",
                "needs-reconnect");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_posts WHERE listing_id = ?", Integer.class,
                notShared)).isZero();
        assertThat(TestGraph.calls(pageId)).isEmpty();
    }

    @Test
    void aTemporaryErrorIsTriedAgainLaterThenFails() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", "FACEBOOK");
        TestGraph.failNext(pageId, IntStream.range(0, 6).mapToObj(i -> new TestGraph.Failure(500, 2, null))
                .toArray(TestGraph.Failure[]::new));

        publisher.publishDue();

        Map<String, Object> row = row(listing);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error_code", "meta-2");
        assertThat(((Timestamp) row.get("next_attempt_at")).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));

        // Not due yet
        clock.advance(Duration.ofSeconds(59));
        publisher.publishDue();
        assertThat(row(listing)).containsEntry("attempts", 1);

        List<Duration> delays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
                Duration.ofHours(2), Duration.ofHours(6));
        clock.advance(Duration.ofSeconds(1));
        for (int failures = 2; failures <= 5; failures++) {
            publisher.publishDue();
            assertThat(row(listing)).containsEntry("status", "PENDING").containsEntry("attempts", failures);
            clock.advance(delays.get(failures - 1));
        }
        // The fifth new attempt fails too: the delays are used up
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "FAILED").containsEntry("attempts", 6)
                .containsEntry("last_error_code", "meta-2");
        assertThat(row(listing).get("next_attempt_at")).isNull();
        assertThat(TestGraph.calls(pageId, "photo")).hasSize(6);
        assertThat(TestGraph.calls(pageId, "post")).isEmpty();
        // The message of Meta is logged without the token nor the email address it quoted
        assertThat(output.getAll()).contains("Simulated failure for […] contact […]");
    }

    @Test
    void anInvalidTokenAsksToReconnectWithoutAnotherAttempt() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        publish(listing, 2, "fr", "FACEBOOK");
        TestGraph.failNext(pageId, new TestGraph.Failure(400, 190, 460));

        publisher.publishDue();
        clock.advance(Duration.ofHours(1));
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "FAILED").containsEntry("last_error_code",
                "needs-reconnect");
        assertThat(jdbc.queryForObject("SELECT status FROM social_connections WHERE user_id = ?", String.class,
                member)).isEqualTo("NEEDS_RECONNECT");
        assertThat(TestGraph.calls(pageId)).hasSize(1);
    }

    @Test
    void aMissingPermissionOrARefusedPhotoFailsAtOnce() throws Exception {
        connectPage(member, pageId);
        UUID permission = UUID.randomUUID();
        UUID refused = UUID.randomUUID();
        publish(permission, 1, "fr", "FACEBOOK");
        TestGraph.failNext(pageId, new TestGraph.Failure(403, 200, null));
        publisher.publishDue();
        publish(refused, 1, "fr", "FACEBOOK");
        TestGraph.failNext(pageId, new TestGraph.Failure(400, 324, null));
        clock.advance(Duration.ofSeconds(30));
        publisher.publishDue();

        assertThat(row(permission)).containsEntry("status", "FAILED").containsEntry("last_error_code",
                "permission-missing");
        assertThat(row(refused)).containsEntry("status", "FAILED").containsEntry("last_error_code", "meta-324");
        assertThat(jdbc.queryForObject("SELECT status FROM social_connections WHERE user_id = ?", String.class,
                member)).isEqualTo("CONNECTED");
    }

    @Test
    void thePostsOfAPageAreThirtySecondsApart() throws Exception {
        connectPage(member, pageId);
        List<UUID> listings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID listing = UUID.randomUUID();
            listings.add(listing);
            publish(listing, 1, "fr", "FACEBOOK");
        }

        for (int tick = 0; tick < 40 && TestGraph.calls(pageId, "post").size() < 5; tick++) {
            publisher.publishDue();
            clock.advance(Duration.ofSeconds(5));
        }

        List<Instant> times = listings.stream()
                .map(listing -> ((Timestamp) row(listing).get("published_at")).toInstant()).sorted().toList();
        assertThat(times).hasSize(5);
        for (int i = 1; i < times.size(); i++) {
            assertThat(Duration.between(times.get(i - 1), times.get(i))).isGreaterThanOrEqualTo(
                    Duration.ofSeconds(30));
        }
        assertThat(listings).allSatisfy(listing -> assertThat(row(listing)).containsEntry("attempts", 0));
    }

    @Test
    void theDeletionOfAListingRemovesItsPost() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", "FACEBOOK");
        publisher.publishDue();
        String postId = (String) row(listing).get("external_post_id");

        deleteListing(listing);

        assertThat(row(listing)).containsEntry("status", "DELETED");
        assertThat(row(listing).get("deleted_at")).isNotNull();
        assertThat(TestGraph.calls(pageId, "delete")).singleElement()
                .satisfies(call -> assertThat(call.params().get("access_token")).startsWith("EAApage" + pageId));
        assertThat(postId).isNotNull();
    }

    @Test
    void aPostAlreadyGoneIsDeletedAndOneThatCannotBeStaysPublished() throws Exception {
        connectPage(member, pageId);
        UUID gone = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        publish(gone, 1, "fr", "FACEBOOK");
        publisher.publishDue();
        clock.advance(Duration.ofSeconds(30));
        publish(kept, 1, "fr", "FACEBOOK");
        publisher.publishDue();
        TestGraph.forgetPost((String) row(gone).get("external_post_id"));

        deleteListing(gone);
        TestGraph.failNext(pageId, new TestGraph.Failure(500, 2, null), new TestGraph.Failure(500, 2, null),
                new TestGraph.Failure(500, 2, null));
        deleteListing(kept);

        assertThat(row(gone)).containsEntry("status", "DELETED");
        assertThat(row(kept)).containsEntry("status", "PUBLISHED").containsEntry("last_error_code", "meta-2");
        // The first deletion, then three attempts for the second one
        assertThat(TestGraph.calls(pageId, "delete")).hasSize(4);
    }

    @Test
    void aListingDeletedBeforeThePublicationIsNeverPublished() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", "FACEBOOK");

        deleteListing(listing);
        publisher.publishDue();

        assertThat(row(listing)).containsEntry("status", "CANCELLED");
        assertThat(TestGraph.calls(pageId)).isEmpty();
    }

    @Test
    void theDeletionOfTheAccountRemovesItsPostsBeforeTheConnection() throws Exception {
        String code = connectPage(member, pageId);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        publish(first, 1, "fr", "FACEBOOK");
        publisher.publishDue();
        clock.advance(Duration.ofSeconds(30));
        publish(second, 1, "fr", "FACEBOOK");
        publisher.publishDue();
        publish(pending, 1, "fr", "FACEBOOK");
        List<String> postIds = List.of((String) row(first).get("external_post_id"),
                (String) row(second).get("external_post_id"));
        UUID eventId = UUID.randomUUID();

        kafka.send(UserEvents.TOPIC, member.toString(), """
                {"eventId": "%s", "type": "user.deleted", "schemaVersion": 1, "occurredAt": "%s", "userId": "%s",
                 "data": {}}""".formatted(eventId, Instant.now(), member)).get();
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);

        // Deleted with the Page token, so while the connection was still there
        assertThat(TestGraph.calls(pageId, "delete")).extracting(call -> call.params().get("access_token"))
                .containsOnly(TestGraph.pageToken(code, pageId)).hasSize(2);
        assertThat(postIds).allSatisfy(postId -> assertThat(postId).startsWith(pageId + "_"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_posts WHERE user_id = ?", Integer.class, member))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                member)).isZero();
        publisher.publishDue();
        assertThat(TestGraph.calls(pageId, "post")).hasSize(2);
    }

    @Test
    void theRequestIdOfTheEventIsKeptForTheLogs() throws Exception {
        connectPage(member, pageId);
        UUID listing = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send(listing, published(eventId, listing, 1, "fr", "FACEBOOK"), "req-soc04-test");
        publishedEvent(eventId, listing);

        assertThat(row(listing)).containsEntry("request_id", "req-soc04-test");
    }

    @Test
    void anInvalidListingEventGoesToTheDlq() throws Exception {
        UUID listing = UUID.randomUUID();
        String invalid = envelope(UUID.randomUUID(), ListingEvents.PUBLISHED, listing,
                "{\"shareTo\": [\"FACEBOOK\"], \"shareLanguage\": \"fr\", \"photoUrls\": []}");

        send(listing, invalid, null);

        List<ConsumerRecord<String, String>> dead = TestRedpanda.read(ListingEvents.DLQ_TOPIC,
                record -> invalid.equals(record.value()), received -> !received.isEmpty(), TIMEOUT);
        assertThat(dead).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_posts WHERE listing_id = ?", Integer.class,
                listing)).isZero();
    }

    /** Connects a single Page through the API; returns the OAuth code. */
    private String connectPage(UUID userId, String page) throws Exception {
        String body = mockMvc.perform(post("/social/facebook/connect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String state = UriComponentsBuilder.fromUriString(json.readTree(body).get("authorizeUrl").asString()).build()
                .getQueryParams().getFirst("state");
        String code = TestGraph.person(TestGraph.ALL_PERMISSIONS,
                List.of(new TestGraph.Page(page, "Sfax Auto", "Car dealership")));
        mockMvc.perform(post("/social/facebook/callback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
        return code;
    }

    /** Sends {@code listing.published} and waits until it is consumed. */
    private void publish(UUID listing, int photos, String language, String... shareTo) throws Exception {
        UUID eventId = UUID.randomUUID();
        send(listing, published(eventId, listing, photos, language, shareTo), null);
        publishedEvent(eventId, listing);
    }

    private void publishedEvent(UUID eventId, UUID listing) {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);
    }

    private void deleteListing(UUID listing) throws Exception {
        UUID eventId = UUID.randomUUID();
        send(listing, envelope(eventId, ListingEvents.DELETED, listing, "{}"), null);
        publishedEvent(eventId, listing);
    }

    private void send(UUID listing, String event, String requestId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(ListingEvents.TOPIC, listing.toString(), event);
        if (requestId != null) {
            record.headers().add(SouklyHeaders.REQUEST_ID, requestId.getBytes(StandardCharsets.UTF_8));
        }
        kafka.send(record).get();
    }

    private Map<String, Object> row(UUID listing) {
        return jdbc.queryForMap("SELECT * FROM social_posts WHERE listing_id = ? AND network = 'FACEBOOK'", listing);
    }

    private double counter(String status) {
        var counter = meters.find("social.posts").tag("network", "FACEBOOK").tag("status", status).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String photoUrl(UUID listing, int index) {
        return "https://photos.example.test/listings/" + listing + "/photo-" + index + "/1600.webp";
    }

    private static String trackedUrl(UUID listing, String language) {
        return FRONT_BASE_URL + "/" + language + "/listings/" + listing
                + "?utm_source=facebook&utm_medium=social&utm_campaign=soukly_share";
    }

    private String published(UUID eventId, UUID listing, int photos, String language, String... shareTo) {
        String photoUrls = IntStream.rangeClosed(1, photos).mapToObj(i -> "\"" + photoUrl(listing, i) + "\"")
                .collect(Collectors.joining(","));
        String networks = java.util.Arrays.stream(shareTo).map(network -> "\"" + network + "\"")
                .collect(Collectors.joining(","));
        String data = """
                {"title": "Peugeot 208 Allure",
                 "description": "Première main, carnet d'entretien complet. Contact : seller@example.tn",
                 "categoryCode": "vehicles.cars", "kind": "SALE", "priceMode": "FIXED", "price": 32000,
                 "currency": "TND", "negotiable": true, "governorateCode": "sfax", "delegationCode": "sfax.sfax-ville",
                 "coverPhotoUrl": "%s", "photoUrls": [%s],
                 "publicUrls": {"fr": "%s/fr/listings/%s", "ar": "%s/ar/listings/%s", "en": "%s/en/listings/%s"},
                 "shareTo": [%s], "shareLanguage": "%s", "expiresAt": "2026-12-11T20:00:00Z"}"""
                .formatted(photoUrl(listing, 1), photoUrls, FRONT_BASE_URL, listing, FRONT_BASE_URL, listing,
                        FRONT_BASE_URL, listing, networks, language);
        return envelope(eventId, ListingEvents.PUBLISHED, listing, data);
    }

    private String envelope(UUID eventId, String type, UUID listing, String data) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "%s", "listingId": "%s",
                 "ownerId": "%s", "data": %s}""".formatted(eventId, type, Instant.now(), listing, member, data);
    }
}
