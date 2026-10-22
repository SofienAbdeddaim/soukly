package com.soukly.social.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.common.event.UserEvents;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGraph;
import com.soukly.social.support.TestJwks;
import com.soukly.social.support.TestRedpanda;

import tools.jackson.databind.json.JsonMapper;

/**
 * Deletion of the connections of a deleted account (SOC-01): {@code user.deleted} read from
 * {@code soukly.user-events}, revocation at Meta, deduplication by {@code eventId} and the DLQ.
 */
class DeletedAccountTests extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID member;

    @BeforeEach
    void member() {
        member = UUID.randomUUID();
    }

    @Test
    void theConnectionIsDeletedAndThePermissionsRevoked() throws Exception {
        String code = connectPage(member);
        // A connection started and not finished is forgotten too
        startConnection(member);
        UUID other = UUID.randomUUID();
        connectPage(other);
        UUID eventId = UUID.randomUUID();

        send(userDeleted(eventId, member));
        awaitProcessed(eventId);

        assertThat(count("social_connections", member)).isZero();
        assertThat(count("oauth_states", member)).isZero();
        assertThat(count("social_connections", other)).isEqualTo(1);
        await().atMost(TIMEOUT).until(() -> TestGraph.revoked().contains(TestGraph.userId(code)));
    }

    @Test
    void theSameEventDeliveredTwiceIsHandledOnce() throws Exception {
        connectPage(member);
        UUID eventId = UUID.randomUUID();
        String event = userDeleted(eventId, member);

        send(event);
        send(event);
        // Same key, so same partition: once the marker is handled, both copies were consumed before it
        UUID marker = UUID.randomUUID();
        send(userDeleted(marker, member));
        awaitProcessed(marker);

        assertThat(count("social_connections", member)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId)).isEqualTo(1);
    }

    @Test
    void otherTypesAreIgnoredAndAMalformedMessageGoesToTheDlq() throws Exception {
        connectPage(member);
        UUID unknown = UUID.randomUUID();
        send(envelope(unknown, "user.future-type", member));
        String malformed = "{\"eventId\": \"" + UUID.randomUUID() + "\", \"type\": \"user.deleted\", \"social\": 1}";
        kafka.send(UserEvents.TOPIC, member.toString(), malformed).get();

        List<ConsumerRecord<String, String>> dead = TestRedpanda.read(UserEvents.DLQ_TOPIC,
                record -> malformed.equals(record.value()), received -> !received.isEmpty(), TIMEOUT);
        assertThat(dead).hasSize(1);
        assertThat(count("social_connections", member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                unknown)).isZero();
    }

    /** Connects a single Page through the API; returns the OAuth code. */
    private String connectPage(UUID userId) throws Exception {
        String state = startConnection(userId);
        String code = TestGraph.person(TestGraph.ALL_PERMISSIONS,
                List.of(new TestGraph.Page("2001", "Sousse Immo", "Real estate")));
        mockMvc.perform(post("/social/facebook/callback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk());
        return code;
    }

    private String startConnection(UUID userId) throws Exception {
        String body = mockMvc.perform(post("/social/facebook/connect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UriComponentsBuilder.fromUriString(json.readTree(body).get("authorizeUrl").asString()).build()
                .getQueryParams().getFirst("state");
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private void send(String event) throws Exception {
        kafka.send(UserEvents.TOPIC, member.toString(), event).get();
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId) == 1);
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
