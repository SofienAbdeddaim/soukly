package com.soukly.social.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.common.event.SocialEvents;
import com.soukly.common.outbox.OutboxRelay;
import com.soukly.social.instagram.InstagramTokenRefresher;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestRedpanda;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The email "connect again" (SOC-06): a connection that goes to {@code NEEDS_RECONNECT} writes
 * {@code social.connection.needs-reconnect} to the outbox, at most once per connection within 24 hours, and the relay
 * sends it to {@code soukly.social-events}.
 */
class ReconnectNoticeTests extends IntegrationTest {

    @Autowired
    private SocialConnectionRepository connections;

    @Autowired
    private ConnectionHealth health;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private InstagramTokenRefresher refresher;

    @Autowired
    private TransactionTemplate transactions;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID member;

    @BeforeEach
    void member() {
        member = UUID.randomUUID();
    }

    @Test
    void theChangeIsPublishedOnTheSocialEventsWithoutToken() {
        UUID connection = connect(Network.INSTAGRAM);

        transactions.executeWithoutResult(status -> health.needsReconnect(connection));
        relay.relayAll();

        List<ConsumerRecord<String, String>> records = TestRedpanda.readKey(SocialEvents.TOPIC, member.toString(), 1,
                Duration.ofSeconds(20));
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().value()).doesNotContain("token-" + member);
        JsonNode event = json.readTree(records.getFirst().value());
        assertThat(event.get("type").asString()).isEqualTo("social.connection.needs-reconnect");
        assertThat(event.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(event.get("userId").asString()).isEqualTo(member.toString());
        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(event.get("occurredAt").asString()).isNotBlank();
        assertThat(event.get("data").get("network").asString()).isEqualTo("INSTAGRAM");
        assertThat(event.get("data").get("accountName").asString()).isEqualTo("@sfax.auto");
        assertThat(status()).isEqualTo("NEEDS_RECONNECT");
    }

    @Test
    void aSecondChangeWithin24HoursAfterAFailedReconnectionSendsNoSecondEmail() {
        UUID connection = connect(Network.INSTAGRAM);
        transactions.executeWithoutResult(status -> health.needsReconnect(connection));

        clock.advance(Duration.ofHours(3));
        connect(Network.INSTAGRAM);
        transactions.executeWithoutResult(status -> health.needsReconnect(connection));
        assertThat(events()).isEqualTo(1);

        clock.advance(Duration.ofHours(22));
        connect(Network.INSTAGRAM);
        transactions.executeWithoutResult(status -> health.needsReconnect(connection));
        assertThat(events()).isEqualTo(2);
    }

    @Test
    void aConnectionAlreadyToReconnectIsNotNotifiedAgain() {
        UUID connection = connect(Network.FACEBOOK);
        transactions.executeWithoutResult(status -> health.needsReconnect(connection));
        clock.advance(Duration.ofDays(2));

        transactions.executeWithoutResult(status -> health.needsReconnect(connection));

        assertThat(events()).isEqualTo(1);
    }

    @Test
    void anExpiredInstagramTokenAsksToReconnectByEmail() {
        connections.save(member, new NewConnection(Network.INSTAGRAM, "ig-" + member, "person-" + member,
                "@sfax.auto", null, "token-" + member, clock.instant().plus(Duration.ofDays(1))));
        clock.advance(Duration.ofDays(2));

        refresher.refreshExpiring();

        assertThat(status()).isEqualTo("NEEDS_RECONNECT");
        assertThat(events()).isEqualTo(1);
    }

    private UUID connect(Network network) {
        connections.save(member, new NewConnection(network, "account-" + member, "person-" + member,
                network == Network.FACEBOOK ? "Sfax Auto" : "@sfax.auto", null, "token-" + member, null));
        return jdbc.queryForObject("SELECT id FROM social_connections WHERE user_id = ?", UUID.class, member);
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM social_connections WHERE user_id = ?", String.class, member);
    }

    private int events() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class,
                member);
    }
}
