package com.soukly.user.password;

/** Unknown, expired, already used or replaced reset token: one answer for every case. */
public class InvalidResetTokenException extends RuntimeException {

    InvalidResetTokenException() {
        super("Invalid reset token", null, false, false);
    }
}
