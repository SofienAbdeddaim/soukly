package com.soukly.user.listing;

/**
 * A message that does not follow the contract ({@code docs/events/listing-events.md}): never retried, sent to the
 * DLQ at once. The message never contains the payload.
 */
class InvalidEventException extends RuntimeException {

    InvalidEventException(String message) {
        super(message, null, false, true);
    }
}
