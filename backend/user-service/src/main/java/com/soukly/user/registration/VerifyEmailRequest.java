package com.soukly.user.registration;

/** Body of {@code POST /users/verify-email}. */
public record VerifyEmailRequest(String token) {

    @Override
    public String toString() {
        return "VerifyEmailRequest[redacted]";
    }
}
