package com.soukly.listing.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.Collator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.soukly.listing.support.IntegrationTest;

class LocationsApiTests extends IntegrationTest {

    @Test
    void governoratesAreTheTwentyFourWithTheirThreeLabels() throws Exception {
        mockMvc.perform(get("/locations/governorates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[*].code").value(contains("tunis", "ariana", "ben-arous", "manouba", "nabeul",
                        "zaghouan", "bizerte", "beja", "jendouba", "kef", "siliana", "sousse", "monastir", "mahdia",
                        "sfax", "kairouan", "kasserine", "sidi-bouzid", "gabes", "medenine", "tataouine", "gafsa",
                        "tozeur", "kebili")))
                .andExpect(jsonPath("$[14].label.fr").value("Sfax"))
                .andExpect(jsonPath("$[14].label.ar").value("صفاقس"))
                .andExpect(jsonPath("$[14].label.en").value("Sfax"))
                .andExpect(jsonPath("$[9].label.fr").value("Le Kef"))
                .andExpect(jsonPath("$[9].label.en").value("El Kef"));
    }

    @Test
    void unknownGovernorateIsANotFoundProblem() throws Exception {
        mockMvc.perform(get("/locations/governorates/atlantis/delegations"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:governorate-not-found"))
                .andExpect(jsonPath("$.instance").value("/locations/governorates/atlantis/delegations"));
    }

    @Test
    void sfaxDelegationsAreSortedByLabel() throws Exception {
        String body = mockMvc.perform(get("/locations/governorates/sfax/delegations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> codes = JsonPath.read(body, "$[*].code");
        List<String> labels = JsonPath.read(body, "$[*].label.fr");
        assertThat(codes).hasSize(16).allSatisfy(code -> assertThat(code).startsWith("sfax."))
                .contains("sfax.sfax-ville", "sfax.sakiet-ezzit", "sfax.kerkenah");
        assertThat(labels).isSortedAccordingTo(Collator.getInstance(Locale.FRENCH));
        assertThat(labels.getFirst()).isEqualTo("Agareb");
        mockMvc.perform(get("/locations/governorates/sfax/delegations"))
                .andExpect(jsonPath("$[0].label.ar").value("عقارب"))
                .andExpect(jsonPath("$[0].label.en").value("Agareb"));
    }

    @Test
    void everyGovernorateHasAtLeastOneDelegation() {
        Integer withoutDelegation = jdbc.queryForObject("""
                SELECT count(*) FROM governorates g
                WHERE NOT EXISTS (SELECT 1 FROM delegations d WHERE d.governorate_code = g.code)""", Integer.class);
        assertThat(withoutDelegation).isZero();
    }

    @Test
    void delegationCodesFollowTheirGovernorateAndTheInsCodes() {
        Integer mismatched = jdbc.queryForObject("""
                SELECT count(*) FROM delegations d JOIN governorates g ON g.code = d.governorate_code
                WHERE d.ins_code NOT LIKE g.ins_code || '__'""", Integer.class);
        assertThat(mismatched).isZero();
    }
}
