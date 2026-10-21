package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Year;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;

class DraftListingApiTests extends IntegrationTest {

    private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

    private static final String CAR_DETAILS = """
            { "title": "  Peugeot   208 Allure  ", "description": "Très bon état, première main, carnet d'entretien.",
              "priceMode": "FIXED", "price": 32000, "negotiable": true, "governorateCode": "sfax",
              "attributes": { "brand": "Peugeot", "year": 2019, "mileage-km": 62000, "fuel": "diesel",
                              "gearbox": "manual" } }""";

    private UUID owner;

    @BeforeEach
    void newAccount() {
        owner = UUID.randomUUID();
    }

    @Test
    void aDraftIsCreatedFromItsCategoryThenCompletedStepByStep() throws Exception {
        ResultActions created = create(owner, "{ \"categoryCode\": \"vehicles.cars\" }")
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.kind").value("SALE"))
                .andExpect(jsonPath("$.categoryCode").value("vehicles.cars"))
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.negotiable").value(false))
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.missingFields").value(contains("title", "description", "priceMode",
                        "governorateCode", "attributes.brand", "attributes.year", "attributes.mileage-km",
                        "attributes.fuel", "attributes.gearbox")))
                .andExpect(jsonPath("$.warnings", empty()));
        String id = id(created);
        created.andExpect(header().string(HttpHeaders.LOCATION, "listings/" + id));

        update(owner, id, "\"1\"", CAR_DETAILS)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.version").value(2))
                // Trimmed and inner spaces collapsed
                .andExpect(jsonPath("$.title").value("Peugeot 208 Allure"))
                .andExpect(jsonPath("$.price").value(32000))
                .andExpect(jsonPath("$.negotiable").value(true))
                .andExpect(jsonPath("$.governorateCode").value("sfax"))
                .andExpect(jsonPath("$.delegationCode").value(nullValue()))
                .andExpect(jsonPath("$.attributes.year").value(2019))
                // Photos join the missing fields with ANN-02.
                .andExpect(jsonPath("$.missingFields", empty()));

        // The owner finds it as left, with the attributes in display order.
        String body = mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.title").value("Peugeot 208 Allure"))
                .andExpect(jsonPath("$.missingFields", empty()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).containsSubsequence("\"brand\"", "\"year\"", "\"mileage-km\"", "\"fuel\"", "\"gearbox\"");
        assertThat(jdbc.queryForObject("SELECT price_tnd FROM listings WHERE id = ?::uuid", Long.class, id))
                .isEqualTo(32000L);
    }

    @Test
    void theLocationResolvesAgainstTheGatewayPathAndTheServicePath() throws Exception {
        ResultActions created = create(owner, "{ \"categoryCode\": \"home.furniture\" }");
        String id = id(created);
        URI location = URI.create(created.andReturn().getResponse().getHeader(HttpHeaders.LOCATION));

        assertThat(URI.create("https://soukly.example/api/listings").resolve(location))
                .hasPath("/api/listings/" + id);
        assertThat(URI.create("http://listing-service:8082/listings").resolve(location)).hasPath("/listings/" + id);
    }

    @Test
    void otherFieldsMayBeSentAtCreation() throws Exception {
        create(owner, """
                { "categoryCode": "home.furniture", "title": "Canapé d'angle", "governorateCode": "tunis" }""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Canapé d'angle"))
                .andExpect(jsonPath("$.missingFields").value(contains("description", "priceMode")));
    }

    @Test
    void theCategoryIsRequiredAtCreation() throws Exception {
        create(owner, "{ \"title\": \"Canapé d'angle\" }")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("categoryCode"))
                .andExpect(jsonPath("$.errors[0].code").value("category.required"));
    }

    @Test
    void aFamilyOrAnUnknownCodeIsNotACategory() throws Exception {
        for (String code : new String[] { "\"vehicles\"", "\"vehicles.rockets\"", "42" }) {
            create(owner, "{ \"categoryCode\": " + code + " }")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("categoryCode"))
                    .andExpect(jsonPath("$.errors[0].code").value("category.unknown"));
        }
    }

    @Test
    void onQuoteIsAllowedForServices() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"services.home-repair\" }")
                .andExpect(jsonPath("$.kind").value("SERVICE")));

        update(owner, id, "\"1\"", "{ \"priceMode\": \"ON_QUOTE\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMode").value("ON_QUOTE"))
                .andExpect(jsonPath("$.missingFields").value(contains("title", "description", "governorateCode")));
    }

    @Test
    void onQuoteIsAllowedForJobs() throws Exception {
        create(owner, "{ \"categoryCode\": \"jobs.offers\", \"priceMode\": \"ON_QUOTE\" }")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SERVICE"));
    }

    @Test
    void onQuoteIsRefusedForASale() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        update(owner, id, "\"1\"", "{ \"priceMode\": \"ON_QUOTE\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("priceMode"))
                .andExpect(jsonPath("$.errors[0].code").value("price-mode.not-allowed"));
    }

    @Test
    void aFixedPriceIsRequiredUntilItIsSent() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\", \"priceMode\": \"FIXED\" }")
                .andExpect(jsonPath("$.missingFields").value(contains("title", "description", "price",
                        "governorateCode"))));

        update(owner, id, "\"1\"", "{ \"price\": 450 }")
                .andExpect(jsonPath("$.missingFields").value(contains("title", "description", "governorateCode")));
    }

    @Test
    void aPriceIsRefusedOnQuote() throws Exception {
        create(owner, "{ \"categoryCode\": \"services.home-repair\", \"priceMode\": \"ON_QUOTE\", \"price\": 50 }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("price"))
                .andExpect(jsonPath("$.errors[0].code").value("price.not-allowed"));
    }

    @Test
    void thePriceIsAWholeNumberOfDinarsInRange() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        for (String price : new String[] { "32000.5", "\"32000\"", "true" }) {
            update(owner, id, "\"1\"", "{ \"price\": " + price + " }")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("price.invalid"));
        }
        for (String price : new String[] { "0", "-5", "100000001", "99999999999999999999" }) {
            update(owner, id, "\"1\"", "{ \"price\": " + price + " }")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("price.out-of-range"));
        }
        update(owner, id, "\"1\"", "{ \"price\": 100000000 }").andExpect(status().isOk());
        update(owner, id, "\"2\"", "{ \"price\": 1 }").andExpect(status().isOk());
    }

    @Test
    void invalidAttributesAreReportedOneByOne() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"vehicles.cars\" }"));

        update(owner, id, "\"1\"", "{ \"attributes\": { \"year\": 1890, \"color\": \"rouge\" } }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("attributes.year",
                        "attributes.color")))
                .andExpect(jsonPath("$.errors[?(@.field == 'attributes.year')].code")
                        .value(contains("attribute.out-of-range")))
                .andExpect(jsonPath("$.errors[?(@.field == 'attributes.color')].code")
                        .value(contains("attribute.unknown")));

        // Nothing was saved: the version is unchanged.
        update(owner, id, "\"1\"", "{}").andExpect(status().isOk());
    }

    @Test
    void theYearMayBeNextYearAtMost() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"vehicles.cars\" }"));
        int nextYear = Year.now().getValue() + 1;

        update(owner, id, "\"1\"", "{ \"attributes\": { \"year\": " + (nextYear + 1) + " } }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("attribute.out-of-range"));
        update(owner, id, "\"1\"", "{ \"attributes\": { \"year\": " + nextYear + " } }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.year").value(nextYear));
    }

    @Test
    void attributesAreMergedMemberByMember() throws Exception {
        String id = id(create(owner, """
                { "categoryCode": "vehicles.cars", "attributes": { "brand": "Renault", "model": "Clio" } }"""));

        update(owner, id, "\"1\"", "{ \"attributes\": { \"model\": null, \"fuel\": \"petrol\" } }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.brand").value("Renault"))
                .andExpect(jsonPath("$.attributes.model").doesNotExist())
                .andExpect(jsonPath("$.attributes.fuel").value("petrol"));

        update(owner, id, "\"2\"", "{ \"attributes\": null }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes").isEmpty());
    }

    @Test
    void nullClearsAnOptionalField() throws Exception {
        String id = id(create(owner, """
                { "categoryCode": "home.furniture", "title": "Canapé d'angle", "negotiable": true }"""));

        update(owner, id, "\"1\"", "{ \"title\": null, \"negotiable\": null }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(nullValue()))
                .andExpect(jsonPath("$.negotiable").value(false))
                .andExpect(jsonPath("$.missingFields[0]").value("title"));
    }

    @Test
    void theCategoryCannotBeCleared() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        update(owner, id, "\"1\"", "{ \"categoryCode\": null }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("category.required"));
    }

    @Test
    void titleAndDescriptionHaveBoundedLengths() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        update(owner, id, "\"1\"", "{ \"title\": \"  Vélo  \", \"description\": \"Trop court\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].code").value(contains("title.too-short")))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')].code")
                        .value(contains("description.too-short")));
        update(owner, id, "\"1\"", "{ \"title\": \"" + "a".repeat(81) + "\", \"description\": \""
                + "b".repeat(4001) + "\" }")
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].code").value(contains("title.too-long")))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')].code")
                        .value(contains("description.too-long")));
        update(owner, id, "\"1\"", "{ \"title\": \"" + "ب".repeat(80) + "\", \"description\": \""
                + "b".repeat(4000) + "\" }")
                .andExpect(status().isOk());
    }

    @Test
    void htmlIsKeptAsPlainText() throws Exception {
        String title = "<b>Vélo</b> <script>alert(1)</script>";
        String id = id(create(owner, "{ \"categoryCode\": \"leisure.sports\", \"title\": \"" + title + "\" }")
                .andExpect(status().isCreated()));

        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.title").value(title));
    }

    @Test
    void theDescriptionKeepsItsLineBreaks() throws Exception {
        String description = "Ligne une du texte\\n\\nLigne deux du texte";
        create(owner, "{ \"categoryCode\": \"home.furniture\", \"description\": \"  " + description + "  \" }")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Ligne une du texte\n\nLigne deux du texte"));
    }

    @Test
    void unknownFieldsAreRefused() throws Exception {
        create(owner, "{ \"categoryCode\": \"home.furniture\", \"status\": \"PUBLISHED\", \"phone\": \"+216\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("status", "phone")))
                .andExpect(jsonPath("$.errors[*].code").value(contains("field.unknown", "field.unknown")));
    }

    @Test
    void theDelegationMustBelongToTheGovernorate() throws Exception {
        String tunisDelegation = delegationOf("tunis");
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        update(owner, id, "\"1\"", "{ \"governorateCode\": \"sfax\", \"delegationCode\": \"" + tunisDelegation
                + "\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("delegationCode"))
                .andExpect(jsonPath("$.errors[0].code").value("delegation.not-in-governorate"));

        String sfaxDelegation = delegationOf("sfax");
        update(owner, id, "\"1\"", "{ \"governorateCode\": \"sfax\", \"delegationCode\": \"" + sfaxDelegation
                + "\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegationCode").value(sfaxDelegation));

        // Changing the governorate alone leaves an incoherent delegation: it has to be sent too.
        update(owner, id, "\"2\"", "{ \"governorateCode\": \"tunis\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("delegation.not-in-governorate"));
        update(owner, id, "\"2\"", "{ \"governorateCode\": \"tunis\", \"delegationCode\": null }")
                .andExpect(status().isOk());
    }

    @Test
    void unknownPlacesAreRefused() throws Exception {
        create(owner, """
                { "categoryCode": "home.furniture", "governorateCode": "paris", "delegationCode": "sfax.nowhere" }""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'governorateCode')].code")
                        .value(contains("governorate.unknown")))
                .andExpect(jsonPath("$.errors[?(@.field == 'delegationCode')].code")
                        .value(contains("delegation.unknown")));
    }

    @Test
    void changingTheCategoryRemovesTheAttributesItDoesNotDefine() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"vehicles.cars\" }"));
        update(owner, id, "\"1\"", CAR_DETAILS).andExpect(status().isOk());

        update(owner, id, "\"2\"", "{ \"categoryCode\": \"real-estate.apartments\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCode").value("real-estate.apartments"))
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.warnings").value(contains("attributes-removed")))
                .andExpect(jsonPath("$.missingFields").value(contains("attributes.transaction",
                        "attributes.surface-m2", "attributes.rooms")))
                // Kept: the common fields
                .andExpect(jsonPath("$.price").value(32000));

        // The warning belongs to the request that caused it.
        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    @Test
    void attributesSharedByTheNewCategoryAreKept() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"vehicles.cars\" }"));
        update(owner, id, "\"1\"", CAR_DETAILS).andExpect(status().isOk());

        update(owner, id, "\"2\"", "{ \"categoryCode\": \"vehicles.motorcycles\" }")
                .andExpect(jsonPath("$.attributes.year").value(2019))
                .andExpect(jsonPath("$.attributes.mileage-km").value(62000))
                .andExpect(jsonPath("$.attributes.brand").doesNotExist())
                .andExpect(jsonPath("$.warnings").value(contains("attributes-removed")));
    }

    @Test
    void attributesSentWithANewCategoryFollowTheNewCategory() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"vehicles.cars\" }"));

        update(owner, id, "\"1\"", """
                { "categoryCode": "real-estate.apartments", "attributes": { "brand": "Peugeot", "rooms": 2 } }""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attributes.brand"))
                .andExpect(jsonPath("$.errors[0].code").value("attribute.unknown"));
    }

    @Test
    void onQuoteIsResetWhenTheNewCategoryIsASale() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"services.home-repair\", \"priceMode\": \"ON_QUOTE\" }"));

        update(owner, id, "\"1\"", "{ \"categoryCode\": \"home.furniture\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SALE"))
                .andExpect(jsonPath("$.priceMode").value(nullValue()))
                .andExpect(jsonPath("$.warnings").value(contains("price-mode-reset")))
                .andExpect(jsonPath("$.missingFields").value(contains("title", "description", "priceMode",
                        "governorateCode")));
    }

    @Test
    void aDraftOfAnotherAccountIsNotFound() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));
        UUID other = UUID.randomUUID();

        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        update(other, id, "\"1\"", "{ \"title\": \"Je prends ce canapé\" }")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        // Without a token, the same answer
        mockMvc.perform(get("/listings/" + id)).andExpect(status().isNotFound());
        // Unchanged
        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value(nullValue()));
    }

    @Test
    void anUnknownOrMalformedIdIsNotFound() throws Exception {
        mockMvc.perform(get("/listings/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/listings/42").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        update(owner, "42", "\"1\"", "{}").andExpect(status().isNotFound());
    }

    @Test
    void anAccountKeepsTwentyDraftsAtMost() throws Exception {
        for (int i = 0; i < 20; i++) {
            create(owner, "{ \"categoryCode\": \"home.furniture\" }").andExpect(status().isCreated());
        }

        create(owner, "{ \"categoryCode\": \"home.furniture\" }")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:draft-limit-reached"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listings WHERE owner_id = ?", Long.class, owner))
                .isEqualTo(20L);

        // Another account is not affected.
        create(UUID.randomUUID(), "{ \"categoryCode\": \"home.furniture\" }").andExpect(status().isCreated());
    }

    @Test
    void aChangeOnAnOldVersionIsAConflict() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));
        update(owner, id, "\"1\"", "{ \"title\": \"Canapé d'angle\" }").andExpect(status().isOk());
        update(owner, id, "\"2\"", "{ \"price\": 300 }").andExpect(header().string(HttpHeaders.ETAG, "\"3\""));

        // A second tab still on version 2
        update(owner, id, "\"2\"", "{ \"title\": \"Canapé convertible\" }")
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:version-conflict"));

        mockMvc.perform(get("/listings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.title").value("Canapé d'angle"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void aChangeNeedsIfMatch() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        mockMvc.perform(patch("/listings/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MERGE_PATCH)
                .content("{ \"title\": \"Canapé d'angle\" }"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:if-match-required"));
        // A weak tag never matches (strong comparison); "*" matches any version.
        update(owner, id, "W/\"1\"", "{}").andExpect(status().isPreconditionFailed());
        update(owner, id, "*", "{}").andExpect(status().isOk());
    }

    @Test
    void plainJsonIsAcceptedForAPatch() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        mockMvc.perform(patch("/listings/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .header(HttpHeaders.IF_MATCH, "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"Canapé d'angle\" }"))
                .andExpect(status().isOk());
    }

    @Test
    void aBodyThatIsNotAnObjectIsRefused() throws Exception {
        String id = id(create(owner, "{ \"categoryCode\": \"home.furniture\" }"));

        update(owner, id, "\"1\"", "[1, 2]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("body.not-an-object"));
        update(owner, id, "\"1\"", "{ not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions create(UUID account, String body) throws Exception {
        return mockMvc.perform(post("/listings")
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions update(UUID account, String id, String ifMatch, String body) throws Exception {
        return mockMvc.perform(patch("/listings/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MERGE_PATCH)
                .content(body));
    }

    private static String id(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String delegationOf(String governorate) {
        return jdbc.queryForObject("SELECT min(code) FROM delegations WHERE governorate_code = ?", String.class,
                governorate);
    }

    private static String bearer(UUID account) {
        return "Bearer " + TestJwks.token(account);
    }
}
