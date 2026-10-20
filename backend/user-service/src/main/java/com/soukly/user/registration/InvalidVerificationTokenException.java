package com.soukly.user.registration;

/** Unknown, expired or already used verification token: one answer for the three cases. */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("Invalid verification token", null, false, false);
    }
}
