package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.jayway.jsonpath.JsonPath;
import com.soukly.common.event.ListingEvents;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;

/** Hourly task of ANN-03: one reminder 3 days before the expiry, then the expiry. */
class ListingLifecycleTests extends IntegrationTest {

    @Autowired
    private ListingLifecycle lifecycle;

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    @Test
    void aListingThatExpiresInTwoDaysGetsOneReminder() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        TestListings.publish(mockMvc, owner, id);
        Instant expiresAt = clock.instant().plus(Duration.ofDays(60));

        // 4 days before: too early
        clock.advance(Duration.ofDays(56));
        lifecycle.run();
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED);

        clock.advance(Duration.ofDays(2));
        lifecycle.run();

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRING_SOON);
        assertThat(jdbc.queryForObject("SELECT reminder_sent_at FROM listings WHERE id = ?", Timestamp.class, id)
                .toInstant())
                .isEqualTo(clock.instant());
        assertThat(jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id))
                .isEqualTo("PUBLISHED");
        String payload = payload(id, ListingEvents.EXPIRING_SOON);
        assertThat(JsonPath.<String>read(payload, "$.data.title")).isEqualTo("Canapé d'angle en cuir");
        assertThat(Instant.parse(JsonPath.read(payload, "$.data.expiresAt"))).isEqualTo(expiresAt);
        assertThat(JsonPath.<String>read(payload, "$.ownerId")).isEqualTo(owner.toString());

        // No second reminder, the next hour or the next day
        clock.advance(Duration.ofHours(1));
        lifecycle.run();
        clock.advance(Duration.ofDays(1));
        lifecycle.run();
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRING_SOON);
    }

    @Test
    void aListingPastItsExpiryDateExpires() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        TestListings.publish(mockMvc, owner, id);
        Instant expiresAt = clock.instant().plus(Duration.ofDays(60));

        clock.advance(Duration.ofDays(60).plusSeconds(1));
        lifecycle.run();

        assertThat(jdbc.queryForMap("SELECT status, version, expires_at FROM listings WHERE id = ?", id))
                .containsEntry("status", "EXPIRED").containsEntry("version", 3L);
        // Expired first: a listing past its date never gets a reminder
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRED);
        String payload = payload(id, ListingEvents.EXPIRED);
        assertThat(Instant.parse(JsonPath.read(payload, "$.data.expiresAt"))).isEqualTo(expiresAt);
        assertThat(JsonPath.<String>read(payload, "$.data.title")).isEqualTo("Canapé d'angle en cuir");

        lifecycle.run();
        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRED);
    }

    @Test
    void aRenewedListingGetsAReminderAgainBeforeItsNewExpiry() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        TestListings.publish(mockMvc, owner, id);
        clock.advance(Duration.ofDays(58));
        lifecycle.run();
        mockMvc.perform(post("/listings/" + id + "/renew").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + TestJwks.token(owner)))
                .andExpect(status().isOk());

        clock.advance(Duration.ofDays(58));
        lifecycle.run();

        assertThat(events(id)).containsExactly(ListingEvents.PUBLISHED, ListingEvents.EXPIRING_SOON,
                ListingEvents.RENEWED, ListingEvents.EXPIRING_SOON);
    }

    @Test
    void draftsNeverExpire() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        clock.advance(Duration.ofDays(400));
        lifecycle.run();
        assertThat(jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(events(id)).isEmpty();
    }

    private List<String> events(UUID id) {
        return jdbc.queryForList("SELECT type FROM outbox_events WHERE aggregate_id = ? ORDER BY sequence",
                String.class, id);
    }

    private String payload(UUID id, String type) {
        return jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND type = ?",
                String.class, id, type);
    }
}
