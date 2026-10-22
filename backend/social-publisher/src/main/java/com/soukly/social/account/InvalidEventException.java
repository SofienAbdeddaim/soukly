package com.soukly.social.account;

/**
 * A message that does not follow the contract ({@code docs/events/user-events.md}): never retried, sent to the DLQ at
 * once. The message never contains the payload.
 */
class InvalidEventException extends RuntimeException {

    InvalidEventException(String message) {
        super(message, null, false, true);
    }
}
