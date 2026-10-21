package com.soukly.listing.search;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Listings inserted straight into the table, with a chosen publication date: the publication itself is tested by
 * ANN-03. Builder style: {@code listing().title("…").car("peugeot", 2019, "diesel").publishedAt(t).insert(jdbc)}.
 */
final class SearchFixtures {

    private static final Duration TTL = Duration.ofDays(60);

    private UUID id = UUID.randomUUID();
    private String status = "PUBLISHED";
    private String category = "home.furniture";
    private String title = "Canapé d'angle";
    private String description = "Très bon état, à récupérer sur place.";
    private String priceMode = "FIXED";
    private Long price = 1200L;
    private String governorate = "tunis";
    private String delegation = null;
    private String attributes = "{}";
    private Instant publishedAt = Instant.now();
    private Instant expiresAt = null;

    static SearchFixtures listing() {
        return new SearchFixtures();
    }

    SearchFixtures status(String value) {
        status = value;
        return this;
    }

    SearchFixtures category(String value) {
        category = value;
        return this;
    }

    SearchFixtures title(String value) {
        title = value;
        return this;
    }

    SearchFixtures description(String value) {
        description = value;
        return this;
    }

    SearchFixtures price(long value) {
        priceMode = "FIXED";
        price = value;
        return this;
    }

    /** A service "on quote" (only services and jobs may be). */
    SearchFixtures onQuote() {
        category = "services.home-repair";
        priceMode = "ON_QUOTE";
        price = null;
        return this;
    }

    SearchFixtures place(String governorateCode, String delegationCode) {
        governorate = governorateCode;
        delegation = delegationCode;
        return this;
    }

    /** A car with every required attribute. */
    SearchFixtures car(String brand, int year, String fuel) {
        category = "vehicles.cars";
        attributes = """
                {"brand": "%s", "year": %d, "mileage-km": 62000, "fuel": "%s", "gearbox": "manual"}"""
                .formatted(brand, year, fuel);
        return this;
    }

    SearchFixtures attributes(String json) {
        attributes = json;
        return this;
    }

    SearchFixtures publishedAt(Instant value) {
        publishedAt = value;
        return this;
    }

    SearchFixtures expiresAt(Instant value) {
        expiresAt = value;
        return this;
    }

    UUID insert(JdbcTemplate jdbc) {
        boolean draft = status.equals("DRAFT");
        Instant expiry = expiresAt != null ? expiresAt : publishedAt.plus(TTL);
        jdbc.update("""
                INSERT INTO listings (id, owner_id, status, category_code, title, description, price_mode, price_tnd,
                        negotiable, governorate_code, delegation_code, attributes, version, created_at, updated_at,
                        published_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, CAST(? AS jsonb), 2, ?, ?, ?, ?)""",
                id, UUID.randomUUID(), status, category, title, description, priceMode, price, governorate,
                delegation, attributes, Timestamp.from(publishedAt), Timestamp.from(publishedAt),
                draft ? null : Timestamp.from(publishedAt), draft ? null : Timestamp.from(expiry));
        return id;
    }
}
