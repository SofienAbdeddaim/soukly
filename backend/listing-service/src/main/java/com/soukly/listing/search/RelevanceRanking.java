package com.soukly.listing.search;

import org.springframework.stereotype.Component;

/**
 * {@code relevance}: full text score (title above description) plus the trigram word similarity of the title or the
 * brand, then the latest publications; the default with {@code q}. The score is a {@code float8} computed from the
 * same values at every page, and written exactly in the cursor (shortest decimal that reads back to the same double).
 */
@Component
class RelevanceRanking extends KeysetRankingStrategy {

    static final String SCORE = """
            (ts_rank(l.search_vector, plainto_tsquery('simple', soukly_normalize(:query)))
             + greatest(word_similarity(soukly_normalize(:query), soukly_normalize(l.title)),
                        coalesce(word_similarity(soukly_normalize(:query),
                                                 soukly_normalize(l.attributes ->> 'brand')), 0)))::float8""";

    RelevanceRanking() {
        super("relevance", SCORE, true);
    }

    @Override
    public boolean requiresQuery() {
        return true;
    }

    @Override
    public Number cursorKey(Object rankKey) {
        return ((Number) rankKey).doubleValue();
    }

    @Override
    public Object bindKey(Number cursorKey) {
        if (!(cursorKey instanceof Double) || !Double.isFinite(cursorKey.doubleValue())) {
            throw new IllegalArgumentException("A finite score is expected");
        }
        return cursorKey;
    }
}
