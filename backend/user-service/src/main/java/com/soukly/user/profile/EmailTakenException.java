package com.soukly.user.profile;

/**
 * The confirmed address belongs to another account since the request: {@code 409 email-taken}. Only the owner of
 * that mailbox can see this answer, since it follows the opening of the link.
 */
public class EmailTakenException extends RuntimeException {

    EmailTakenException() {
        super("Email taken", null, false, false);
    }
}
