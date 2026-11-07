package com.soukly.user.event;

/**
 * A message that does not follow the contract ({@code docs/events/listing-events.md}, {@code social-events.md}): never
 * retried, sent to the DLQ at once. The message never contains the payload.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message, null, false, true);
    }
}
