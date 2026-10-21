package com.soukly.listing.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import com.soukly.listing.referential.Attribute;
import com.soukly.listing.referential.CategoryFamily;
import com.soukly.listing.referential.Delegation;
import com.soukly.listing.referential.EnumValue;
import com.soukly.listing.referential.ReferentialService;
import com.soukly.listing.referential.Subcategory;

/**
 * Reads the query parameters of {@code GET /listings} into {@link SearchCriteria}, against the referentials of
 * CAT-01. Unknown parameters are ignored (tracking parameters of a shared link); a parameter given twice counts once.
 */
@Component
class SearchCriteriaParser {

    static final String ATTRIBUTE_PREFIX = "attr.";
    private static final String MIN_SUFFIX = ".min";
    private static final String MAX_SUFFIX = ".max";
    private static final long MAX_PRICE = 100_000_000L;

    private final ReferentialService referentials;
    private final Map<String, ListingRankingStrategy> rankings;
    private final SearchCursorCodec cursors;
    private final SearchProperties properties;

    SearchCriteriaParser(ReferentialService referentials, List<ListingRankingStrategy> rankings,
            SearchCursorCodec cursors, SearchProperties properties) {
        this.referentials = referentials;
        this.rankings = rankings.stream()
                .collect(Collectors.toUnmodifiableMap(ListingRankingStrategy::name, Function.identity()));
        this.cursors = cursors;
        this.properties = properties;
    }

    /**
     * @throws InvalidSearchException a parameter that cannot be applied, with its stable code
     */
    SearchCriteria parse(MultiValueMap<String, String> params) {
        String query = SearchText.query(params.getFirst("q"));
        String category = blankToNull(params.getFirst("category"));
        List<String> categoryCodes = category == null ? null : categoryCodes(category);
        String governorate = blankToNull(params.getFirst("governorate"));
        String delegation = blankToNull(params.getFirst("delegation"));
        checkPlace(governorate, delegation);
        Long priceMin = price(params.getFirst("priceMin"));
        Long priceMax = price(params.getFirst("priceMax"));
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
        }
        List<AttributeFilter> attributes = attributes(params, category);
        ListingRankingStrategy ranking = ranking(params.getFirst("sort"), query);
        int limit = limit(params.getFirst("limit"));
        String cursor = blankToNull(params.getFirst("cursor"));
        return new SearchCriteria(query, categoryCodes, governorate, delegation, priceMin, priceMax, attributes,
                ranking, limit, cursor == null ? null : cursors.decode(cursor, ranking));
    }

    /** A subcategory alone, or every active subcategory of a family. */
    private List<String> categoryCodes(String code) {
        if (referentials.findSubcategory(code).isPresent()) {
            return List.of(code);
        }
        return referentials.categories().stream()
                .filter(family -> family.code().equals(code))
                .findFirst()
                .map(CategoryFamily::subcategories)
                .map(subcategories -> subcategories.stream().map(Subcategory::code).toList())
                .orElseThrow(() -> new InvalidSearchException(InvalidSearchException.UNKNOWN_CATEGORY));
    }

    private void checkPlace(String governorate, String delegation) {
        if (governorate != null && referentials.findGovernorate(governorate).isEmpty()) {
            throw new InvalidSearchException(InvalidSearchException.UNKNOWN_PLACE);
        }
        if (delegation != null) {
            Optional<Delegation> found = referentials.findDelegation(delegation);
            if (found.isEmpty() || !found.get().governorateCode().equals(governorate)) {
                throw new InvalidSearchException(InvalidSearchException.UNKNOWN_PLACE);
            }
        }
    }

    private List<AttributeFilter> attributes(MultiValueMap<String, String> params, String category) {
        List<String> names = params.keySet().stream().filter(name -> name.startsWith(ATTRIBUTE_PREFIX)).sorted()
                .toList();
        if (names.isEmpty()) {
            return List.of();
        }
        if (category == null || referentials.findSubcategory(category).isEmpty()) {
            throw new InvalidSearchException(InvalidSearchException.REQUIRES_SUBCATEGORY);
        }
        Map<String, Attribute> filterable = referentials.attributesOf(category).stream()
                .filter(Attribute::filterable)
                .collect(Collectors.toMap(Attribute::code, Function.identity()));
        List<AttributeFilter> filters = new ArrayList<>();
        List<String> ranges = new ArrayList<>();
        for (String name : names) {
            String rest = name.substring(ATTRIBUTE_PREFIX.length());
            String suffix = rest.endsWith(MIN_SUFFIX) ? MIN_SUFFIX : rest.endsWith(MAX_SUFFIX) ? MAX_SUFFIX : null;
            String code = suffix == null ? rest : rest.substring(0, rest.length() - suffix.length());
            Attribute attribute = filterable.get(code);
            if (attribute == null) {
                throw new InvalidSearchException(InvalidSearchException.UNKNOWN_ATTRIBUTE);
            }
            String value = blankToNull(params.getFirst(name));
            if (value == null) {
                throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
            }
            switch (attribute.type()) {
                case INTEGER -> {
                    if (suffix == null) {
                        throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
                    }
                    // Both bounds of one attribute make one filter
                    if (!ranges.contains(code)) {
                        ranges.add(code);
                        Long min = integer(params.getFirst(ATTRIBUTE_PREFIX + code + MIN_SUFFIX));
                        Long max = integer(params.getFirst(ATTRIBUTE_PREFIX + code + MAX_SUFFIX));
                        if (min != null && max != null && min > max) {
                            throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
                        }
                        filters.add(new AttributeFilter.Range(code, min, max));
                    }
                }
                case ENUM -> {
                    requireNoSuffix(suffix);
                    List<String> known = attribute.values().stream().map(EnumValue::code).toList();
                    List<String> values = Arrays.stream(value.split(",")).map(String::strip).distinct().toList();
                    if (!known.containsAll(values)) {
                        throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
                    }
                    filters.add(new AttributeFilter.OneOf(code, values));
                }
                case BOOLEAN -> {
                    requireNoSuffix(suffix);
                    if (!value.equals("true") && !value.equals("false")) {
                        throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
                    }
                    filters.add(new AttributeFilter.Is(code, Boolean.parseBoolean(value)));
                }
                case TEXT -> {
                    requireNoSuffix(suffix);
                    if (value.length() > SearchText.MAX_LENGTH) {
                        throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
                    }
                    filters.add(new AttributeFilter.SameText(code, value.strip()));
                }
            }
        }
        return List.copyOf(filters);
    }

    private ListingRankingStrategy ranking(String sort, String query) {
        String name = blankToNull(sort);
        if (name == null) {
            name = query == null ? "recent" : "relevance";
        }
        ListingRankingStrategy ranking = rankings.get(name);
        if (ranking == null || (ranking.requiresQuery() && query == null)) {
            throw new InvalidSearchException(InvalidSearchException.INVALID_SORT);
        }
        return ranking;
    }

    private int limit(String value) {
        if (blankToNull(value) == null) {
            return properties.defaultLimit();
        }
        try {
            int limit = Integer.parseInt(value.strip());
            if (limit >= 1 && limit <= properties.maxLimit()) {
                return limit;
            }
        } catch (NumberFormatException e) {
            // answered below
        }
        throw new InvalidSearchException(InvalidSearchException.INVALID_LIMIT);
    }

    private static Long price(String value) {
        Long price = integer(value);
        if (price != null && (price < 0 || price > MAX_PRICE)) {
            throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
        }
        return price;
    }

    /** Whole number written in Latin digits (the prices have no millimes). */
    private static Long integer(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (!text.matches("-?[0-9]{1,12}")) {
            throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
        }
        return Long.parseLong(text);
    }

    private static void requireNoSuffix(String suffix) {
        if (suffix != null) {
            throw new InvalidSearchException(InvalidSearchException.INVALID_VALUE);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
