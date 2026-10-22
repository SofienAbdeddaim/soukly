package com.soukly.user.password;

/** Body of {@code POST /users/password/reset}; never logged. */
public record PasswordResetRequest(String token, String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[]";
    }
}
