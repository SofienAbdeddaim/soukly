package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.common.event.ListingEvents;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGraph;
import com.soukly.social.support.TestJwks;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publication of every listing on Soukly's own Facebook Page (SOC-07), against the simulated Graph API of
 * {@link TestGraph}: the Gherkin scenarios of the ticket. Soukly's Page is the connection of
 * {@link IntegrationTest#HOUSE_USER}, made through "My networks" like any member's.
 */
@ExtendWith(OutputCaptureExtension.class)
class HousePublishingTests extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    @Qualifier(PostChannelsConfig.HOUSE)
    private FacebookPublisher housePublisher;

    @Autowired
    private FacebookPublisher sellerPublisher;

    @Autowired
    private MeterRegistry meters;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID member;
    private String housePageId;
    private String housePageToken;
    private CapturedOutput output;

    @BeforeEach
    void houseAccount(CapturedOutput output) throws Exception {
        member = UUID.randomUUID();
        // A new Page every test: the pace of a Page is kept in memory between the tests
        housePageId = newPageId();
        housePageToken = TestGraph.pageToken(connectPage(HOUSE_USER, housePageId), housePageId);
        // The house posts recorded by the other test classes are not the subject here
        jdbc.update("UPDATE house_posts SET status = 'CANCELLED' WHERE status = 'PENDING'");
        this.output = output;
    }

    @AfterEach
    void noTokenInTheLogs() {
        assertThat(output.getAll()).doesNotContain(housePageToken).doesNotContain("seller@example.tn");
    }

    @Test
    void aListingWithoutNetworkIsPublishedOnSouklysPage() throws Exception {
        UUID listing = UUID.randomUUID();
        double published = counter("PUBLISHED");

        publish(listing, 2, "fr", true);

        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "PENDING").containsEntry("user_id", member);
        // Soukly's Instagram is not connected in this test
        assertThat(houseRow(listing, "INSTAGRAM")).containsEntry("status", "SKIPPED")
                .containsEntry("last_error_code", "not-connected");
        assertThat(sellerRows(listing)).isZero();

        assertThat(housePublisher.publishDue().published()).isEqualTo(1);

        assertThat(TestGraph.calls(housePageId, "photo")).hasSize(2).allSatisfy(call -> assertThat(call.params())
                .containsEntry("access_token", housePageToken));
        String message = TestGraph.calls(housePageId, "post").getFirst().params().get("message");
        assertThat(message).startsWith("Peugeot 208 Allure")
                .contains("/fr/listings/" + listing + "?utm_source=facebook&utm_medium=social"
                        + "&utm_campaign=soukly_house");
        Map<String, Object> row = houseRow(listing, "FACEBOOK");
        assertThat(row).containsEntry("status", "PUBLISHED").containsEntry("external_account_id", housePageId);
        assertThat(row.get("permalink")).isNotNull();
        assertThat(counter("PUBLISHED")).isEqualTo(published + 1);
    }

    @Test
    void theTextOfAHousePostHasNoPersonalData() throws Exception {
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "ar", true);

        housePublisher.publishDue();

        String message = TestGraph.calls(housePageId, "post").getFirst().params().get("message");
        assertThat(message).doesNotContain("seller@example.tn").doesNotContain("+216")
                .contains("/ar/listings/" + listing);
    }

    @Test
    void aSellerWithAPageGetsTwoDistinctPosts() throws Exception {
        String sellerPageId = newPageId();
        connectPage(member, sellerPageId);
        UUID listing = UUID.randomUUID();

        publish(listing, 1, "fr", true, "FACEBOOK");
        sellerPublisher.publishDue();
        housePublisher.publishDue();

        assertThat(TestGraph.calls(sellerPageId, "post")).hasSize(1);
        assertThat(TestGraph.calls(housePageId, "post")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM social_posts WHERE listing_id = ? AND network = "
                + "'FACEBOOK'", String.class, listing)).isEqualTo("PUBLISHED");
        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "PUBLISHED");
    }

    @Test
    void aSellerWhoUntickedSouklysPagesGetsNoHousePost() throws Exception {
        UUID listing = UUID.randomUUID();

        publish(listing, 1, "fr", false);
        housePublisher.publishDue();

        assertThat(houseRows(listing)).isZero();
        assertThat(TestGraph.calls(housePageId)).isEmpty();
    }

    @Test
    void anEventOfAPreviousVersionWithoutHouseShareIsSharedInFrench() throws Exception {
        UUID listing = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        // A version before SOC-07: no houseShare, no language without a network
        send(listing, published(eventId, listing, 1, null, null));
        processed(eventId);

        housePublisher.publishDue();

        assertThat(TestGraph.calls(housePageId, "post").getFirst().params().get("message"))
                .contains("/fr/listings/" + listing);
    }

    @Test
    void theListingsOfSouklysOwnAccountGetNoHousePost() throws Exception {
        member = HOUSE_USER;
        UUID listing = UUID.randomUUID();

        publish(listing, 1, "fr", true);

        assertThat(houseRows(listing)).isZero();
    }

    @Test
    void anExpiredOrDeactivatedListingLeavesSouklysPage() throws Exception {
        UUID expired = UUID.randomUUID();
        UUID deactivated = UUID.randomUUID();
        publish(expired, 1, "fr", true);
        housePublisher.publishDue();
        clock.advance(Duration.ofMinutes(2));
        publish(deactivated, 1, "fr", true);
        housePublisher.publishDue();
        assertThat(TestGraph.calls(housePageId, "post")).hasSize(2);

        event(expired, ListingEvents.EXPIRED);
        event(deactivated, ListingEvents.DEACTIVATED);

        assertThat(houseRow(expired, "FACEBOOK")).containsEntry("status", "DELETED");
        assertThat(houseRow(deactivated, "FACEBOOK")).containsEntry("status", "DELETED");
        assertThat(TestGraph.calls(housePageId, "delete")).hasSize(2).allSatisfy(call -> assertThat(call.params())
                .containsEntry("access_token", housePageToken));
    }

    @Test
    void anExpiredListingKeepsThePostOnTheSellersPage() throws Exception {
        String sellerPageId = newPageId();
        connectPage(member, sellerPageId);
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", true, "FACEBOOK");
        sellerPublisher.publishDue();
        housePublisher.publishDue();

        event(listing, ListingEvents.EXPIRED);

        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "DELETED");
        assertThat(jdbc.queryForObject("SELECT status FROM social_posts WHERE listing_id = ? AND network = "
                + "'FACEBOOK'", String.class, listing)).isEqualTo("PUBLISHED");
        assertThat(TestGraph.calls(sellerPageId, "delete")).isEmpty();
    }

    @Test
    void aDeletedListingIsRemovedFromBothPages() throws Exception {
        String sellerPageId = newPageId();
        connectPage(member, sellerPageId);
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", true, "FACEBOOK");
        sellerPublisher.publishDue();
        housePublisher.publishDue();

        event(listing, ListingEvents.DELETED);

        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "DELETED");
        assertThat(TestGraph.calls(sellerPageId, "delete")).hasSize(1);
        assertThat(TestGraph.calls(housePageId, "delete")).hasSize(1);
    }

    @Test
    void aListingDeletedBeforeItsHousePostIsNeverPublished() throws Exception {
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", true);

        event(listing, ListingEvents.DELETED);
        housePublisher.publishDue();

        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "CANCELLED");
        assertThat(TestGraph.calls(housePageId)).isEmpty();
    }

    @Test
    void aHousePostWaitingForMoreThan48HoursIsSkipped() throws Exception {
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", true);

        clock.advance(Duration.ofHours(49));
        housePublisher.publishDue();

        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "SKIPPED")
                .containsEntry("last_error_code", "stale");
        assertThat(TestGraph.calls(housePageId)).isEmpty();
    }

    @Test
    void housePostsOnSouklysPageAreTwoMinutesApart() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        publish(first, 1, "fr", true);
        publish(second, 1, "fr", true);

        housePublisher.publishDue();
        assertThat(TestGraph.calls(housePageId, "post")).hasSize(1);
        clock.advance(Duration.ofSeconds(30));
        housePublisher.publishDue();
        assertThat(TestGraph.calls(housePageId, "post")).hasSize(1);
        clock.advance(Duration.ofSeconds(90));
        housePublisher.publishDue();

        assertThat(TestGraph.calls(housePageId, "post")).hasSize(2);
        assertThat(houseRow(second, "FACEBOOK")).containsEntry("status", "PUBLISHED").containsEntry("attempts", 0);
    }

    @Test
    void aHousePageToReconnectSkipsTheHousePosts() throws Exception {
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ? AND network = "
                + "'FACEBOOK'", HOUSE_USER);
        UUID listing = UUID.randomUUID();

        publish(listing, 1, "fr", true);

        assertThat(houseRow(listing, "FACEBOOK")).containsEntry("status", "SKIPPED")
                .containsEntry("last_error_code", "needs-reconnect");
    }

    @Test
    void theDeletionOfTheSellersAccountRemovesTheirHousePosts() throws Exception {
        UUID listing = UUID.randomUUID();
        publish(listing, 1, "fr", true);
        housePublisher.publishDue();
        UUID eventId = UUID.randomUUID();

        kafka.send(com.soukly.common.event.UserEvents.TOPIC, member.toString(), """
                {"eventId": "%s", "type": "user.deleted", "schemaVersion": 1, "occurredAt": "%s", "userId": "%s",
                 "data": {}}""".formatted(eventId, Instant.now(), member)).get();
        processed(eventId);

        assertThat(TestGraph.calls(housePageId, "delete")).singleElement().satisfies(call -> assertThat(
                call.params()).containsEntry("access_token", housePageToken));
        assertThat(houseRows(listing)).isZero();
        // Soukly's own connection stays
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                HOUSE_USER)).isPositive();
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
                List.of(new TestGraph.Page(page, "Soukly", "Shopping & retail")));
        mockMvc.perform(post("/social/facebook/callback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
        return code;
    }

    private static String newPageId() {
        return String.valueOf(2_000_000 + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000));
    }

    /** Sends {@code listing.published} and waits until it is consumed. */
    private void publish(UUID listing, int photos, String language, boolean houseShare, String... shareTo)
            throws Exception {
        UUID eventId = UUID.randomUUID();
        send(listing, published(eventId, listing, photos, language, houseShare, shareTo));
        processed(eventId);
    }

    private void event(UUID listing, String type) throws Exception {
        UUID eventId = UUID.randomUUID();
        send(listing, envelope(eventId, type, listing, "{}"));
        processed(eventId);
    }

    private void processed(UUID eventId) {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);
    }

    private void send(UUID listing, String event) throws Exception {
        kafka.send(new ProducerRecord<>(ListingEvents.TOPIC, listing.toString(), event)).get();
    }

    private Map<String, Object> houseRow(UUID listing, String network) {
        return jdbc.queryForMap("SELECT * FROM house_posts WHERE listing_id = ? AND network = ?", listing, network);
    }

    private int houseRows(UUID listing) {
        return jdbc.queryForObject("SELECT count(*) FROM house_posts WHERE listing_id = ?", Integer.class, listing);
    }

    private int sellerRows(UUID listing) {
        return jdbc.queryForObject("SELECT count(*) FROM social_posts WHERE listing_id = ?", Integer.class, listing);
    }

    private double counter(String status) {
        var counter = meters.find("social.house_posts").tag("network", "FACEBOOK").tag("status", status).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String photoUrl(UUID listing, int index) {
        return "https://photos.example.test/listings/" + listing + "/photo-" + index + "/1600.webp";
    }

    /**
     * @param language   null leaves {@code shareLanguage} out
     * @param houseShare null leaves {@code houseShare} out, as a version before SOC-07
     */
    private String published(UUID eventId, UUID listing, int photos, String language, Boolean houseShare,
            String... shareTo) {
        String photoUrls = IntStream.rangeClosed(1, photos).mapToObj(i -> "\"" + photoUrl(listing, i) + "\"")
                .collect(Collectors.joining(","));
        String networks = java.util.Arrays.stream(shareTo).map(network -> "\"" + network + "\"")
                .collect(Collectors.joining(","));
        String optional = (language == null ? "" : ", \"shareLanguage\": \"" + language + "\"")
                + (houseShare == null ? "" : ", \"houseShare\": " + houseShare);
        String data = """
                {"title": "Peugeot 208 Allure",
                 "description": "Première main, carnet d'entretien complet. Contact : seller@example.tn",
                 "categoryCode": "vehicles.cars", "kind": "SALE", "priceMode": "FIXED", "price": 32000,
                 "currency": "TND", "negotiable": true, "governorateCode": "sfax", "delegationCode": "sfax.sfax-ville",
                 "coverPhotoUrl": "%s", "photoUrls": [%s],
                 "publicUrls": {"fr": "%s/fr/listings/%s", "ar": "%s/ar/listings/%s", "en": "%s/en/listings/%s"},
                 "shareTo": [%s], "expiresAt": "2026-12-11T20:00:00Z", "shortRef": "8F3C2K9A"%s}"""
                .formatted(photoUrl(listing, 1), photoUrls, FRONT_BASE_URL, listing, FRONT_BASE_URL, listing,
                        FRONT_BASE_URL, listing, networks, optional);
        return envelope(eventId, ListingEvents.PUBLISHED, listing, data);
    }

    private String envelope(UUID eventId, String type, UUID listing, String data) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "%s", "listingId": "%s",
                 "ownerId": "%s", "data": %s}""".formatted(eventId, type, Instant.now(), listing, member, data);
    }
}
