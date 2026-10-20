package com.soukly.user.registration;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalization and syntax check of the email addresses. */
final class EmailAddresses {

    static final int MAX_LENGTH = 254;

    /** One {@code @}, no whitespace, a dot in the domain. Deliverability is proven by the verification link. */
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private EmailAddresses() {
    }

    /** Trims and lower-cases an address; {@code null} stays {@code null}. */
    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** @param email normalized address */
    static boolean isValid(String email) {
        return email.length() <= MAX_LENGTH && FORMAT.matcher(email).matches();
    }
}
