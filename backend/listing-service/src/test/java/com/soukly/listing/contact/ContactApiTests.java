package com.soukly.listing.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.listing.listing.ListingLifecycle;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;
import com.soukly.listing.support.TestUserService;

/** "Show the number" of CATA-03: members only, not the owner, published listings only, 30 listings per 24 hours. */
class ContactApiTests extends IntegrationTest {

    @Autowired
    private ListingLifecycle lifecycle;

    private UUID owner;
    private UUID viewer;

    @BeforeEach
    void newAccounts() {
        owner = UUID.randomUUID();
        viewer = UUID.randomUUID();
    }

    @Test
    void aMemberIsShownTheNumberNeverCachedAndTheRevealIsRecorded() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 1);
        Instant now = clock.instant();

        contact(id, viewer)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.phone").value(TestUserService.PHONE))
                .andExpect(jsonPath("$.length()").value(1));

        assertThat(jdbc.queryForList("SELECT viewer_id FROM contact_reveals WHERE listing_id = ?", UUID.class, id))
                .containsExactly(viewer);
        assertThat(jdbc.queryForObject("SELECT revealed_at FROM contact_reveals WHERE listing_id = ?",
                Timestamp.class, id).toInstant()).isEqualTo(now);
        assertThat(TestUserService.calls(owner, "contact")).isEqualTo(1);
        // Never kept: a second request reads it again, and is recorded again
        contact(id, viewer).andExpect(status().isOk());
        assertThat(TestUserService.calls(owner, "contact")).isEqualTo(2);
        assertThat(reveals(id)).isEqualTo(2);
    }

    @Test
    void withoutATokenTheRequestIsRefused() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        mockMvc.perform(post("/listings/" + id + "/contact"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(reveals(id)).isZero();
    }

    @Test
    void theOwnerGetsAConflict() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        contact(id, owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:own-listing"));
        assertThat(reveals(id)).isZero();
    }

    @Test
    void onlyAPublishedListingHasAContact() throws Exception {
        UUID draft = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        UUID expired = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 1);
        clock.advance(Duration.ofDays(61));
        lifecycle.run();

        contact(draft, viewer).andExpect(status().isNotFound());
        contact(expired, viewer)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.categoryCode").value("vehicles.cars"))
                .andExpect(jsonPath("$.phone").doesNotExist());
        contact(UUID.randomUUID(), viewer).andExpect(status().isNotFound());
        mockMvc.perform(post("/listings/42/contact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(viewer)))
                .andExpect(status().isNotFound());
        assertThat(TestUserService.calls(owner, "contact")).isZero();
    }

    @Test
    void aSellerWhoseAccountIsNoLongerActiveIsGone() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        TestUserService.close(owner);

        contact(id, viewer).andExpect(status().isGone());
        assertThat(reveals(id)).isZero();
    }

    @Test
    void after30DistinctListingsIn24HoursOnlyTheListingsAlreadySeenAnswer() throws Exception {
        UUID seen = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        UUID other = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 1);
        Instant now = clock.instant();
        // 29 other listings seen 23 hours ago, then the 30th now
        for (int i = 0; i < 29; i++) {
            UUID listing = TestListings.draft(mockMvc, UUID.randomUUID(), "{ \"categoryCode\": \"home.furniture\" }");
            jdbc.update("INSERT INTO contact_reveals (listing_id, viewer_id, revealed_at) VALUES (?, ?, ?)",
                    listing, viewer, Timestamp.from(now.minus(Duration.ofHours(23))));
        }
        contact(seen, viewer).andExpect(status().isOk());

        contact(other, viewer)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:contact-limit-reached"))
                .andExpect(jsonPath("$.phone").doesNotExist());
        assertThat(reveals(other)).isZero();
        // Seeing a number again does not count
        contact(seen, viewer).andExpect(status().isOk());
        // Another member has their own limit
        contact(other, UUID.randomUUID()).andExpect(status().isOk());

        // The 29 listings leave the window
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        contact(other, viewer).andExpect(status().isOk());
    }

    private ResultActions contact(UUID id, UUID member) throws Exception {
        return mockMvc.perform(post("/listings/" + id + "/contact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(member)));
    }

    private Integer reveals(UUID listingId) {
        return jdbc.queryForObject("SELECT count(*) FROM contact_reveals WHERE listing_id = ?", Integer.class,
                listingId);
    }
}
