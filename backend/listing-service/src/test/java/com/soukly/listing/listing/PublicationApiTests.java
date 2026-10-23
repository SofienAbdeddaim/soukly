package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soukly.common.event.ListingEvents;
import com.soukly.common.outbox.OutboxRelay;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;
import com.soukly.listing.support.TestRedpanda;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Publication and renewal of ANN-03, with the events written in the outbox and relayed to Redpanda. */
class PublicationApiTests extends IntegrationTest {

    private static final Duration TTL = Duration.ofDays(60);

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ListingLifecycle lifecycle;

    @Autowired
    private JsonMapper json;

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    @Test
    void aCompleteDraftIsPublishedForSixtyDaysAndItsEventReachesTheTopic() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 3);
        List<UUID> photos = jdbc.queryForList(
                "SELECT id FROM listing_photos WHERE listing_id = ? ORDER BY position", UUID.class, id);
        Instant now = clock.instant();

        publish(owner, id)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.publishedAt").value(now.toString()))
                .andExpect(jsonPath("$.expiresAt").value(now.plus(TTL).toString()))
                .andExpect(jsonPath("$.missingFields").isEmpty());
        assertThat(statusOf(id)).isEqualTo("PUBLISHED");
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);

        relay.relayAll();

        assertThat(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM outbox_events WHERE aggregate_id = ?",
                Boolean.class, id)).isTrue();
        List<ConsumerRecord<String, String>> records = TestRedpanda.readKey(ListingEvents.TOPIC, id.toString(), 1,
                Duration.ofSeconds(20));
        assertThat(records).hasSize(1);
        JsonNode event = json.readTree(records.getFirst().value());
        assertThat(names(event)).containsExactlyInAnyOrder("eventId", "type", "schemaVersion", "occurredAt",
                "listingId", "ownerId", "data");
        assertThat(event.get("type").asString()).isEqualTo("listing.published");
        assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(event.get("listingId").asString()).isEqualTo(id.toString());
        assertThat(event.get("ownerId").asString()).isEqualTo(owner.toString());
        assertThat(Instant.parse(event.get("occurredAt").asString())).isEqualTo(now);
        assertThat(event.get("eventId").asString()).isEqualTo(
                jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id = ?", UUID.class, id)
                        .toString());

        JsonNode data = event.get("data");
        // Exactly the members of the contract: no name, email or phone number of the seller
        assertThat(names(data)).containsExactlyInAnyOrder("title", "description", "categoryCode", "kind",
                "priceMode", "price", "currency", "negotiable", "governorateCode", "delegationCode",
                "coverPhotoUrl", "photoUrls", "publicUrls", "shareTo", "expiresAt", "shareLanguage", "shortRef");
        assertThat(data.get("title").asString()).isEqualTo("Canapé d'angle en cuir");
        assertThat(data.get("description").asString()).startsWith("Canapé d'angle en cuir marron");
        assertThat(data.get("categoryCode").asString()).isEqualTo("home.furniture");
        assertThat(data.get("kind").asString()).isEqualTo("SALE");
        assertThat(data.get("priceMode").asString()).isEqualTo("FIXED");
        assertThat(data.get("price").asLong()).isEqualTo(1200);
        assertThat(data.get("currency").asString()).isEqualTo("TND");
        assertThat(data.get("negotiable").asBoolean()).isTrue();
        assertThat(data.get("governorateCode").asString()).isEqualTo("sfax");
        assertThat(data.get("delegationCode").asString()).isEqualTo("sfax.sfax-ville");
        List<String> photoUrls = photos.stream()
                .map(photo -> "https://photos.example.test/listings/" + id + "/" + photo + "/1600.webp").toList();
        assertThat(data.get("photoUrls").valueStream().map(JsonNode::asString).toList()).isEqualTo(photoUrls);
        assertThat(data.get("coverPhotoUrl").asString()).isEqualTo(photoUrls.getFirst());
        assertThat(data.get("publicUrls").get("fr").asString())
                .isEqualTo("https://soukly.example.test/fr/listings/" + id);
        assertThat(data.get("publicUrls").get("ar").asString())
                .isEqualTo("https://soukly.example.test/ar/listings/" + id);
        assertThat(data.get("publicUrls").get("en").asString())
                .isEqualTo("https://soukly.example.test/en/listings/" + id);
        assertThat(data.get("shareTo").isArray()).isTrue();
        assertThat(data.get("shareTo")).isEmpty();
        // No choice on the preview (SOC-03): no network, so no language
        assertThat(data.get("shareLanguage").isNull()).isTrue();
        assertThat(Instant.parse(data.get("expiresAt").asString())).isEqualTo(now.plus(TTL));
    }

    @Test
    void aDraftWithoutPhotoIsIncompleteAndWritesNoEvent() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        publish(owner, id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-incomplete"))
                .andExpect(jsonPath("$.missingFields", contains("photos")));

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(events(id)).isEmpty();
    }

    @Test
    void everyMissingFieldIsListedInFormOrder() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, "{ \"categoryCode\": \"vehicles.cars\" }");

        publish(owner, id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.missingFields", contains("photos", "title", "description", "priceMode",
                        "governorateCode", "attributes.brand", "attributes.year", "attributes.mileage-km",
                        "attributes.fuel", "attributes.gearbox")));
    }

    @Test
    void anErrorAfterTheStatusChangeRollsBackTheListingAndItsEvent() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        // The outbox insert fails for this listing only, after the UPDATE of its status in the same transaction
        jdbc.execute("""
                CREATE FUNCTION test_fail_outbox() RETURNS trigger AS $$
                BEGIN
                    IF NEW.aggregate_id = '%s' THEN
                        RAISE EXCEPTION 'provoked failure';
                    END IF;
                    RETURN NEW;
                END $$ LANGUAGE plpgsql""".formatted(id));
        jdbc.execute("""
                CREATE TRIGGER test_fail_outbox BEFORE INSERT ON outbox_events
                FOR EACH ROW EXECUTE FUNCTION test_fail_outbox()""");
        try {
            publish(owner, id).andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_outbox ON outbox_events");
            jdbc.execute("DROP FUNCTION test_fail_outbox()");
        }

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForMap("SELECT version, published_at, expires_at FROM listings WHERE id = ?", id))
                .containsEntry("version", 1L).containsEntry("published_at", null).containsEntry("expires_at", null);
        assertThat(events(id)).isEmpty();

        // Nothing was left behind: the publication works once the failure is gone
        publish(owner, id).andExpect(status().isOk());
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);
    }

    @Test
    void aPublishedListingCannotBePublishedAgain() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        publish(owner, id).andExpect(status().isOk());

        publish(owner, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:already-published"));

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);
    }

    @Test
    void onlyTheOwnerPublishesAndTheOthersSeeNothing() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);

        publish(UUID.randomUUID(), id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        renew(UUID.randomUUID(), id).andExpect(status().isNotFound());
        publish(owner, UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(post("/listings/not-a-uuid/publish").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/listings/" + id + "/publish")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/listings/" + id + "/renew")).andExpect(status().isUnauthorized());

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(events(id)).isEmpty();
    }

    @Test
    void anExpiredListingIsRenewedForSixtyDaysWithoutANewPublication() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 2);
        publish(owner, id).andExpect(status().isOk());
        Instant publishedAt = clock.instant();
        clock.advance(TTL.plusMinutes(1));
        lifecycle.run();
        assertThat(statusOf(id)).isEqualTo("EXPIRED");
        clock.advance(Duration.ofDays(5));
        Instant now = clock.instant();

        renew(owner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(publishedAt.toString()))
                .andExpect(jsonPath("$.expiresAt").value(now.plus(TTL).toString()));

        assertThat(jdbc.queryForObject("SELECT reminder_sent_at IS NULL FROM listings WHERE id = ?", Boolean.class,
                id)).isTrue();
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRED,
                ListingEvents.RENEWED);
        JsonNode renewed = json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND type = 'listing.renewed'",
                String.class, id));
        assertThat(names(renewed.get("data"))).containsExactlyInAnyOrder("title", "expiresAt");
        assertThat(renewed.get("data").get("title").asString()).isEqualTo("Canapé d'angle en cuir");
        assertThat(Instant.parse(renewed.get("data").get("expiresAt").asString())).isEqualTo(now.plus(TTL));

        // The events leave in their order, under the key of the listing
        relay.relayAll();
        List<ConsumerRecord<String, String>> records = TestRedpanda.readKey(ListingEvents.TOPIC, id.toString(), 3,
                Duration.ofSeconds(20));
        assertThat(records).extracting(record -> JsonPath.<String>read(record.value(), "$.type"))
                .containsExactly("listing.published", "listing.expired", "listing.renewed");
    }

    @Test
    void aPublishedListingIsRenewedOnlyWithinTheReminderPeriod() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        renew(owner, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:renew-not-allowed"));
        publish(owner, id).andExpect(status().isOk());

        clock.advance(Duration.ofDays(50));
        renew(owner, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:renew-not-allowed"));
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);

        // Expires in 2 days
        clock.advance(Duration.ofDays(8));
        renew(owner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(clock.instant().plus(TTL).toString()));
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.RENEWED);
    }

    private ResultActions publish(UUID user, UUID id) throws Exception {
        return mockMvc.perform(post("/listings/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions renew(UUID user, UUID id) throws Exception {
        return mockMvc.perform(post("/listings/" + id + "/renew").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id);
    }

    /** Types of the outbox events of a listing, in order of creation. */
    private List<String> events(UUID id) {
        return jdbc.queryForList("SELECT type FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
                String.class, id);
    }

    private static Set<String> names(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    private static String bearer(UUID user) {
        return "Bearer " + TestJwks.token(user);
    }
}
