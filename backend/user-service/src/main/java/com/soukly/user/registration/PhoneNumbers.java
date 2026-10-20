package com.soukly.user.registration;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Validates Tunisian phone numbers with libphonenumber (region {@code TN}, mobile and fixed lines) and formats
 * them in E.164.
 */
final class PhoneNumbers {

    private static final String TUNISIA = "TN";
    /** Digits, spaces and the usual separators only: no letters (vanity numbers) and no extension. */
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("^\\+?[0-9 .()-]{1,30}$");
    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /**
     * Returns the E.164 form of a Tunisian number, for example {@code +21620123456} for {@code 20 123 456},
     * {@code +216 20123456} or {@code 0021620123456}; empty when the number is not a valid Tunisian number.
     */
    static Optional<String> toE164(String input) {
        if (input == null || !ALLOWED_CHARACTERS.matcher(input.strip()).matches()) {
            return Optional.empty();
        }
        try {
            PhoneNumber number = UTIL.parse(input.strip(), TUNISIA);
            if (!UTIL.isValidNumberForRegion(number, TUNISIA)) {
                return Optional.empty();
            }
            return Optional.of(UTIL.format(number, PhoneNumberFormat.E164));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }
}
