package com.soukly.user.session;

/** Body of {@code POST /users/login}. */
public record LoginRequest(String email, String password) {

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
