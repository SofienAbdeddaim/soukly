package com.soukly.social.caption;

import java.util.regex.Pattern;

/**
 * Removes the email addresses and the phone numbers a seller may have typed in a title or a description: a text
 * posted on a social network never carries personal data (SOC-03). The listing page still shows the text as written.
 */
public final class PersonalData {

    static final String MASK = "[…]";

    private static final Pattern EMAIL = Pattern.compile(
            "[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)*\\.\\p{L}{2,}");

    /**
     * A Tunisian number: 8 digits starting with 2 to 9, optionally after {@code +216} or {@code 00216}, with spaces,
     * dots or dashes between the digits ({@code 20 123 456}, {@code 98.12.34.56}, {@code +216 20123456}). Longer runs
     * of digits are not numbers of Tunisia, and an amount followed by the currency ({@code 25 000 000 DT}) is kept.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\p{N}+])(?:(?:\\+|00)\\s?216[\\s.-]?)?[2-9](?:[\\s.-]?\\d){7}(?![\\p{N}])"
                    + "(?!\\s?(?:DT|TND|dinars?|د\\.?ت|دينار))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private PersonalData() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        return PHONE.matcher(EMAIL.matcher(text).replaceAll(MASK)).replaceAll(MASK);
    }
}
