package com.soukly.listing.listing;

import java.util.regex.Pattern;

/** Normalization and length of the texts entered by the seller. Texts are plain: HTML is kept as text. */
final class Texts {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private Texts() {
    }

    /** Trimmed, every run of whitespace (line breaks included) replaced by one space; case kept. */
    static String singleLine(String text) {
        return WHITESPACE.matcher(text.strip()).replaceAll(" ");
    }

    /** Trimmed only: line breaks inside a description are kept. */
    static String multiLine(String text) {
        return text.strip();
    }

    /** Length in characters (Unicode code points). */
    static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /** Control characters other than line breaks and tabs (NUL, escape...) are refused. */
    static boolean hasForbiddenControl(String text) {
        return text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }
}
