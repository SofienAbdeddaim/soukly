package com.soukly.social.caption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestJwks;
import com.soukly.social.support.TestReferentials;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@code POST /social/caption-preview} (SOC-03) with the labels of the simulated referentials of listing-service. */
class CaptionPreviewApiTests extends IntegrationTest {

    private static final String URL = "https://soukly.example.test/fr/listings/8f3c2a10";

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private ReferentialLabels labels;

    @BeforeEach
    void freshLabels() {
        labels.clear();
    }

    @AfterEach
    void referentialsUp() {
        TestReferentials.down(false);
    }

    @Test
    void theFrenchTextOfACarInSfax() throws Exception {
        JsonNode caption = preview(car("fr")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8).transform(json::readTree);

        String text = plain(caption.get("text").asString());
        assertThat(text).isEqualTo("""
                Peugeot 208 diesel, première main
                32 000 DT · Négociable
                Sfax, Sakiet Ezzit

                Première main, carnet d'entretien complet chez le concessionnaire.

                Voir l'annonce : https://soukly.example.test/fr/listings/8f3c2a10
                #Soukly #Sfax #Voitures""");
        assertThat(caption.get("hashtags").toString()).isEqualTo("[\"#Soukly\",\"#Sfax\",\"#Voitures\"]");
    }

    @Test
    void theArabicTextHasArabicLabelsAndPrice() throws Exception {
        String text = preview(car("ar")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8).transform(json::readTree)
                .get("text").asString();

        assertThat(plain(text)).contains("32.000 د.ت. · قابل للتفاوض", "صفاقس، ساقية الزيت", "شاهد الإعلان:",
                "#Soukly #صفاقس #سيارات");
    }

    @Test
    void theLabelsAreReadOnceThenKeptInMemory() throws Exception {
        preview(car("fr")).andExpect(status().isOk());
        int calls = TestReferentials.calls();

        preview(car("en")).andExpect(status().isOk()).andExpect(jsonPath("$.hashtags[2]").value("#Cars"));
        preview(car("ar")).andExpect(status().isOk());

        assertThat(TestReferentials.calls()).isEqualTo(calls);
    }

    @Test
    void aServiceOnQuoteHasNoPrice() throws Exception {
        Map<String, Object> body = car("fr");
        body.put("categoryCode", "services.tutoring");
        body.put("priceMode", "ON_QUOTE");
        body.put("priceTnd", null);
        body.put("governorateCode", "ben-arous");
        body.put("delegationCode", null);

        preview(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("\nSur devis\nBen Arous\n")))
                .andExpect(jsonPath("$.hashtags[1]").value("#BenArous"))
                .andExpect(jsonPath("$.hashtags[2]").value("#CoursEtSoutienScolaire"));
    }

    @Test
    void unknownCodesAndInvalidMembersAreRefused() throws Exception {
        Map<String, Object> body = car("de");
        body.put("categoryCode", "vehicles.boats");
        body.put("publicUrl", "javascript:alert(1)");
        body.put("priceMode", "FREE");

        preview(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.contains("language",
                        "publicUrl", "priceMode")));

        Map<String, Object> places = car("fr");
        places.put("categoryCode", "vehicles.boats");
        places.put("delegationCode", "tunis.bab-bhar");
        preview(places)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].code").value(org.hamcrest.Matchers.contains("category.unknown",
                        "delegation.unknown")));
    }

    @Test
    void listingServiceDownMakesTheTextUnavailable() throws Exception {
        TestReferentials.down(true);

        preview(car("fr"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:caption-unavailable"));
    }

    @Test
    void anAccessTokenIsRequired() throws Exception {
        mockMvc.perform(post("/social/caption-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(car("fr"))))
                .andExpect(status().isUnauthorized());
    }

    private static Map<String, Object> car(String language) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Peugeot 208 diesel, première main");
        body.put("description", "Première main, carnet d'entretien complet chez le concessionnaire.");
        body.put("priceMode", "FIXED");
        body.put("priceTnd", 32000);
        body.put("negotiable", true);
        body.put("categoryCode", "vehicles.cars");
        body.put("governorateCode", "sfax");
        body.put("delegationCode", "sfax.sakiet-ezzit");
        body.put("publicUrl", URL);
        body.put("language", language);
        return body;
    }

    private ResultActions preview(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/social/caption-preview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private static String plain(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }
}
