package com.soukly.social.caption;

import java.net.URI;

/**
 * What a post text is made of, with the labels of the referentials already in the language of the text. Every field
 * but the language and the link may be empty (a draft being previewed): its line is then left out.
 *
 * @param priceMode       {@code FIXED} or {@code ON_QUOTE}, or {@code null}
 * @param priceTnd        whole dinars, with {@code FIXED}
 * @param categoryLabel   label of the subcategory (CAT-01)
 * @param governorateLabel label of the governorate (CAT-01)
 * @param delegationLabel label of the delegation (CAT-01), or {@code null}
 * @param publicUrl       page of the listing
 */
public record CaptionInput(String title, String description, String priceMode, Long priceTnd, boolean negotiable,
        String categoryLabel, String governorateLabel, String delegationLabel, URI publicUrl,
        CaptionLanguage language) {

    public static final String FIXED = "FIXED";
    public static final String ON_QUOTE = "ON_QUOTE";
}
