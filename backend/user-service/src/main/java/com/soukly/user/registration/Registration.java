package com.soukly.user.registration;

import com.soukly.user.account.Language;

/**
 * Validated and normalized registration.
 *
 * @param email    trimmed, lower case
 * @param phone    E.164
 * @param language from {@code Accept-Language}
 */
record Registration(String email, String password, String displayName, String phone, Language language) {

    @Override
    public String toString() {
        return "Registration[redacted]";
    }
}
