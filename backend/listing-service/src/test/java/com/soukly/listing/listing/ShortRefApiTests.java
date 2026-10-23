package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Short reference of a listing (SOC-05): given at the creation, shown on the listing page, carried by
 * {@code listing.published} and resolved by {@code GET /listings/by-ref/{ref}} in any case.
 */
class ShortRefApiTests extends IntegrationTest {

    private static final String PATTERN = "[2-9A-HJ-NP-Z]{8}";

    @Autowired
    private JsonMapper json;

    @Autowired
    private ListingRepository repository;

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    @Test
    void aListingHasItsReferenceFromItsCreation() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        String ref = shortRef(id);
        assertThat(ref).matches(PATTERN);
        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortRef").value(ref));
    }

    @Test
    void thePublishedListingShowsItsReferenceAndTheEventCarriesIt() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        String ref = shortRef(id);

        mockMvc.perform(get("/listings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortRef").value(ref));
        assertThat(publishedData(id).get("shortRef").asString()).isEqualTo(ref);
    }

    @Test
    void aReferenceTypedInLowerCaseLeadsToItsListingWithoutToken() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        String ref = shortRef(id);

        byRef(ref.toLowerCase(Locale.ROOT))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.length()").value(1));
        byRef(ref).andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void anExpiredOrDeactivatedListingStillHasItsPage() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        jdbc.update("UPDATE listings SET status = 'INACTIVE' WHERE id = ?", id);

        byRef(shortRef(id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void anUnknownReferenceADraftOrADeletedListingIsNotFound() throws Exception {
        UUID draft = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);
        UUID deleted = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        mockMvc.perform(delete("/listings/" + deleted)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner)))
                .andExpect(status().isNoContent());

        byRef(shortRef(draft)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        byRef(shortRef(deleted)).andExpect(status().isNotFound());
        byRef("ZZZZZZZZ").andExpect(status().isNotFound());
        byRef("not-a-ref").andExpect(status().isNotFound());
        byRef("0O1I0O1I").andExpect(status().isNotFound());
    }

    @Test
    void aListingWithoutReferenceGetsOneWhenItIsPublished() throws Exception {
        // Created by the version before SOC-05, which leaves the column NULL
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        jdbc.update("UPDATE listings SET short_ref = NULL WHERE id = ?", id);

        TestListings.publish(mockMvc, owner, id);

        String ref = shortRef(id);
        assertThat(ref).matches(PATTERN);
        assertThat(publishedData(id).get("shortRef").asString()).isEqualTo(ref);
    }

    @Test
    void aReferenceTakenAlreadyIsNotInsertedSoThatAnotherOneIsDrawn() throws Exception {
        UUID existing = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);
        Listing taken = repository.find(existing).orElseThrow();
        Listing other = new Listing(UUID.randomUUID(), owner, ListingStatus.DRAFT, taken.content(), 1,
                taken.createdAt(), taken.createdAt(), null, null, null, null, taken.shortRef());

        assertThat(repository.insert(other)).isFalse();
        assertThat(repository.find(other.id())).isEmpty();
        assertThat(repository.insert(other.withShortRef(ShortRefs.generate()))).isTrue();
    }

    @Test
    void twoListingsNeverShareAReference() {
        Integer duplicates = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT short_ref FROM listings GROUP BY short_ref HAVING count(*) > 1) d "
                        + "WHERE short_ref IS NOT NULL", Integer.class);
        assertThat(duplicates).isZero();
    }

    private String shortRef(UUID id) {
        return jdbc.queryForObject("SELECT short_ref FROM listings WHERE id = ?", String.class, id);
    }

    private JsonNode publishedData(UUID id) {
        return json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND type = 'listing.published'",
                String.class, id)).get("data");
    }

    private ResultActions byRef(String ref) throws Exception {
        return mockMvc.perform(get("/listings/by-ref/" + ref));
    }
}
