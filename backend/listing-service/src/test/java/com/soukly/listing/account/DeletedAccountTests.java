package com.soukly.listing.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import com.soukly.common.event.ListingEvents;
import com.soukly.common.event.UserEvents;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestListings;
import com.soukly.listing.support.TestRedpanda;

/**
 * Deletion of the listings of a deleted account (AUTH-05): {@code user.deleted} read from {@code soukly.user-events},
 * the rules of ANN-05, deduplication by {@code eventId} and the DLQ.
 */
class DeletedAccountTests extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private UUID owner;

    @BeforeEach
    void seller() {
        owner = UUID.randomUUID();
    }

    @Test
    void everyListingOfTheAccountIsDeletedAndTheDraftErased() throws Exception {
        List<UUID> online = List.of(
                TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1),
                TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 2),
                TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1));
        UUID draft = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        UUID otherSeller = TestListings.published(mockMvc, jdbc, UUID.randomUUID(), TestListings.COMPLETE_FURNITURE,
                1);
        UUID eventId = UUID.randomUUID();

        send(userDeleted(eventId, owner));
        awaitProcessed(eventId);

        for (UUID id : online) {
            assertThat(statusOf(id)).isEqualTo("DELETED");
            assertThat(outboxTypes(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.DELETED);
            mockMvc.perform(get("/listings/" + id)).andExpect(status().isGone());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listings WHERE id = ?", Integer.class, draft)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_photos WHERE listing_id = ?", Integer.class,
                draft)).isZero();
        assertThat(outboxTypes(draft)).isEmpty();
        assertThat(statusOf(otherSeller)).isEqualTo("PUBLISHED");
    }

    @Test
    void theSameEventDeliveredTwiceIsHandledOnce() throws Exception {
        UUID listing = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        UUID eventId = UUID.randomUUID();
        String event = userDeleted(eventId, owner);

        send(event);
        send(event);
        // Same key, so same partition: once the marker is handled, both copies were consumed before it
        UUID marker = UUID.randomUUID();
        send(userDeleted(marker, owner));
        awaitProcessed(marker);

        assertThat(outboxTypes(listing)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.DELETED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId)).isEqualTo(1);
    }

    @Test
    void otherTypesAreIgnoredAndAMalformedMessageGoesToTheDlq() throws Exception {
        UUID listing = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        UUID unknown = UUID.randomUUID();
        send(envelope(unknown, "user.future-type", owner));
        String malformed = "{\"eventId\": \"" + UUID.randomUUID() + "\", \"type\": \"user.deleted\"}";
        kafka.send(UserEvents.TOPIC, owner.toString(), malformed).get();

        List<ConsumerRecord<String, String>> dead = TestRedpanda.read(UserEvents.DLQ_TOPIC,
                record -> malformed.equals(record.value()), received -> !received.isEmpty(), TIMEOUT);
        assertThat(dead).hasSize(1);
        assertThat(statusOf(listing)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                unknown)).isZero();
    }

    private void send(String event) throws Exception {
        kafka.send(UserEvents.TOPIC, owner.toString(), event).get();
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id);
    }

    private List<String> outboxTypes(UUID id) {
        return jdbc.queryForList("SELECT type FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
                String.class, id);
    }

    private static String userDeleted(UUID eventId, UUID userId) {
        return envelope(eventId, UserEvents.DELETED, userId);
    }

    private static String envelope(UUID eventId, String type, UUID userId) {
        return """
                {"eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "%s", "userId": "%s", "data": {}}"""
                .formatted(eventId, type, Instant.now(), userId);
    }
}
