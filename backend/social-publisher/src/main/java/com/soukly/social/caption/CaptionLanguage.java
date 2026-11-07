package com.soukly.social.caption;

import java.util.Locale;
import java.util.Optional;

/**
 * Languages of the post texts (SOC-03), with the words of the template. Prices are formatted like the {@code price}
 * pipe of the front end (SETUP-07): {@code 32 000 DT}, {@code 32.000 د.ت.}, {@code TND 32,000}, with the
 * non-breaking spaces of {@code Intl} and Latin digits in every language.
 */
public enum CaptionLanguage {

    FR("fr", "Négociable", "Sur devis", "Voir l'annonce : ", "Annonce complète sur ", ", ") {
        @Override
        String price(long dinars) {
            return group(dinars, NARROW_NO_BREAK_SPACE) + NO_BREAK_SPACE + "DT";
        }
    },
    AR("ar", "قابل للتفاوض", "حسب الطلب", "شاهد الإعلان: ", "الإعلان كاملًا على ", "، ") {
        @Override
        String price(long dinars) {
            return group(dinars, '.') + NO_BREAK_SPACE + "د.ت.";
        }
    },
    EN("en", "Negotiable", "On quote", "See the listing: ", "Full listing on ", ", ") {
        @Override
        String price(long dinars) {
            return "TND" + NO_BREAK_SPACE + group(dinars, ',');
        }
    };

    static final char NO_BREAK_SPACE = ' ';
    static final char NARROW_NO_BREAK_SPACE = ' ';

    private final String code;
    private final String negotiable;
    private final String onQuote;
    private final String seeListing;
    private final String fullListingOn;
    private final String listSeparator;

    CaptionLanguage(String code, String negotiable, String onQuote, String seeListing, String fullListingOn,
            String listSeparator) {
        this.code = code;
        this.negotiable = negotiable;
        this.onQuote = onQuote;
        this.seeListing = seeListing;
        this.fullListingOn = fullListingOn;
        this.listSeparator = listSeparator;
    }

    /** {@code fr}, {@code ar} or {@code en}. */
    public String code() {
        return code;
    }

    public static Optional<CaptionLanguage> of(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String lower = code.toLowerCase(Locale.ROOT);
        for (CaptionLanguage language : values()) {
            if (language.code.equals(lower)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /** A price in whole dinars. */
    abstract String price(long dinars);

    String negotiable() {
        return negotiable;
    }

    String onQuote() {
        return onQuote;
    }

    String seeListing() {
        return seeListing;
    }

    /** Before the short address of the listing on Instagram, where links are not clickable (SOC-05). */
    String fullListingOn() {
        return fullListingOn;
    }

    /** Between the governorate and the delegation: the Arabic comma in Arabic. */
    String listSeparator() {
        return listSeparator;
    }

    private static String group(long value, char separator) {
        String digits = Long.toString(value);
        StringBuilder grouped = new StringBuilder(digits.length() + digits.length() / 3);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append(separator);
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
