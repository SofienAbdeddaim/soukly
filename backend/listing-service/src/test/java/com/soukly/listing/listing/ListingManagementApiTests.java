package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soukly.common.event.ListingEvents;
import com.soukly.listing.outbox.OutboxRelay;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestGarage;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;
import com.soukly.listing.support.TestRedpanda;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * "My listings" of ANN-05: the list, the changes of a listing that was published, deactivation, renewal, deletion
 * and purge, with their events.
 */
class ListingManagementApiTests extends IntegrationTest {

    private static final Duration TTL = Duration.ofDays(60);

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ListingLifecycle lifecycle;

    @Autowired
    private ListingDeletion deletion;

    @Autowired
    private S3Client s3;

    @Autowired
    private JsonMapper json;

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    // --- My listings

    @Test
    void theListShowsOneStatusWithTheCountsOfEveryTabAndNeverTheDeletedListings() throws Exception {
        List<UUID> online = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            online.add(TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1));
            clock.advance(Duration.ofSeconds(1));
        }
        UUID draft = TestListings.draft(mockMvc, owner, """
                { "categoryCode": "vehicles.cars", "title": "Golf 7 à vendre" }""");
        UUID expired = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        UUID deleted = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        jdbc.update("UPDATE listings SET status = 'EXPIRED' WHERE id = ?", expired);
        remove(owner, deleted).andExpect(status().isNoContent());
        // Two members were shown the number, one of them twice
        UUID viewer = UUID.randomUUID();
        reveal(online.getFirst(), viewer);
        reveal(online.getFirst(), viewer);
        reveal(online.getFirst(), UUID.randomUUID());

        String body = mine(owner, "")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.counts.PUBLISHED").value(3))
                .andExpect(jsonPath("$.counts.DRAFT").value(1))
                .andExpect(jsonPath("$.counts.EXPIRED").value(1))
                .andExpect(jsonPath("$.counts.INACTIVE").value(0))
                .andExpect(jsonPath("$.counts.DELETED").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        // The latest changed first
        assertThat(ids(body)).containsExactly(online.get(2), online.get(1), online.get(0));
        Map<String, Object> card = JsonPath.read(body, "$.items[2]");
        assertThat(card).containsOnlyKeys("id", "status", "title", "kind", "categoryCode", "priceMode", "price",
                "currency", "coverThumbnailUrl", "updatedAt", "publishedAt", "expiresAt", "contactRevealsCount");
        assertThat(card.get("status")).isEqualTo("PUBLISHED");
        assertThat(card.get("title")).isEqualTo("Canapé d'angle en cuir");
        assertThat(card.get("price")).isEqualTo(1200);
        assertThat(card.get("contactRevealsCount")).isEqualTo(2);
        UUID cover = jdbc.queryForObject("SELECT id FROM listing_photos WHERE listing_id = ? AND position = 0",
                UUID.class, online.getFirst());
        assertThat(card.get("coverThumbnailUrl"))
                .isEqualTo("https://photos.example.test/listings/" + online.getFirst() + "/" + cover + "/400.webp");

        String drafts = body(mine(owner, "?status=DRAFT"));
        assertThat(ids(drafts)).containsExactly(draft);
        List<String> missing = JsonPath.read(drafts, "$.items[0].missingFields");
        assertThat(missing).startsWith("photos", "description").contains("attributes.brand");
        assertThat(JsonPath.<Integer>read(drafts, "$.items[0].missingFieldsCount")).isEqualTo(missing.size());
        assertThat(JsonPath.<Object>read(drafts, "$.items[0].coverThumbnailUrl")).isNull();
        assertThat(ids(body(mine(owner, "?status=EXPIRED")))).containsExactly(expired);
        assertThat(ids(body(mine(owner, "?status=INACTIVE")))).isEmpty();
        // Nobody else's listings
        assertThat(ids(body(mine(UUID.randomUUID(), "")))).isEmpty();
    }

    @Test
    void theListIsReadPageByPageWithACursorOfTheSameStatus() throws Exception {
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE));
            clock.advance(Duration.ofSeconds(1));
        }

        String first = body(mine(owner, "?status=DRAFT&limit=2"));
        String cursor = JsonPath.read(first, "$.nextCursor");
        String second = mine(owner, "?status=DRAFT&limit=2&cursor=" + cursor)
                .andExpect(jsonPath("$.counts").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String third = body(mine(owner, "?status=DRAFT&limit=2&cursor=" + JsonPath.read(second, "$.nextCursor")));

        List<UUID> all = new ArrayList<>(ids(first));
        all.addAll(ids(second));
        all.addAll(ids(third));
        assertThat(all).containsExactlyElementsOf(created.reversed());
        mine(owner, "?status=DRAFT&limit=2&cursor=" + JsonPath.read(second, "$.nextCursor"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        // A cursor of another tab, or a changed one, is refused
        mine(owner, "?status=PUBLISHED&cursor=" + cursor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"))
                .andExpect(jsonPath("$.errors[0].code").value("cursor.invalid"));
        mine(owner, "?status=DRAFT&cursor=abc").andExpect(status().isBadRequest());
        mine(owner, "?status=DELETED").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("status.invalid"));
        mine(owner, "?limit=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("limit.invalid"));
        mockMvc.perform(get("/listings/mine")).andExpect(status().isUnauthorized());
    }

    // --- Changes of a listing that was published

    @Test
    void aChangeOfAPublishedListingIsVisibleAtOnceAndWritesListingUpdated() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 2);

        update(owner, id, "\"2\"", "{ \"price\": 30500 }")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.price").value(30500));

        mockMvc.perform(get("/listings/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(30500));
        String search = body(mockMvc.perform(get("/listings").param("q", "Peugeot 208")));
        assertThat(JsonPath.<List<Integer>>read(search, "$.items[?(@.id == '" + id + "')].price"))
                .containsExactly(30500);

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.UPDATED);
        JsonNode updated = event(id, ListingEvents.UPDATED);
        JsonNode data = updated.get("data");
        assertThat(names(data)).containsExactlyInAnyOrder("changedFields", "title", "description", "categoryCode",
                "kind", "priceMode", "price", "currency", "negotiable", "governorateCode", "delegationCode",
                "coverPhotoUrl", "photoUrls", "publicUrls", "expiresAt");
        assertThat(data.get("changedFields").valueStream().map(JsonNode::asString).toList()).containsExactly("price");
        assertThat(data.get("price").asLong()).isEqualTo(30500);
        assertThat(data.get("photoUrls")).hasSize(2);

        // Nothing changed: no event
        update(owner, id, "\"3\"", "{ \"price\": 30500 }").andExpect(status().isOk());
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.UPDATED);
    }

    @Test
    void theCategoryOfAListingThatWasPublishedIsLocked() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        update(owner, id, "\"2\"", "{ \"categoryCode\": \"home.decor\" }")
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:category-locked"));
        // The same category is not a change
        update(owner, id, "\"2\"", "{ \"categoryCode\": \"home.furniture\", \"title\": \"Canapé d'angle cuir\" }")
                .andExpect(status().isOk());
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.UPDATED);
    }

    @Test
    void aListingThatWasPublishedStaysComplete() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        update(owner, id, "\"2\"", "{ \"title\": null, \"governorateCode\": null, \"delegationCode\": null }")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-incomplete"))
                .andExpect(jsonPath("$.missingFields", contains("title", "governorateCode")));
        assertThat(jdbc.queryForObject("SELECT title FROM listings WHERE id = ?", String.class, id))
                .isEqualTo("Canapé d'angle en cuir");

        // The last photo stays
        UUID photo = jdbc.queryForObject("SELECT id FROM listing_photos WHERE listing_id = ?", UUID.class, id);
        deletePhoto(owner, id, photo)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.missingFields", contains("photos")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_photos WHERE listing_id = ?", Integer.class,
                id)).isEqualTo(1);
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);
    }

    @Test
    void thePhotosOfAPublishedListingChangeWithListingUpdated() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 2);
        List<UUID> photos = jdbc.queryForList(
                "SELECT id FROM listing_photos WHERE listing_id = ? ORDER BY position", UUID.class, id);

        mockMvc.perform(post("/listings/" + id + "/photos")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"contentType\": \"image/jpeg\", \"sizeBytes\": 1000 }"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/listings/" + id + "/photos/order")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"photoIds\": [\"" + photos.get(1) + "\", \"" + photos.get(0) + "\"] }"))
                .andExpect(status().isOk());
        deletePhoto(owner, id, photos.get(0)).andExpect(status().isNoContent());

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.UPDATED,
                ListingEvents.UPDATED);
        JsonNode last = json.readTree(jdbc.queryForObject("""
                SELECT payload::text FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence DESC LIMIT 1""",
                String.class, id));
        assertThat(last.get("data").get("changedFields").valueStream().map(JsonNode::asString).toList())
                .containsExactly("photos");
        assertThat(last.get("data").get("photoUrls")).hasSize(1);
    }

    @Test
    void anExpiredListingIsEditedWithoutEvent() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        clock.advance(TTL.plusMinutes(1));
        lifecycle.run();

        update(owner, id, "\"3\"", "{ \"price\": 1100 }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.price").value(1100));
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRED);
    }

    // --- Deactivation and renewal

    @Test
    void aDeactivatedListingIsGoneUntilItIsRenewed() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        deactivate(owner, id)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/listings/" + id))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-gone"));
        // Its owner still reads it, to edit it
        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertThat(ids(body(mockMvc.perform(get("/listings").param("q", "Canapé cuir"))))).doesNotContain(id);
        assertThat(ids(body(mine(owner, "?status=INACTIVE")))).containsExactly(id);
        deactivate(owner, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:transition-not-allowed"));

        // It never expires while it is deactivated
        clock.advance(TTL.plusDays(1));
        lifecycle.run();
        assertThat(statusOf(id)).isEqualTo("INACTIVE");

        Instant now = clock.instant();
        renew(owner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.expiresAt").value(now.plus(TTL).toString()));
        mockMvc.perform(get("/listings/" + id)).andExpect(status().isOk());

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.DEACTIVATED,
                ListingEvents.RENEWED);
        assertThat(names(event(id, ListingEvents.DEACTIVATED).get("data"))).isEmpty();
        relay.relayAll();
        List<ConsumerRecord<String, String>> records = TestRedpanda.readKey(ListingEvents.TOPIC, id.toString(), 3,
                Duration.ofSeconds(20));
        assertThat(records).extracting(record -> JsonPath.<String>read(record.value(), "$.type"))
                .containsExactly("listing.published", "listing.deactivated", "listing.renewed");
    }

    @Test
    void onlyAPublishedListingIsDeactivated() throws Exception {
        UUID draft = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        deactivate(owner, draft)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:transition-not-allowed"));
        assertThat(statusOf(draft)).isEqualTo("DRAFT");
        assertThat(events(draft)).isEmpty();
    }

    // --- Deletion and purge

    @Test
    void aDeletedListingIsGoneForEveryoneWithListingDeleted() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 2);

        remove(owner, id).andExpect(status().isNoContent());

        assertThat(statusOf(id)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM listings WHERE id = ?", Timestamp.class, id)
                .toInstant()).isEqualTo(clock.instant());
        mockMvc.perform(get("/listings/" + id)).andExpect(status().isGone());
        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isGone());
        update(owner, id, "\"3\"", "{ \"price\": 1 }").andExpect(status().isNotFound());
        remove(owner, id).andExpect(status().isNotFound());
        renew(owner, id).andExpect(status().isNotFound());
        deactivate(owner, id).andExpect(status().isNotFound());
        mockMvc.perform(post("/listings/" + id + "/photos")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"contentType\": \"image/jpeg\", \"sizeBytes\": 1000 }"))
                .andExpect(status().isNotFound());
        for (String status : List.of("PUBLISHED", "DRAFT", "EXPIRED", "INACTIVE")) {
            assertThat(ids(body(mine(owner, "?status=" + status)))).doesNotContain(id);
        }
        // Not purged before the delay: the photos stay
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_photos WHERE listing_id = ?", Integer.class,
                id)).isEqualTo(2);

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.DELETED);
        JsonNode deleted = event(id, ListingEvents.DELETED);
        assertThat(deleted.get("ownerId").asString()).isEqualTo(owner.toString());
        assertThat(names(deleted.get("data"))).isEmpty();
    }

    @Test
    void aDraftIsErasedAtOnceWithItsPhotosAndNoEvent() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);
        List<String> keys = storedPhotos(id, 2);

        remove(owner, id).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM listings WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_photos WHERE listing_id = ?", Integer.class,
                id)).isZero();
        assertThat(keys).noneMatch(this::exists);
        assertThat(events(id)).isEmpty();
        remove(owner, id).andExpect(status().isNotFound());
    }

    @Test
    void theDeletedListingsArePurgedAfterThirtyDays() throws Exception {
        UUID old = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);
        List<String> keys = storedPhotos(old, 3);
        TestListings.publish(mockMvc, owner, old);
        reveal(old, UUID.randomUUID());
        remove(owner, old).andExpect(status().isNoContent());
        clock.advance(Duration.ofDays(1));
        UUID recent = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        remove(owner, recent).andExpect(status().isNoContent());
        UUID online = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        // 31 days minus an hour after the first deletion, 30 days minus an hour after the second
        clock.advance(Duration.ofDays(30).minusHours(1));
        assertThat(deletion.purge()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM listings WHERE id = ?", Integer.class, old)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_photos WHERE listing_id = ?", Integer.class,
                old)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_reveals WHERE listing_id = ?", Integer.class,
                old)).isZero();
        assertThat(keys).noneMatch(this::exists);
        // The events already written are kept
        assertThat(events(old)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.DELETED);
        // Deleted 30 days ago only, and online: kept
        assertThat(statusOf(recent)).isEqualTo("DELETED");
        assertThat(statusOf(online)).isEqualTo("PUBLISHED");
    }

    // --- Someone else's listing

    @Test
    void theListingOfAnotherAccountIsNotFound() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        UUID other = UUID.randomUUID();

        remove(other, id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        deactivate(other, id).andExpect(status().isNotFound());
        update(other, id, "\"2\"", "{ \"price\": 1 }").andExpect(status().isNotFound());
        renew(other, id).andExpect(status().isNotFound());
        remove(owner, UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(delete("/listings/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/listings/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/listings/" + id + "/deactivate")).andExpect(status().isUnauthorized());

        assertThat(statusOf(id)).isEqualTo("PUBLISHED");
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);
    }

    private ResultActions mine(UUID user, String query) throws Exception {
        return mockMvc.perform(get("/listings/mine" + query).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions update(UUID user, UUID id, String ifMatch, String patch) throws Exception {
        return mockMvc.perform(patch("/listings/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType("application/merge-patch+json")
                .content(patch));
    }

    private ResultActions deactivate(UUID user, UUID id) throws Exception {
        return mockMvc.perform(post("/listings/" + id + "/deactivate").header(HttpHeaders.AUTHORIZATION,
                bearer(user)));
    }

    private ResultActions renew(UUID user, UUID id) throws Exception {
        return mockMvc.perform(post("/listings/" + id + "/renew").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions remove(UUID user, UUID id) throws Exception {
        return mockMvc.perform(delete("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions deletePhoto(UUID user, UUID id, UUID photo) throws Exception {
        return mockMvc.perform(delete("/listings/" + id + "/photos/" + photo)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return body(mockMvc.perform(request));
    }

    private static List<UUID> ids(String body) {
        return JsonPath.<List<String>>read(body, "$.items[*].id").stream().map(UUID::fromString).toList();
    }

    private void reveal(UUID listing, UUID viewer) {
        jdbc.update("INSERT INTO contact_reveals (listing_id, viewer_id, revealed_at) VALUES (?, ?, ?)", listing,
                viewer, Timestamp.from(clock.instant()));
    }

    /** {@code count} READY photos with their three objects (original, 1600 and 400 px) in the bucket. */
    private List<String> storedPhotos(UUID listing, int count) {
        List<String> keys = new ArrayList<>();
        for (UUID photo : TestListings.readyPhotos(jdbc, listing, count)) {
            for (String key : List.of("incoming/" + listing + "/" + photo,
                    "listings/" + listing + "/" + photo + "/1600.webp",
                    "listings/" + listing + "/" + photo + "/400.webp")) {
                s3.putObject(put -> put.bucket(TestGarage.BUCKET).key(key), RequestBody.fromString("photo"));
                keys.add(key);
            }
        }
        assertThat(keys).allMatch(this::exists);
        return keys;
    }

    private boolean exists(String key) {
        try {
            s3.headObject(head -> head.bucket(TestGarage.BUCKET).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id);
    }

    /** Types of the outbox events of a listing, in order of creation. */
    private List<String> events(UUID id) {
        return jdbc.queryForList("SELECT type FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
                String.class, id);
    }

    private JsonNode event(UUID id, String type) {
        return json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND type = ?", String.class, id, type));
    }

    private static Set<String> names(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    private static String bearer(UUID user) {
        return "Bearer " + TestJwks.token(user);
    }
}
