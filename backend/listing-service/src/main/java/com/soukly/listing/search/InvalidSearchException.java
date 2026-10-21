package com.soukly.listing.search;

/** A search parameter that cannot be applied: {@code 400} of type {@code invalid-search} with a stable {@code code}. */
public class InvalidSearchException extends RuntimeException {

    /** {@code q} shorter than 2 or longer than 100 characters once normalized. */
    static final String QUERY_LENGTH = "query.invalid-length";
    /** {@code category} is neither an active family nor an active subcategory. */
    static final String UNKNOWN_CATEGORY = "filter.unknown-category";
    /** Unknown {@code governorate} or {@code delegation}, or a delegation of another governorate (or without one). */
    static final String UNKNOWN_PLACE = "filter.unknown-place";
    /** A malformed number, a {@code priceMin} above {@code priceMax}, an unknown enumeration value... */
    static final String INVALID_VALUE = "filter.invalid-value";
    /** {@code attr.<code>} that the subcategory does not have, or that is not filterable. */
    static final String UNKNOWN_ATTRIBUTE = "filter.unknown-attribute";
    /** {@code attr.<code>} without a subcategory in {@code category}. */
    static final String REQUIRES_SUBCATEGORY = "filter.requires-subcategory";
    /** Unknown {@code sort}, or {@code relevance} without {@code q}. */
    static final String INVALID_SORT = "sort.invalid";
    /** {@code limit} outside 1 to 48. */
    static final String INVALID_LIMIT = "limit.invalid";
    /** A cursor that was changed, or that belongs to another sort. */
    static final String INVALID_CURSOR = "cursor.invalid";

    private final String code;

    InvalidSearchException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
