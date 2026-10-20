package com.soukly.user.account;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Languages of Soukly. The preferred language of an account is used for all its emails.
 */
public enum Language {

    FR("fr"),
    AR("ar"),
    EN("en");

    public static final Language DEFAULT = FR;

    private static final List<String> CODES = Arrays.stream(values()).map(Language::code).toList();

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    public boolean isRightToLeft() {
        return this == AR;
    }

    public static Language fromCode(String code) {
        return Arrays.stream(values()).filter(language -> language.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + code));
    }

    /**
     * Picks the best supported language of an {@code Accept-Language} header, {@link #DEFAULT} when the header is
     * missing, malformed or names no supported language.
     */
    public static Language fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT;
        }
        try {
            String code = Locale.lookupTag(Locale.LanguageRange.parse(header), CODES);
            return code == null ? DEFAULT : fromCode(code);
        } catch (IllegalArgumentException malformed) {
            return DEFAULT;
        }
    }
}
