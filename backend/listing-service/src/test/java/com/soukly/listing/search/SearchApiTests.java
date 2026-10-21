package com.soukly.listing.search;

import static com.soukly.listing.search.SearchFixtures.listing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestListings;

/** Public search of CATA-01: one test per scenario of the ticket, then the edge cases of the parameters. */
class SearchApiTests extends IntegrationTest {

    private Instant now;

    /** The search reads every published listing: each test starts from an empty table. */
    @BeforeEach
    void emptyListings() {
        jdbc.update("DELETE FROM listing_photos");
        jdbc.update("DELETE FROM listings");
        now = clock.instant();
    }

    @Test
    void homePageGivesTheLatestPublishedListingsWithTheTotalAndNoSellerData() throws Exception {
        List<UUID> published = publishedEveryMinute(30);
        for (int i = 0; i < 5; i++) {
            listing().status("DRAFT").publishedAt(now).insert(jdbc);
        }

        String body = search(get("/listings"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=30, public"))
                .andExpect(jsonPath("$.items", hasSize(24)))
                .andExpect(jsonPath("$.total").value(30))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.items[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Latest first: the last inserted listing is the most recent one
        assertThat(ids(body)).containsExactlyElementsOf(published.reversed().subList(0, 24));
        Map<String, Object> card = JsonPath.read(body, "$.items[0]");
        assertThat(card).containsOnlyKeys("id", "title", "kind", "categoryCode", "priceMode", "price", "currency",
                "negotiable", "governorateCode", "delegationCode", "coverThumbnailUrl", "photoCount", "summary",
                "publishedAt");
    }

    @Test
    void nextPageGivesTheRestWithoutDuplicateNorTotalNorCursor() throws Exception {
        List<UUID> published = publishedEveryMinute(30);
        String first = body(get("/listings"));

        String second = search(get("/listings").param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<UUID> all = new ArrayList<>(ids(first));
        all.addAll(ids(second));
        assertThat(all).containsExactlyElementsOf(published.reversed());
    }

    @Test
    void aListingPublishedBetweenTwoPagesIsNeverShownTwice() throws Exception {
        publishedEveryMinute(30);
        String first = body(get("/listings"));
        listing().title("Nouvelle annonce").publishedAt(now.plus(Duration.ofHours(1))).insert(jdbc);

        String second = body(get("/listings").param("cursor", (String) JsonPath.read(first, "$.nextCursor")));

        assertThat(ids(second)).hasSize(6).doesNotContainAnyElementsOf(ids(first));
    }

    @Test
    void aTypingMistakeStillFindsTheBrand() throws Exception {
        UUID peugeot = listing().title("Peugeot 208 diesel").car("Peugeot", 2019, "diesel").insert(jdbc);
        listing().title("Renault Clio").car("Renault", 2018, "petrol").insert(jdbc);

        assertThat(ids(body(get("/listings").param("q", "peugot")))).containsExactly(peugeot);
    }

    @Test
    void arabicQueryIgnoresTheSpellingOfTehMarbutaAndDiacritics() throws Exception {
        UUID car = listing().title("سيارة للبيع بحالة جيدة").insert(jdbc);
        listing().title("دراجة نارية").insert(jdbc);

        assertThat(ids(body(get("/listings").param("q", "سياره")))).containsExactly(car);
        assertThat(ids(body(get("/listings").param("q", "سَيّارة")))).containsExactly(car);
    }

    @Test
    void latinQueryIgnoresCaseAndAccents() throws Exception {
        UUID sofa = listing().title("Canapé d'angle en cuir").insert(jdbc);
        UUID table = listing().title("Table basse").description("Assortie au CANAPÉ, très peu servie").insert(jdbc);
        listing().title("Armoire").insert(jdbc);

        // The title weighs more than the description
        assertThat(ids(body(get("/listings").param("q", "canape")))).containsExactly(sofa, table);
    }

    @Test
    void familyIncludesAllItsSubcategories() throws Exception {
        UUID car = listing().car("Kia", 2020, "petrol").insert(jdbc);
        UUID part = listing().category("vehicles.parts").insert(jdbc);
        listing().category("home.furniture").insert(jdbc);

        assertThat(ids(body(get("/listings").param("category", "vehicles")))).containsExactlyInAnyOrder(car, part);
        assertThat(ids(body(get("/listings").param("category", "vehicles.parts")))).containsExactly(part);
    }

    @Test
    void specificAttributesFilterTheCarsOfTheSubcategory() throws Exception {
        listing().car("Kia", 2012, "diesel").insert(jdbc);
        UUID recentDiesel = listing().car("Kia", 2017, "diesel").insert(jdbc);
        UUID newestDiesel = listing().car("Kia", 2021, "diesel").insert(jdbc);
        listing().car("Kia", 2021, "petrol").insert(jdbc);

        assertThat(ids(body(get("/listings").param("category", "vehicles.cars").param("attr.year.min", "2015")
                .param("attr.fuel", "diesel")))).containsExactlyInAnyOrder(recentDiesel, newestDiesel);
        assertThat(ids(body(get("/listings").param("category", "vehicles.cars").param("attr.year.min", "2015")
                .param("attr.year.max", "2020").param("attr.fuel", "diesel,hybrid"))))
                .containsExactly(recentDiesel);
    }

    @Test
    void textAndBooleanAttributes() throws Exception {
        UUID peugeot = listing().car("Peugeot", 2019, "diesel").insert(jdbc);
        listing().car("Citroën", 2019, "diesel").insert(jdbc);
        UUID furnished = listing().category("real-estate.apartments").attributes("""
                {"transaction": "rent", "surface-m2": 90, "rooms": 2, "furnished": true}""").insert(jdbc);
        listing().category("real-estate.apartments").attributes("""
                {"transaction": "rent", "surface-m2": 90, "rooms": 2, "furnished": false}""").insert(jdbc);

        assertThat(ids(body(get("/listings").param("category", "vehicles.cars").param("attr.brand", "PEUGEOT"))))
                .containsExactly(peugeot);
        assertThat(ids(body(get("/listings").param("category", "real-estate.apartments")
                .param("attr.furnished", "true")))).containsExactly(furnished);
    }

    @Test
    void anAttributeFilterNeedsASubcategoryThatHasIt() throws Exception {
        expectInvalid(get("/listings").param("category", "vehicles").param("attr.year.min", "2015"),
                "filter.requires-subcategory");
        expectInvalid(get("/listings").param("attr.year.min", "2015"), "filter.requires-subcategory");
        expectInvalid(get("/listings").param("category", "vehicles.cars").param("attr.surface-m2.min", "10"),
                "filter.unknown-attribute");
        // model exists for cars but is not filterable
        expectInvalid(get("/listings").param("category", "vehicles.cars").param("attr.model", "208"),
                "filter.unknown-attribute");
        expectInvalid(get("/listings").param("category", "vehicles.cars").param("attr.fuel", "steam"),
                "filter.invalid-value");
        expectInvalid(get("/listings").param("category", "vehicles.cars").param("attr.year", "2015"),
                "filter.invalid-value");
    }

    @Test
    void aPriceFilterLeavesOutTheListingsOnQuoteAndTheyComeLastWhenSortedByPrice() throws Exception {
        UUID onQuote = listing().onQuote().publishedAt(now).insert(jdbc);
        UUID cheap = listing().category("services.home-repair").price(50).publishedAt(now.minusSeconds(60))
                .insert(jdbc);
        UUID expensive = listing().price(900).publishedAt(now.minusSeconds(120)).insert(jdbc);

        assertThat(ids(body(get("/listings").param("priceMax", "100")))).containsExactly(cheap);
        assertThat(ids(body(get("/listings").param("sort", "price-asc")))).containsExactly(cheap, expensive,
                onQuote);
        assertThat(ids(body(get("/listings").param("sort", "price-desc")))).containsExactly(expensive, cheap,
                onQuote);
    }

    @Test
    void governorateAndDelegationFilters() throws Exception {
        UUID sfaxCity = listing().place("sfax", "sfax.sfax-ville").insert(jdbc);
        UUID sfax = listing().place("sfax", null).insert(jdbc);
        listing().place("tunis", null).insert(jdbc);

        assertThat(ids(body(get("/listings").param("governorate", "sfax")))).containsExactlyInAnyOrder(sfaxCity,
                sfax);
        assertThat(ids(body(get("/listings").param("governorate", "sfax").param("delegation", "sfax.sfax-ville"))))
                .containsExactly(sfaxCity);
        expectInvalid(get("/listings").param("delegation", "sfax.sfax-ville"), "filter.unknown-place");
        expectInvalid(get("/listings").param("governorate", "tunis").param("delegation", "sfax.sfax-ville"),
                "filter.unknown-place");
        expectInvalid(get("/listings").param("governorate", "atlantis"), "filter.unknown-place");
    }

    @Test
    void expiredListingsAndListingsPastTheirExpiryAreNeverFound() throws Exception {
        UUID online = listing().insert(jdbc);
        listing().status("EXPIRED").publishedAt(now.minus(Duration.ofDays(61))).insert(jdbc);
        // Still PUBLISHED until the hourly task runs
        listing().publishedAt(now.minus(Duration.ofDays(61))).expiresAt(now.minusSeconds(1)).insert(jdbc);

        assertThat(ids(body(get("/listings")))).containsExactly(online);
    }

    @Test
    void cardHasTheCoverTheNumberOfPhotosAndThreeAttributesInDisplayOrder() throws Exception {
        UUID car = listing().title("Peugeot 208").car("Peugeot", 2019, "diesel").place("sfax", "sfax.sfax-ville")
                .insert(jdbc);
        List<UUID> photos = TestListings.readyPhotos(jdbc, car, 4);

        search(get("/listings"))
                .andExpect(jsonPath("$.items[0].kind").value("SALE"))
                .andExpect(jsonPath("$.items[0].categoryCode").value("vehicles.cars"))
                .andExpect(jsonPath("$.items[0].priceMode").value("FIXED"))
                .andExpect(jsonPath("$.items[0].price").value(1200))
                .andExpect(jsonPath("$.items[0].currency").value("TND"))
                .andExpect(jsonPath("$.items[0].negotiable").value(true))
                .andExpect(jsonPath("$.items[0].governorateCode").value("sfax"))
                .andExpect(jsonPath("$.items[0].delegationCode").value("sfax.sfax-ville"))
                .andExpect(jsonPath("$.items[0].coverThumbnailUrl")
                        .value("https://photos.example.test/listings/" + car + "/" + photos.getFirst() + "/400.webp"))
                .andExpect(jsonPath("$.items[0].photoCount").value(4))
                .andExpect(jsonPath("$.items[0].summary.brand").value("Peugeot"))
                .andExpect(jsonPath("$.items[0].summary.year").value(2019))
                .andExpect(jsonPath("$.items[0].summary.mileage-km").value(62000))
                .andExpect(jsonPath("$.items[0].summary.fuel").doesNotExist());
    }

    @Test
    void aServiceOnQuoteHasNoPriceNorPhoto() throws Exception {
        listing().onQuote().insert(jdbc);

        search(get("/listings"))
                .andExpect(jsonPath("$.items[0].kind").value("SERVICE"))
                .andExpect(jsonPath("$.items[0].priceMode").value("ON_QUOTE"))
                .andExpect(jsonPath("$.items[0].price").isEmpty())
                .andExpect(jsonPath("$.items[0].coverThumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.items[0].photoCount").value(0))
                .andExpect(jsonPath("$.items[0].summary").isEmpty());
    }

    @Test
    void noResultIsAnEmptyPage() throws Exception {
        search(get("/listings").param("q", "introuvable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void everySortPagesThroughEveryListingOnceEvenWithEqualKeys() throws Exception {
        Set<UUID> expected = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            // Same prices and same dates in pairs: the id breaks the ties
            Instant date = now.minus(i / 2, ChronoUnit.MINUTES);
            expected.add(listing().title("Vélo de ville " + i).price(100 * (i % 3 + 1)).publishedAt(date)
                    .insert(jdbc));
        }
        expected.add(listing().title("Réparation de vélo").onQuote().insert(jdbc));

        for (String sort : List.of("recent", "price-asc", "price-desc", "relevance")) {
            List<UUID> seen = new ArrayList<>();
            String cursor = null;
            do {
                MockHttpServletRequestBuilder request = get("/listings").param("sort", sort).param("limit", "5")
                        .param("q", "velo");
                if (cursor != null) {
                    request.param("cursor", cursor);
                }
                String body = body(request);
                seen.addAll(ids(body));
                cursor = JsonPath.<Map<String, Object>>read(body, "$").containsKey("nextCursor")
                        ? JsonPath.read(body, "$.nextCursor")
                        : null;
            } while (cursor != null);
            assertThat(seen).as(sort).hasSize(13).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void aChangedCursorOrACursorOfAnotherSortIsRefused() throws Exception {
        publishedEveryMinute(3);
        String cursor = JsonPath.read(body(get("/listings").param("limit", "1")), "$.nextCursor");
        String payload = cursor.substring(0, cursor.indexOf('.'));
        String changed = (payload.charAt(0) == 'A' ? 'B' : 'A') + cursor.substring(1);

        expectInvalid(get("/listings").param("cursor", changed), "cursor.invalid");
        expectInvalid(get("/listings").param("cursor", cursor).param("sort", "price-asc"), "cursor.invalid");
        expectInvalid(get("/listings").param("cursor", "not-a-cursor"), "cursor.invalid");
        search(get("/listings").param("cursor", cursor).param("limit", "1")).andExpect(status().isOk());
    }

    @Test
    void invalidParametersHaveAStableCode() throws Exception {
        expectInvalid(get("/listings").param("q", "a"), "query.invalid-length");
        expectInvalid(get("/listings").param("q", "a".repeat(101)), "query.invalid-length");
        expectInvalid(get("/listings").param("category", "spaceships"), "filter.unknown-category");
        expectInvalid(get("/listings").param("priceMin", "abc"), "filter.invalid-value");
        expectInvalid(get("/listings").param("priceMin", "500").param("priceMax", "100"), "filter.invalid-value");
        expectInvalid(get("/listings").param("sort", "cheapest"), "sort.invalid");
        expectInvalid(get("/listings").param("sort", "relevance"), "sort.invalid");
        expectInvalid(get("/listings").param("limit", "49"), "limit.invalid");
        expectInvalid(get("/listings").param("limit", "0"), "limit.invalid");
    }

    @Test
    void sqlNormalizationMatchesTheJavaOne() {
        for (String text : List.of("Peugeot Éléphant ŒUVRE Straße Ærø", "سيّارة أحمد إلى مُستشفى آخر",
                "ـمـؤسسة ـ ئـ", "Çà et là, NAÏVE", "Kilométrage 62 000 km")) {
            assertThat(jdbc.queryForObject("SELECT soukly_normalize(?)", String.class, text))
                    .as(text).isEqualTo(SearchText.normalize(text));
        }
    }

    /** {@code count} listings published one minute apart, the oldest first. */
    private List<UUID> publishedEveryMinute(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(listing().title("Annonce " + i).publishedAt(now.minus(count - i, ChronoUnit.MINUTES))
                    .insert(jdbc));
        }
        return ids;
    }

    private ResultActions search(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.accept(MediaType.APPLICATION_JSON));
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return search(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void expectInvalid(MockHttpServletRequestBuilder request, String code) throws Exception {
        search(request)
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:invalid-search"))
                .andExpect(jsonPath("$.code").value(code));
    }

    private static List<UUID> ids(String body) {
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }
}
