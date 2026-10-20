package com.soukly.user.registration;

/** Body of {@code POST /users/verify-email/resend}. */
public record ResendVerificationRequest(String email) {

    @Override
    public String toString() {
        return "ResendVerificationRequest[redacted]";
    }
}
