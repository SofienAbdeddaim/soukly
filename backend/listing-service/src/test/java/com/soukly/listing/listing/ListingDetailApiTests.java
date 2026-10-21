package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;
import com.soukly.listing.support.TestListings;
import com.soukly.listing.support.TestUserService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Public reading of a listing (CATA-03): status × reader, seller profile, {@code 410} and {@code 404}. */
class ListingDetailApiTests extends IntegrationTest {

    private static final Duration TTL = Duration.ofDays(60);

    @Autowired
    private ListingLifecycle lifecycle;

    @Autowired
    private JsonMapper json;

    private UUID owner;

    @BeforeEach
    void newOwner() {
        owner = UUID.randomUUID();
    }

    @Test
    void aPublishedListingIsReadByAnyoneWithItsSellerButNoContactData() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 4);
        List<UUID> photos = jdbc.queryForList(
                "SELECT id FROM listing_photos WHERE listing_id = ? ORDER BY position", UUID.class, id);

        String body = read(id, null)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, private"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.kind").value("SALE"))
                .andExpect(jsonPath("$.title").value("Peugeot 208 diesel"))
                .andExpect(jsonPath("$.description")
                        .value("Première main, carnet d'entretien.\nPneus neufs. <script>alert(1)</script>"))
                .andExpect(jsonPath("$.priceMode").value("FIXED"))
                .andExpect(jsonPath("$.price").value(32000))
                .andExpect(jsonPath("$.currency").value("TND"))
                .andExpect(jsonPath("$.negotiable").value(true))
                .andExpect(jsonPath("$.categoryCode").value("vehicles.cars"))
                .andExpect(jsonPath("$.governorateCode").value("sousse"))
                .andExpect(jsonPath("$.delegationCode").value("sousse.sousse-riadh"))
                .andExpect(jsonPath("$.attributes.year").value(2019))
                .andExpect(jsonPath("$.photos.length()").value(4))
                .andExpect(jsonPath("$.photos[0].id").value(photos.getFirst().toString()))
                .andExpect(jsonPath("$.photos[0].url").value(
                        "https://photos.example.test/listings/" + id + "/" + photos.getFirst() + "/1600.webp"))
                .andExpect(jsonPath("$.photos[0].thumbnailUrl").value(
                        "https://photos.example.test/listings/" + id + "/" + photos.getFirst() + "/400.webp"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.seller.id").value(owner.toString()))
                .andExpect(jsonPath("$.seller.displayName").value(TestUserService.DISPLAY_NAME))
                .andExpect(jsonPath("$.seller.memberSince").value(TestUserService.MEMBER_SINCE))
                .andReturn().getResponse().getContentAsString();

        JsonNode listing = json.readTree(body);
        assertThat(names(listing)).containsExactlyInAnyOrder("id", "status", "kind", "categoryCode", "title",
                "description", "priceMode", "price", "currency", "negotiable", "governorateCode", "delegationCode",
                "attributes", "photos", "publishedAt", "seller");
        assertThat(names(listing.get("seller"))).containsExactlyInAnyOrder("id", "displayName", "memberSince");
        // Attributes in the display order of the subcategory (CAT-01)
        assertThat(names(listing.get("attributes"))).containsExactly("brand", "model", "year", "mileage-km", "fuel",
                "gearbox");
        assertThat(body).doesNotContain(TestUserService.PHONE).doesNotContain("@");
    }

    @Test
    void anotherMemberGetsThePublicViewAndTheOwnerTheFullOne() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);

        read(id, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.missingFields").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.seller.displayName").value(TestUserService.DISPLAY_NAME));
        read(id, owner)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.missingFields").isEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.seller.id").value(owner.toString()))
                .andExpect(jsonPath("$.seller.displayName").value(TestUserService.DISPLAY_NAME));
    }

    @Test
    void anExpiredListingIsGoneWithItsCategoryAndGovernorateOnlyButStillReadByItsOwner() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 1);
        clock.advance(TTL.plusMinutes(1));
        lifecycle.run();

        for (UUID reader : new UUID[] { null, UUID.randomUUID() }) {
            String body = read(id, reader)
                    .andExpect(status().isGone())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-gone"))
                    .andExpect(jsonPath("$.categoryCode").value("vehicles.cars"))
                    .andExpect(jsonPath("$.governorateCode").value("sousse"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(names(json.readTree(body))).containsExactlyInAnyOrder("type", "title", "status", "detail",
                    "instance", "categoryCode", "governorateCode");
        }
        read(id, owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.seller.displayName").value(TestUserService.DISPLAY_NAME));
    }

    @Test
    void aPublishedListingPastItsExpiryIsGoneBeforeTheExpiryTaskRuns() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        clock.advance(TTL.plusSeconds(1));

        read(id, null).andExpect(status().isGone());
        read(id, owner).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void aDraftIsNotFoundForAnyoneButItsOwner() throws Exception {
        UUID id = TestListings.completeDraft(mockMvc, jdbc, owner, 1);

        read(id, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        read(id, UUID.randomUUID()).andExpect(status().isNotFound());
        // The owner's draft needs no seller: user-service is not called
        read(id, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.seller").value((Object) null));
        assertThat(TestUserService.calls(owner, "public-profile")).isZero();
    }

    @Test
    void aListingWhoseSellerIsNoLongerActiveIsGone() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        TestUserService.close(owner);

        read(id, null)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.categoryCode").value("home.furniture"))
                .andExpect(jsonPath("$.governorateCode").value("sfax"));
    }

    @Test
    void theSellerProfileIsReadOnceThenKeptInMemory() throws Exception {
        UUID first = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        UUID second = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_CAR, 1);

        List<ResultActions> reads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            reads.add(read(first, null));
            reads.add(read(second, UUID.randomUUID()));
        }
        for (ResultActions result : reads) {
            result.andExpect(status().isOk());
        }
        assertThat(TestUserService.calls(owner, "public-profile")).isEqualTo(1);
        assertThat(TestUserService.calls(owner, "contact")).isZero();
    }

    @Test
    void anUnavailableUserServiceAnswers503() throws Exception {
        UUID id = TestListings.published(mockMvc, jdbc, owner, TestListings.COMPLETE_FURNITURE, 1);
        TestUserService.fail(owner);

        read(id, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions read(UUID id, UUID reader) throws Exception {
        var request = get("/listings/" + id);
        if (reader != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(reader));
        }
        return mockMvc.perform(request);
    }

    private static List<String> names(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }
}
