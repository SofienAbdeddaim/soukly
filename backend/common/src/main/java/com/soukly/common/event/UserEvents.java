package com.soukly.common.event;

/**
 * Topics and types of the user events, produced by user-service (AUTH-05). Contract: {@code docs/events/user-events.md}.
 */
public final class UserEvents {

    /** Every user event, keyed by {@code userId}. */
    public static final String TOPIC = "soukly.user-events";

    /** Messages a consumer could not process, with the cause in the {@code kafka_dlt-*} headers. */
    public static final String DLQ_TOPIC = "soukly.user-events.dlq";

    /** Data: empty object. listing-service deletes the listings of the account. */
    public static final String DELETED = "user.deleted";

    /** {@code schemaVersion} of every type today. */
    public static final int SCHEMA_VERSION = 1;

    private UserEvents() {
    }
}
