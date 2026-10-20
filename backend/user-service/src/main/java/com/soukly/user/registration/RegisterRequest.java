package com.soukly.user.registration;

/**
 * Body of {@code POST /users/register}. Every field may be missing; {@link RegistrationValidator} checks them.
 */
public record RegisterRequest(String email, String password, String displayName, String phone,
        Boolean acceptTerms) {

    /** Never prints the password or the personal data. */
    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
