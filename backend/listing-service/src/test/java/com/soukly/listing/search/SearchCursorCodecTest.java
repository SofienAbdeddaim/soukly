package com.soukly.listing.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SearchCursorCodecTest {

    private static final Instant DATE = Instant.parse("2026-10-21T10:15:30.123456Z");

    private final SearchCursorCodec codec = codec("secret-1");
    private final RelevanceRanking relevance = new RelevanceRanking();
    private final PriceRanking price = new PriceRanking.Ascending();
    private final RecentRanking recent = new RecentRanking();

    @Test
    void cursorReadsBackExactly() {
        UUID id = UUID.randomUUID();
        double score = 0.1 + 0.2;

        assertThat(codec.decode(codec.encode(new SearchCursor("relevance", score, DATE, id)), relevance))
                .isEqualTo(new SearchCursor("relevance", score, DATE, id));
        assertThat(codec.decode(codec.encode(new SearchCursor("recent", null, DATE, id)), recent).key()).isNull();
        assertThat(codec.decode(codec.encode(new SearchCursor("price-asc", Long.MAX_VALUE, DATE, id)), price).key())
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void changedPayloadIsRefused() {
        String cursor = codec.encode(new SearchCursor("recent", null, DATE, UUID.randomUUID()));
        String payload = new String(Base64.getUrlDecoder().decode(cursor.substring(0, cursor.indexOf('.'))));
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("recent", "price-asc").getBytes())
                + cursor.substring(cursor.indexOf('.'));

        assertInvalid(forged, price);
    }

    @Test
    void cursorOfAnotherSortOrAnotherKeyIsRefused() {
        String cursor = codec.encode(new SearchCursor("recent", null, DATE, UUID.randomUUID()));

        assertInvalid(cursor, price);
        assertInvalid(codec("secret-2").encode(new SearchCursor("recent", null, DATE, UUID.randomUUID())), recent);
    }

    @Test
    void keyOfTheWrongTypeIsRefused() {
        assertInvalid(codec.encode(new SearchCursor("relevance", 12L, DATE, UUID.randomUUID())), relevance);
        assertInvalid(codec.encode(new SearchCursor("price-asc", 1.5, DATE, UUID.randomUUID())), price);
        assertInvalid(codec.encode(new SearchCursor("recent", 3L, DATE, UUID.randomUUID())), recent);
    }

    @Test
    void garbageIsRefused() {
        assertInvalid("", recent);
        assertInvalid("abc", recent);
        assertInvalid("abc.def", recent);
        assertInvalid("é.é", recent);
        assertInvalid("a".repeat(600) + ".b", recent);
    }

    private void assertInvalid(String cursor, ListingRankingStrategy ranking) {
        assertThatThrownBy(() -> codec.decode(cursor, ranking))
                .isInstanceOfSatisfying(InvalidSearchException.class,
                        e -> assertThat(e.code()).isEqualTo("cursor.invalid"));
    }

    private static SearchCursorCodec codec(String secret) {
        return new SearchCursorCodec(new SearchProperties(24, 48, 0.5, secret, Duration.ofSeconds(30)),
                JsonMapper.builder().build());
    }
}
