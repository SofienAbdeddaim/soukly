package com.soukly.social.caption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The single source of the post texts (SOC-03), used by the preview of the publish form and by the publication on
 * Facebook (SOC-04) and Instagram (SOC-05):
 *
 * <pre>
 * {title}
 * {price} · Négociable            (or "Sur devis")
 * {governorate}, {delegation}
 *
 * {description, cut at 300 characters on the last word, then "…"}
 *
 * Voir l'annonce : {publicUrl}
 * #Soukly #{Governorate} #{Category}
 * </pre>
 *
 * The text never carries the name, phone number or email of the seller: none is an input, and those typed in the
 * title or the description are removed ({@link PersonalData}). Lengths are counted in code points.
 */
@Component
public class CaptionBuilder {

    /** Longest text: below the 2,200 characters of an Instagram caption, the strictest of the two networks. */
    public static final int MAX_LENGTH = 2_000;
    public static final int DESCRIPTION_MAX = 300;
    static final String ELLIPSIS = "…";
    static final String BRAND_HASHTAG = "#Soukly";
    private static final String PRICE_SEPARATOR = " · ";

    public Caption build(CaptionInput input) {
        CaptionLanguage language = input.language();
        List<String> hashtags = hashtags(input);
        String tail = language.seeListing() + input.publicUrl() + "\n" + String.join(" ", hashtags);
        String price = priceLine(input);
        String place = place(input);
        String title = nonBlank(PersonalData.mask(input.title()));
        if (title != null) {
            // Never reached with the 80 characters of a real title: the template stays within the limit anyway
            int others = length(tail) + (price == null ? 0 : length(price) + 1)
                    + (place == null ? 0 : length(place) + 1);
            title = truncate(title, Math.max(MAX_LENGTH - others - 2, length(ELLIPSIS) + 1));
        }
        String head = join(Arrays.asList(title, price, place));
        String description = PersonalData.mask(blankToNull(input.description()));
        // The description is the only part of unbounded length: it gives way when the whole text would be too long
        int budget = MAX_LENGTH - length(head) - length(tail) - 4;
        String text = description == null || budget <= length(ELLIPSIS)
                ? joinParagraphs(head, tail)
                : joinParagraphs(head, truncate(description, Math.min(DESCRIPTION_MAX, budget)), tail);
        return new Caption(text, hashtags);
    }

    private static String priceLine(CaptionInput input) {
        CaptionLanguage language = input.language();
        if (CaptionInput.ON_QUOTE.equals(input.priceMode())) {
            return language.onQuote();
        }
        if (!CaptionInput.FIXED.equals(input.priceMode()) || input.priceTnd() == null) {
            return null;
        }
        String price = language.price(input.priceTnd());
        return input.negotiable() ? price + PRICE_SEPARATOR + language.negotiable() : price;
    }

    private static String place(CaptionInput input) {
        String governorate = nonBlank(input.governorateLabel());
        String delegation = nonBlank(input.delegationLabel());
        if (governorate == null) {
            return null;
        }
        return delegation == null || delegation.equals(governorate) ? governorate : governorate + input.language().listSeparator() + delegation;
    }

    private static List<String> hashtags(CaptionInput input) {
        Set<String> hashtags = new LinkedHashSet<>();
        hashtags.add(BRAND_HASHTAG);
        for (String label : new String[] { input.governorateLabel(), input.categoryLabel() }) {
            String hashtag = hashtag(label);
            if (hashtag != null) {
                hashtags.add(hashtag);
            }
        }
        return List.copyOf(hashtags);
    }

    /**
     * A label as a hashtag, without spaces nor punctuation: Latin words are joined with a capital letter
     * ({@code Ben Arous} → {@code #BenArous}), words of a script without capitals (Arabic) with {@code _}, as is usual
     * for Arabic hashtags ({@code دراجات نارية} → {@code #دراجات_نارية}).
     */
    static String hashtag(String label) {
        if (label == null) {
            return null;
        }
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        label.codePoints().forEach(c -> {
            if (Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK) {
                word.appendCodePoint(c);
            } else if (!word.isEmpty()) {
                words.add(word.toString());
                word.setLength(0);
            }
        });
        if (!word.isEmpty()) {
            words.add(word.toString());
        }
        if (words.isEmpty()) {
            return null;
        }
        StringBuilder hashtag = new StringBuilder("#");
        for (int i = 0; i < words.size(); i++) {
            String current = words.get(i);
            int first = current.codePointAt(0);
            boolean cased = Character.isUpperCase(first) || Character.isLowerCase(first);
            if (i > 0 && !cased) {
                hashtag.append('_');
            }
            hashtag.append(cased ? capitalize(current) : current);
        }
        return hashtag.toString();
    }

    /** At most {@code max} code points, cut after the last whole word, then "…". */
    static String truncate(String text, int max) {
        String trimmed = text.strip();
        if (length(trimmed) <= max) {
            return trimmed;
        }
        int keep = max - length(ELLIPSIS);
        String cut = trimmed.substring(0, trimmed.offsetByCodePoints(0, keep));
        int next = trimmed.codePointAt(cut.length());
        if (!Character.isWhitespace(next)) {
            // In the middle of a word: go back to the end of the previous one, unless the text is one long word
            int space = lastWhitespace(cut);
            if (space > 0) {
                cut = cut.substring(0, space);
            }
        }
        return stripEndPunctuation(cut) + ELLIPSIS;
    }

    private static int lastWhitespace(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String stripEndPunctuation(String text) {
        int end = text.length();
        while (end > 0) {
            int c = text.codePointBefore(end);
            int type = Character.getType(c);
            boolean drop = Character.isWhitespace(c) || type == Character.OTHER_PUNCTUATION
                    || type == Character.DASH_PUNCTUATION;
            if (!drop) {
                break;
            }
            end -= Character.charCount(c);
        }
        return text.substring(0, end);
    }

    private static String capitalize(String word) {
        int first = word.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toTitleCase(first))
                .append(word, Character.charCount(first), word.length()).toString();
    }

    private static String join(List<String> lines) {
        return String.join("\n", lines.stream().filter(line -> line != null).toList());
    }

    private static String joinParagraphs(String... paragraphs) {
        List<String> kept = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (!paragraph.isEmpty()) {
                kept.add(paragraph);
            }
        }
        return String.join("\n\n", kept);
    }

    private static String nonBlank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String blankToNull(String text) {
        return nonBlank(text);
    }

    static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
