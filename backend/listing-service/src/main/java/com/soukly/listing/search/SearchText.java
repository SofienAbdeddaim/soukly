package com.soukly.listing.search;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalization of the searched text, the Java mirror of the SQL function {\code soukly_normalize} (V9): lower case,
 * Arabic diacritics (tashkil, superscript alef) and tatweel removed, alef variants, teh marbuta and alef maksura
 * unified, Latin accents removed. PostgreSQL normalizes the query again with the same function as the index; this
 * class decides the length of the query and is tested against the SQL function.
 */
final class SearchText {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 100;

    private static final Pattern ARABIC_MARKS = Pattern.compile("[\u064B-\u0652\u0670\u0640]");
    // Combining marks left by the decomposition of the Latin letters (é → e + ◌́); unaccent leaves the Arabic letters
    // with a hamza (ؤ, ئ) as they are
    private static final Pattern LATIN_MARKS = Pattern.compile("(?<=\\p{IsLatin})\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchText() {
    }

    static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String arabic = ARABIC_MARKS.matcher(lower).replaceAll("")
                .replace('\u0623', '\u0627') // أ → ا
                .replace('\u0625', '\u0627') // إ → ا
                .replace('\u0622', '\u0627') // آ → ا
                .replace('\u0629', '\u0647') // ة → ه
                .replace('\u0649', '\u064A'); // ى → ي
        // Ligatures and letters that unaccent rewrites but that have no decomposition
        String latin = arabic.replace("\u0153", "oe").replace("\u00E6", "ae").replace("\u00DF", "ss")
                .replace('\u00F8', 'o').replace('\u0111', 'd').replace('\u0142', 'l');
        String decomposed = Normalizer.normalize(latin, Normalizer.Form.NFD);
        return Normalizer.normalize(LATIN_MARKS.matcher(decomposed).replaceAll(""), Normalizer.Form.NFC);
    }

    /**
     * The query as searched: trimmed, its spaces collapsed.
     *
     * \return null for a blank query
     * \throws InvalidSearchException {\code query.invalid-length} outside 2 to 100 characters once normalized
     */
    static String query(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String query = SPACES.matcher(raw.strip()).replaceAll(" ");
        String normalized = normalize(query);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new InvalidSearchException(InvalidSearchException.QUERY_LENGTH);
        }
        return query;
    }
}
