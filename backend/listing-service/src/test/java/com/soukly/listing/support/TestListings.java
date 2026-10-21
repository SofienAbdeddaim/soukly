package com.soukly.listing.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** Listings in a given state, created through the API; READY photos are inserted directly (ANN-02 is tested apart). */
public final class TestListings {

    /** Every required field of a {@code home.furniture} listing, which has no specific attribute. */
    public static final String COMPLETE_FURNITURE = """
            { "categoryCode": "home.furniture", "title": "Canapé d'angle en cuir",
              "description": "Canapé d'angle en cuir marron, très bon état, à récupérer sur place.",
              "priceMode": "FIXED", "price": 1200, "negotiable": true,
              "governorateCode": "sfax", "delegationCode": "sfax.sfax-ville" }""";

    private TestListings() {
    }

    /** Creates a draft from {@code body} and returns its id. */
    public static UUID draft(MockMvc mockMvc, UUID owner, String body) throws Exception {
        String created = mockMvc.perform(post("/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.id"));
    }

    /** Inserts {@code count} READY photos after the existing ones and returns their ids in order. */
    public static List<UUID> readyPhotos(JdbcTemplate jdbc, UUID listingId, int count) {
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM listing_photos WHERE listing_id = ? AND status = 'READY'", Integer.class,
                listingId);
        Timestamp now = Timestamp.from(Instant.now());
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO listing_photos (id, listing_id, status, position, width, height, bytes, created_at,
                        updated_at)
                    VALUES (?, ?, 'READY', ?, 1600, 1200, 500000, ?, ?)""", id, listingId, existing + i, now, now);
            ids.add(id);
        }
        return ids;
    }

    /** A complete draft of {@code owner} with {@code photos} READY photos. */
    public static UUID completeDraft(MockMvc mockMvc, JdbcTemplate jdbc, UUID owner, int photos) throws Exception {
        UUID id = draft(mockMvc, owner, COMPLETE_FURNITURE);
        readyPhotos(jdbc, id, photos);
        return id;
    }

    /** {@code POST /listings/{id}/publish} as {@code owner}, expecting {@code 200}. */
    public static void publish(MockMvc mockMvc, UUID owner, UUID id) throws Exception {
        mockMvc.perform(post("/listings/" + id + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(owner)))
                .andExpect(status().isOk());
    }
}
