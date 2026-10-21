package com.soukly.listing.search;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.listing.listing.ListingKind;
import com.soukly.listing.listing.PriceMode;
import com.soukly.listing.photo.ListingPhotos;
import com.soukly.listing.referential.Attribute;
import com.soukly.listing.referential.ReferentialService;

/** Public search of the PUBLISHED listings (CATA-01). */
@Service
@EnableConfigurationProperties(SearchProperties.class)
public class SearchService {

    static final String CURRENCY = "TND";
    static final int SUMMARY_SIZE = 3;

    private final SearchRepository repository;
    private final SearchCursorCodec cursors;
    private final ReferentialService referentials;
    private final ListingPhotos photos;
    private final SearchProperties properties;
    private final Clock clock;

    SearchService(SearchRepository repository, SearchCursorCodec cursors, ReferentialService referentials,
            ListingPhotos photos, SearchProperties properties, Clock clock) {
        this.repository = repository;
        this.cursors = cursors;
        this.referentials = referentials;
        this.photos = photos;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    SearchPage search(SearchCriteria criteria) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (criteria.query() != null) {
            repository.useSimilarityThreshold(properties.similarityThreshold());
        }
        // One row more than the page tells whether another page follows
        List<SearchRow> rows = repository.page(criteria, now, criteria.limit() + 1);
        boolean more = rows.size() > criteria.limit();
        List<SearchRow> page = more ? rows.subList(0, criteria.limit()) : rows;
        String nextCursor = null;
        if (more) {
            SearchRow last = page.getLast();
            ListingRankingStrategy ranking = criteria.ranking();
            nextCursor = cursors.encode(new SearchCursor(ranking.name(), ranking.cursorKey(last.rankKey()),
                    last.publishedAt(), last.id()));
        }
        Long total = criteria.cursor() == null ? repository.count(criteria, now) : null;
        return new SearchPage(page.stream().map(this::card).toList(), nextCursor, total);
    }

    private SearchCard card(SearchRow row) {
        return new SearchCard(row.id(), row.title(), ListingKind.of(row.categoryCode()), row.categoryCode(),
                PriceMode.valueOf(row.priceMode()), row.priceTnd(), CURRENCY, row.negotiable(),
                row.governorateCode(), row.delegationCode(),
                row.coverId() == null ? null : photos.thumbnailUrl(row.id(), row.coverId()), row.photoCount(),
                summary(row), row.publishedAt());
    }

    /** The first attributes of the listing in the display order of its subcategory. */
    private Map<String, Object> summary(SearchRow row) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (referentials.findSubcategory(row.categoryCode()).isEmpty()) {
            return summary;
        }
        for (Attribute attribute : referentials.attributesOf(row.categoryCode())) {
            Object value = row.attributes().get(attribute.code());
            if (value != null && summary.size() < SUMMARY_SIZE) {
                summary.put(attribute.code(), value);
            }
        }
        return summary;
    }
}
