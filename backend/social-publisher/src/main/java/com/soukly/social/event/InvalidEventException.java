package com.soukly.social.event;

/**
 * A message that does not follow its contract ({@code docs/events/user-events.md}, {@code listing-events.md}): never
 * retried, sent to the DLQ at once. The message never contains the payload.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message, null, false, true);
    }
}
