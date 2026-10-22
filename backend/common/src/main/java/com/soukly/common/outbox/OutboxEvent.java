package com.soukly.common.outbox;

import java.util.UUID;

/**
 * An unsent event as the relay reads it.
 *
 * @param sequence    order of creation
 * @param aggregateId listing or account id, the message key
 * @param payload     the whole envelope, in JSON
 * @param attempts    failed sends so far
 */
record OutboxEvent(UUID id, long sequence, UUID aggregateId, String type, String payload, int attempts) {
}
