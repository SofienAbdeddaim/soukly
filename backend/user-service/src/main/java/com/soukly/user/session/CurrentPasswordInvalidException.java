package com.soukly.user.session;

/** The current password sent with a profile action is wrong: {@code 400 current-password-invalid}. */
public class CurrentPasswordInvalidException extends RuntimeException {

    CurrentPasswordInvalidException() {
        super("Current password invalid", null, false, false);
    }
}
