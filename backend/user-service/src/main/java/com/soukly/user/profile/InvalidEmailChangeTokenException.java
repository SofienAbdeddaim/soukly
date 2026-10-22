package com.soukly.user.profile;

/** Unknown, expired, already used, replaced or cancelled email change token: one answer for every case. */
public class InvalidEmailChangeTokenException extends RuntimeException {

    InvalidEmailChangeTokenException() {
        super("Invalid email change token", null, false, false);
    }
}
