package com.soukly.common.event;

/**
 * Topics and types of the social events, produced by social-publisher (SOC-06). Contract:
 * {@code docs/events/social-events.md}.
 */
public final class SocialEvents {

    /** Every social event, keyed by {@code userId}. */
    public static final String TOPIC = "soukly.social-events";

    /** Messages a consumer could not process, with the cause in the {@code kafka_dlt-*} headers. */
    public static final String DLQ_TOPIC = "soukly.social-events.dlq";

    /**
     * Data: {@link SocialConnectionNeedsReconnectData}. A connection went to {@code NEEDS_RECONNECT}; user-service asks
     * the member to connect it again by email, at most once per connection and per 24 hours.
     */
    public static final String CONNECTION_NEEDS_RECONNECT = "social.connection.needs-reconnect";

    /** {@code schemaVersion} of every type today. */
    public static final int SCHEMA_VERSION = 1;

    private SocialEvents() {
    }
}
