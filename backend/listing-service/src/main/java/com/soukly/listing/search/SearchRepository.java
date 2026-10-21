package com.soukly.listing.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * SQL of the search. The statement is assembled from constant fragments chosen by the criteria; every value given by
 * the client, attribute codes included, is a bound parameter and never part of the SQL text.
 */
@Repository
class SearchRepository {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<>() {
    };

    /** Full text on the title and the description, or a title or a brand close to the query (typing mistakes). */
    private static final String TEXT_MATCH = """
            (l.search_vector @@ plainto_tsquery('simple', soukly_normalize(:query))
             OR soukly_normalize(l.title) %> soukly_normalize(:query)
             OR soukly_normalize(l.attributes ->> 'brand') %> soukly_normalize(:query))""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    SearchRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Applies the similarity threshold of the {@code %>} operator until the end of the current transaction. */
    void useSimilarityThreshold(double threshold) {
        jdbc.sql("SELECT set_config('pg_trgm.word_similarity_threshold', :threshold, true)")
                .param("threshold", Double.toString(threshold))
                .query((rs, row) -> 1)
                .single();
    }

    /** Matching listings, in the order of the ranking, after the cursor; at most {@code limit} rows. */
    List<SearchRow> page(SearchCriteria criteria, Instant now, int limit) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = filters(criteria, now, params);
        ListingRankingStrategy ranking = criteria.ranking();
        if (criteria.cursor() != null) {
            where.append(" AND ").append(ranking.after());
            params.put("cursorKey", criteria.cursor().key() == null ? null
                    : ranking.bindKey(criteria.cursor().key()));
            params.put("cursorPublishedAt", Timestamp.from(criteria.cursor().publishedAt()));
            params.put("cursorId", criteria.cursor().id());
        }
        params.put("limit", limit);
        // The photos are only read for the rows of the page.
        String sql = """
                WITH page AS (
                    SELECT l.id, l.title, l.category_code, l.price_mode, l.price_tnd, l.negotiable,
                           l.governorate_code, l.delegation_code, l.attributes, l.published_at,
                           %s AS rank_key
                    FROM listings l
                    WHERE %s
                    ORDER BY %s
                    LIMIT :limit)
                SELECT page.*, cover.id AS cover_id,
                       (SELECT count(*) FROM listing_photos p
                        WHERE p.listing_id = page.id AND p.status = 'READY') AS photo_count
                FROM page
                LEFT JOIN listing_photos cover
                       ON cover.listing_id = page.id AND cover.status = 'READY' AND cover.position = 0
                ORDER BY %s""".formatted(ranking.rankKey(), where, ranking.orderBy(), ranking.orderBy());
        return jdbc.sql(sql).params(params).query(this::row).list();
    }

    /** Every matching listing, whatever the page. */
    long count(SearchCriteria criteria, Instant now) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = filters(criteria, now, params);
        return jdbc.sql("SELECT count(*) FROM listings l WHERE " + where).params(params).query(Long.class).single();
    }

    private static StringBuilder filters(SearchCriteria criteria, Instant now, Map<String, Object> params) {
        // The literal status lets PostgreSQL use the partial indexes of the search (V9). A listing past its expiry
        // date stays PUBLISHED until the hourly task: it is hidden already.
        StringBuilder where = new StringBuilder("l.status = 'PUBLISHED' AND l.expires_at > :now");
        params.put("now", Timestamp.from(now));
        if (criteria.query() != null) {
            // Also used by the relevance score
            where.append(" AND ").append(TEXT_MATCH);
            params.put("query", criteria.query());
        }
        if (criteria.categoryCodes() != null) {
            where.append(" AND l.category_code IN (:categories)");
            params.put("categories", criteria.categoryCodes());
        }
        if (criteria.governorateCode() != null) {
            where.append(" AND l.governorate_code = :governorate");
            params.put("governorate", criteria.governorateCode());
        }
        if (criteria.delegationCode() != null) {
            where.append(" AND l.delegation_code = :delegation");
            params.put("delegation", criteria.delegationCode());
        }
        if (criteria.filtersPrice()) {
            where.append(" AND l.price_mode = 'FIXED'");
            if (criteria.priceMin() != null) {
                where.append(" AND l.price_tnd >= :priceMin");
                params.put("priceMin", criteria.priceMin());
            }
            if (criteria.priceMax() != null) {
                where.append(" AND l.price_tnd <= :priceMax");
                params.put("priceMax", criteria.priceMax());
            }
        }
        int index = 0;
        for (AttributeFilter filter : criteria.attributes()) {
            String code = "attr" + index + "Code";
            String value = "attr" + index + "Value";
            params.put(code, filter.code());
            switch (filter) {
                case AttributeFilter.Range range -> {
                    // Validated as whole numbers when written (ANN-01)
                    if (range.min() != null) {
                        where.append(" AND (l.attributes ->> :").append(code).append(")::bigint >= :")
                                .append(value).append("Min");
                        params.put(value + "Min", range.min());
                    }
                    if (range.max() != null) {
                        where.append(" AND (l.attributes ->> :").append(code).append(")::bigint <= :")
                                .append(value).append("Max");
                        params.put(value + "Max", range.max());
                    }
                }
                case AttributeFilter.OneOf oneOf -> {
                    where.append(" AND l.attributes ->> :").append(code).append(" IN (:").append(value).append(')');
                    params.put(value, oneOf.values());
                }
                case AttributeFilter.Is is -> {
                    where.append(" AND l.attributes ->> :").append(code).append(" = :").append(value);
                    params.put(value, Boolean.toString(is.value()));
                }
                case AttributeFilter.SameText text -> {
                    where.append(" AND soukly_normalize(l.attributes ->> :").append(code)
                            .append(") = soukly_normalize(:").append(value).append(')');
                    params.put(value, text.value());
                }
            }
            index++;
        }
        return where;
    }

    private SearchRow row(ResultSet rs, int row) throws SQLException {
        long price = rs.getLong("price_tnd");
        Long priceTnd = rs.wasNull() ? null : price;
        return new SearchRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("category_code"),
                rs.getString("price_mode"), priceTnd, rs.getBoolean("negotiable"), rs.getString("governorate_code"),
                rs.getString("delegation_code"), json.readValue(rs.getString("attributes"), ATTRIBUTES),
                rs.getTimestamp("published_at").toInstant(), rs.getObject("rank_key"),
                rs.getObject("cover_id", UUID.class), rs.getInt("photo_count"));
    }
}
