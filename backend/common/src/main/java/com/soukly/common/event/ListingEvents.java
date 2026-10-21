package com.soukly.common.event;

/**
 * Topics and types of the listing events, produced by listing-service (ANN-03, ANN-05). Contract:
 * {@code docs/events/listing-events.md}.
 */
public final class ListingEvents {

    /** Every listing event, keyed by {@code listingId}. */
    public static final String TOPIC = "soukly.listing-events";

    /** Messages a consumer could not process, with the cause in the {@code kafka_dlt-*} headers. */
    public static final String DLQ_TOPIC = "soukly.listing-events.dlq";

    /** Data: {@link ListingPublishedData}. */
    public static final String PUBLISHED = "listing.published";

    /** Data: {@link ListingExpiryData}. Never posted again on the social networks. */
    public static final String RENEWED = "listing.renewed";

    /** Data: {@link ListingExpiryData}. */
    public static final String EXPIRING_SOON = "listing.expiring-soon";

    /** Data: {@link ListingExpiryData}. */
    public static final String EXPIRED = "listing.expired";

    /** Data: {@link ListingUpdatedData}. Ignored by social-publisher in V1: a change is never posted again. */
    public static final String UPDATED = "listing.updated";

    /** Data: empty object. */
    public static final String DEACTIVATED = "listing.deactivated";

    /** Data: empty object. social-publisher removes the posts of the listing (SOC-04, SOC-05). */
    public static final String DELETED = "listing.deleted";

    /** {@code schemaVersion} of every type today. */
    public static final int SCHEMA_VERSION = 1;

    private ListingEvents() {
    }
}
