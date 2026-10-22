package com.soukly.user.registration;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Rules of a new password (AUTH-01), shared by the registration and the password reset (AUTH-04): 8 to 128
 * characters, not in the common password list, not equal to the email.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    private final CommonPasswords commonPasswords;

    PasswordPolicy(CommonPasswords commonPasswords) {
        this.commonPasswords = commonPasswords;
    }

    /**
     * @param email normalized email of the account, {@code null} when unknown
     * @return the code of the first rule the password breaks, empty when it is accepted
     */
    public Optional<String> violation(String password, String email) {
        if (password == null || password.isEmpty()) {
            return Optional.of("password.required");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return Optional.of("password.length");
        }
        if (commonPasswords.contains(password)) {
            return Optional.of("password.too-common");
        }
        if (email != null && password.strip().equalsIgnoreCase(email)) {
            return Optional.of("password.same-as-email");
        }
        return Optional.empty();
    }
}
