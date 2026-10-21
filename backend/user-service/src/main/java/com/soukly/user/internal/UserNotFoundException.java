package com.soukly.user.internal;

/** No active account has this id: unknown, not verified yet, or closed (AUTH-05). */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super(null, null, false, false);
    }
}
