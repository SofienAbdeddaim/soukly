package com.soukly.listing.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;

/**
 * Status of listings for social-publisher (SOC-06): {@code GET /internal/listings/{id}/status} and
 * {@code POST /internal/listings/statuses}, behind the shared token of {@code X-Internal-Token}.
 */
class InternalListingApiTests extends IntegrationTest {

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    @Test
    void aPublishedListingIsOnline() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        mockMvc.perform(get("/internal/listings/" + id + "/status").header(InternalTokenFilter.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.online").value(true));
    }

    @Test
    void aListingPastItsExpiryOrDeactivatedIsNotOnline() throws Exception {
        UUID late = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        jdbc.update("UPDATE listings SET expires_at = ? WHERE id = ?", Timestamp.from(clock.instant().minusSeconds(60)),
                late);
        UUID inactive = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        jdbc.update("UPDATE listings SET status = 'INACTIVE' WHERE id = ?", inactive);
        UUID draft = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        mockMvc.perform(get("/internal/listings/" + late + "/status")
                .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.online").value(false));
        statuses("\"" + inactive + "\", \"" + draft + "\"")
                .andExpect(jsonPath("$.statuses.length()").value(2))
                .andExpect(jsonPath("$.statuses[?(@.id == '" + inactive + "')].status").value("INACTIVE"))
                .andExpect(jsonPath("$.statuses[?(@.id == '" + draft + "')].online").value(false));
    }

    @Test
    void anUnknownListingIsNotFoundAndLeftOutOfABatch() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        mockMvc.perform(get("/internal/listings/" + UUID.randomUUID() + "/status")
                .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNotFound());
        statuses("\"" + id + "\", \"" + UUID.randomUUID() + "\"")
                .andExpect(jsonPath("$.statuses.length()").value(1))
                .andExpect(jsonPath("$.statuses[0].online").value(true));
    }

    @Test
    void aBatchTakesFiftyListingsAtMost() throws Exception {
        String ids = IntStream.range(0, 51).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/internal/listings/statuses").header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [" + ids + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInternalEndpointNeedsTheSharedTokenNotAnAccessToken() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        mockMvc.perform(get("/internal/listings/" + id + "/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/listings/" + id + "/status").header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/listings/" + id + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions statuses(String ids) throws Exception {
        return mockMvc.perform(post("/internal/listings/statuses").header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [" + ids + "]}"))
                .andExpect(status().isOk());
    }
}
