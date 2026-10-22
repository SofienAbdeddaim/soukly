package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Where a draft is shared (SOC-03): {@code shareTo} and {@code shareLanguage} change while it is a draft, are copied
 * into {@code listing.published}, then are locked.
 */
class ShareChoiceApiTests extends IntegrationTest {

    @Autowired
    private JsonMapper json;

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    @Test
    void aNewDraftHasNoChoiceYet() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        read(id)
                .andExpect(jsonPath("$.shareTo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.shareLanguage").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void theChoiceIsSavedAndReadBack() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        update(id, "\"1\"", "{\"shareTo\":[\"FACEBOOK\"],\"shareLanguage\":\"fr\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareTo[0]").value("FACEBOOK"))
                .andExpect(jsonPath("$.shareTo.length()").value(1))
                .andExpect(jsonPath("$.shareLanguage").value("fr"));
        // Instagram ticked later, the order of the networks is always the same
        update(id, "\"2\"", "{\"shareTo\":[\"INSTAGRAM\",\"FACEBOOK\"],\"shareLanguage\":\"ar\"}")
                .andExpect(status().isOk());

        read(id)
                .andExpect(jsonPath("$.shareTo[0]").value("FACEBOOK"))
                .andExpect(jsonPath("$.shareTo[1]").value("INSTAGRAM"))
                .andExpect(jsonPath("$.shareLanguage").value("ar"));
        assertThat(jdbc.queryForObject("SELECT array_to_string(share_to, ',') FROM listings WHERE id = ?",
                String.class, id)).isEqualTo("FACEBOOK,INSTAGRAM");
    }

    @Test
    void noNetworkIsAChoiceToo() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        update(id, "\"1\"", "{\"shareTo\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$.shareTo").isEmpty());
        // Another change of the draft keeps the choice
        update(id, "\"2\"", "{\"title\":\"Canapé d'angle en cuir noir\"}")
                .andExpect(jsonPath("$.shareTo").isEmpty());
    }

    @Test
    void anUnknownNetworkADuplicateOrAnUnknownLanguageIsRefused() throws Exception {
        UUID id = TestListings.draft(mockMvc, owner, TestListings.COMPLETE_FURNITURE);

        update(id, "\"1\"", "{\"shareTo\":[\"TIKTOK\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shareTo"))
                .andExpect(jsonPath("$.errors[0].code").value("share-to.invalid"));
        update(id, "\"1\"", "{\"shareTo\":[\"FACEBOOK\",\"FACEBOOK\"]}")
                .andExpect(jsonPath("$.errors[0].code").value("share-to.duplicate"));
        update(id, "\"1\"", "{\"shareTo\":\"FACEBOOK\"}")
                .andExpect(jsonPath("$.errors[0].code").value("share-to.invalid"));
        update(id, "\"1\"", "{\"shareLanguage\":\"de\"}")
                .andExpect(jsonPath("$.errors[0].field").value("shareLanguage"))
                .andExpect(jsonPath("$.errors[0].code").value("share-language.invalid"));
        read(id).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void thePublicationCopiesTheChoiceIntoTheEvent() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 2);
        update(id, "\"1\"", "{\"shareTo\":[\"FACEBOOK\",\"INSTAGRAM\"],\"shareLanguage\":\"fr\"}")
                .andExpect(status().isOk());

        TestListings.publish(mockMvc, owner, id);

        JsonNode data = json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND type = 'listing.published'",
                String.class, id)).get("data");
        assertThat(data.get("shareTo").toString()).isEqualTo("[\"FACEBOOK\",\"INSTAGRAM\"]");
        assertThat(data.get("shareLanguage").asString()).isEqualTo("fr");
    }

    @Test
    void aNetworkWithoutLanguageIsSharedInFrench() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);
        update(id, "\"1\"", "{\"shareTo\":[\"INSTAGRAM\"]}").andExpect(status().isOk());

        TestListings.publish(mockMvc, owner, id);

        JsonNode data = json.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE aggregate_id = ? AND type = 'listing.published'",
                String.class, id)).get("data");
        assertThat(data.get("shareTo").toString()).isEqualTo("[\"INSTAGRAM\"]");
        assertThat(data.get("shareLanguage").asString()).isEqualTo("fr");
    }

    @Test
    void theChoiceIsLockedOnceTheListingIsPublished() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        update(id, "\"2\"", "{\"shareTo\":[\"FACEBOOK\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:share-locked"));
        update(id, "\"2\"", "{\"shareLanguage\":\"ar\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:share-locked"));
        // The rest of the listing still changes (ANN-05)
        update(id, "\"2\"", "{\"title\":\"Canapé d'angle en cuir noir\"}").andExpect(status().isOk());
        read(id).andExpect(jsonPath("$.shareTo").value(org.hamcrest.Matchers.nullValue()));
    }

    private ResultActions update(UUID id, String ifMatch, String body) throws Exception {
        return mockMvc.perform(patch("/listings/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType("application/merge-patch+json")
                .content(body));
    }

    private ResultActions read(UUID id) throws Exception {
        return mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner)))
                .andExpect(status().isOk());
    }
}
