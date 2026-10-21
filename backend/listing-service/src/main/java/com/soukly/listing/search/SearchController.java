package com.soukly.listing.search;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /listings}: public search (CATA-01), {@code GET /api/listings} at the gateway. Not to be confused with
 * {@code GET /listings/{id}} (ANN-01, CATA-03).
 */
@RestController
class SearchController {

    private final SearchCriteriaParser parser;
    private final SearchService search;
    private final SearchProperties properties;

    SearchController(SearchCriteriaParser parser, SearchService search, SearchProperties properties) {
        this.parser = parser;
        this.search = search;
        this.properties = properties;
    }

    @GetMapping("/listings")
    ResponseEntity<SearchPage> search(@RequestParam MultiValueMap<String, String> params) {
        SearchPage page = search.search(parser.parse(params));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(properties.cacheMaxAge()).cachePublic())
                .body(page);
    }
}
