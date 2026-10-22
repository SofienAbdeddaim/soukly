package com.soukly.common.outbox;

import java.time.Duration;

/** What {@link OutboxRelay} reads from the outbox settings of a service. */
public interface OutboxSettings {

    /** Events locked and sent per transaction. */
    int batchSize();

    /** Wait for the acknowledgements of one batch; beyond, the events stay unsent. */
    Duration sendTimeout();

    /** Sent events are deleted after this. */
    Duration retention();
}
