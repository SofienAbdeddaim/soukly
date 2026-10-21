package com.soukly.listing.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;

class CategoriesApiTests extends IntegrationTest {

    @Test
    void categoriesAreTheTenFamiliesInDisplayOrderWithTheirSubcategories() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[*].code").value(contains("vehicles", "real-estate", "jobs", "services",
                        "home", "multimedia", "fashion", "leisure", "animals", "misc")))
                .andExpect(jsonPath("$[*].subcategories[*]", hasSize(44)))
                .andExpect(jsonPath("$[0].label.fr").value("Véhicules"))
                .andExpect(jsonPath("$[0].subcategories[*].code").value(contains("vehicles.cars",
                        "vehicles.motorcycles", "vehicles.utility", "vehicles.parts")))
                .andExpect(jsonPath("$[0].subcategories[0].label.fr").value("Voitures"))
                .andExpect(jsonPath("$[1].subcategories[*].code").value(contains("real-estate.apartments",
                        "real-estate.houses", "real-estate.land", "real-estate.commercial", "real-estate.roommates",
                        "real-estate.holiday-rentals")));
    }

    @Test
    void everyCategoryHasACodeAndThreeLabels() throws Exception {
        String body = mockMvc.perform(get("/categories")).andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> families = JsonPath.read(body, "$[*]");
        List<Map<String, Object>> subcategories = JsonPath.read(body, "$[*].subcategories[*]");
        assertThat(families).hasSize(10);
        assertThat(subcategories).hasSize(44);
        for (Map<String, Object> category : concat(families, subcategories)) {
            assertThat((String) category.get("code")).matches("[a-z0-9-]+(\\.[a-z0-9-]+)?");
            assertThat(category.get("label")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsOnlyKeys("fr", "ar", "en")
                    .allSatisfy((language, label) -> assertThat((String) label).isNotBlank());
        }
    }

    @Test
    void carsHaveTheirSixAttributesWithTypeConstraintsAndLabels() throws Exception {
        int nextYear = Year.now().getValue() + 1;
        mockMvc.perform(get("/categories/vehicles.cars/attributes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("brand", "model", "year", "mileage-km", "fuel",
                        "gearbox")))
                // brand
                .andExpect(jsonPath("$[0].type").value("TEXT"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].minLength").value(2))
                .andExpect(jsonPath("$[0].maxLength").value(40))
                .andExpect(jsonPath("$[0].filterable").value(true))
                .andExpect(jsonPath("$[0].label.fr").value("Marque"))
                .andExpect(jsonPath("$[0].minValue").doesNotExist())
                .andExpect(jsonPath("$[0].values").doesNotExist())
                // model
                .andExpect(jsonPath("$[1].required").value(false))
                .andExpect(jsonPath("$[1].minLength").value(1))
                .andExpect(jsonPath("$[1].filterable").value(false))
                // year: maximum resolved to the current year + 1
                .andExpect(jsonPath("$[2].type").value("INTEGER"))
                .andExpect(jsonPath("$[2].minValue").value(1950))
                .andExpect(jsonPath("$[2].maxValue").value(nextYear))
                // mileage-km
                .andExpect(jsonPath("$[3].minValue").value(0))
                .andExpect(jsonPath("$[3].maxValue").value(2_000_000))
                // fuel
                .andExpect(jsonPath("$[4].type").value("ENUM"))
                .andExpect(jsonPath("$[4].values[*].code").value(contains("petrol", "diesel", "hybrid",
                        "electric", "lpg")))
                .andExpect(jsonPath("$[4].values[0].label.fr").value("Essence"))
                .andExpect(jsonPath("$[4].values[4].label.fr").value("GPL"))
                // gearbox
                .andExpect(jsonPath("$[5].values[*].code").value(contains("manual", "automatic")))
                .andExpect(jsonPath("$[5].values[1].label.fr").value("Automatique"));
    }

    @Test
    void realEstateAttributesFollowTheirSubcategory() throws Exception {
        mockMvc.perform(get("/categories/real-estate.apartments/attributes"))
                .andExpect(jsonPath("$[*].code").value(contains("transaction", "surface-m2", "rooms", "furnished")))
                .andExpect(jsonPath("$[0].values[*].code").value(contains("sale", "rent")))
                .andExpect(jsonPath("$[1].minValue").value(1))
                .andExpect(jsonPath("$[1].maxValue").value(100_000))
                .andExpect(jsonPath("$[2].minValue").value(0))
                .andExpect(jsonPath("$[2].maxValue").value(20))
                .andExpect(jsonPath("$[3].type").value("BOOLEAN"))
                .andExpect(jsonPath("$[3].required").value(false));
        mockMvc.perform(get("/categories/real-estate.land/attributes"))
                .andExpect(jsonPath("$[*].code").value(contains("transaction", "surface-m2")));
        mockMvc.perform(get("/categories/real-estate.roommates/attributes"))
                .andExpect(jsonPath("$[*].code").value(contains("furnished")));
        mockMvc.perform(get("/categories/real-estate.holiday-rentals/attributes"))
                .andExpect(jsonPath("$[*].code").value(contains("surface-m2", "rooms", "furnished")));
        mockMvc.perform(get("/categories/vehicles.motorcycles/attributes"))
                .andExpect(jsonPath("$[*].code").value(contains("year", "mileage-km")));
        mockMvc.perform(get("/categories/vehicles.utility/attributes"))
                .andExpect(jsonPath("$[*].code").value(contains("year", "mileage-km", "fuel", "gearbox")));
    }

    @Test
    void categoryWithoutSpecificAttributesHasAnEmptyList() throws Exception {
        mockMvc.perform(get("/categories/fashion.shoes/attributes"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void unknownCategoryIsANotFoundProblem() throws Exception {
        mockMvc.perform(get("/categories/foo.bar/attributes").header("X-Request-Id", "req-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:category-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/categories/foo.bar/attributes"))
                .andExpect(jsonPath("$.requestId").value("req-1"));
    }

    @Test
    void familyHasNoAttributesOfItsOwn() throws Exception {
        mockMvc.perform(get("/categories/vehicles/attributes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:category-not-found"));
    }

    /** Even for a signed-in account (without a token, the resource server answers 401 first, like the gateway). */
    @Test
    void writeMethodsAreNotExposed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(UUID.randomUUID())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static List<Map<String, Object>> concat(List<Map<String, Object>> first, List<Map<String, Object>> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
