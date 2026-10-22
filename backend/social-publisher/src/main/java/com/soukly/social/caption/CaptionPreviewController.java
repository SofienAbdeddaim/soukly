package com.soukly.social.caption;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.social.web.FieldError;
import com.soukly.social.web.InvalidRequestException;

/**
 * {@code POST /social/caption-preview} (SOC-03): the text that will be posted for a draft, shown on the preview of
 * the publish form. The front end never builds the text itself. Every member of the access token may call it; the
 * body carries only what the seller typed in the listing, never their name or phone number.
 */
@RestController
class CaptionPreviewController {

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 10_000;
    static final int URL_MAX = 500;
    static final long PRICE_MAX = 100_000_000;

    private final CaptionBuilder builder;
    private final ReferentialLabels labels;

    CaptionPreviewController(CaptionBuilder builder, ReferentialLabels labels) {
        this.builder = builder;
        this.labels = labels;
    }

    /**
     * @throws InvalidRequestException     an invalid member, or an unknown code of the referentials
     * @throws CaptionUnavailableException listing-service did not give the labels
     */
    @PostMapping("/social/caption-preview")
    Caption preview(@RequestBody PreviewRequest body) {
        List<FieldError> errors = new ArrayList<>();
        Optional<CaptionLanguage> language = CaptionLanguage.of(body.language());
        if (language.isEmpty()) {
            errors.add(new FieldError("language", "language.invalid"));
        }
        URI publicUrl = publicUrl(body.publicUrl(), errors);
        String priceMode = body.priceMode();
        if (priceMode != null && !CaptionInput.FIXED.equals(priceMode) && !CaptionInput.ON_QUOTE.equals(priceMode)) {
            errors.add(new FieldError("priceMode", "price-mode.invalid"));
        }
        if (body.priceTnd() != null && (body.priceTnd() < 1 || body.priceTnd() > PRICE_MAX)) {
            errors.add(new FieldError("priceTnd", "price.out-of-range"));
        }
        tooLong(body.title(), TITLE_MAX, "title", errors);
        tooLong(body.description(), DESCRIPTION_MAX, "description", errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        CaptionLanguage lang = language.orElseThrow();
        String category = label(labels.subcategory(body.categoryCode()), body.categoryCode(), "categoryCode",
                "category.unknown", lang, errors);
        String governorate = label(labels.governorate(body.governorateCode()), body.governorateCode(),
                "governorateCode", "governorate.unknown", lang, errors);
        String delegation = body.delegationCode() == null || body.governorateCode() == null ? null
                : label(labels.delegation(body.governorateCode(), body.delegationCode()), body.delegationCode(),
                        "delegationCode", "delegation.unknown", lang, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return builder.build(new CaptionInput(body.title(), body.description(), priceMode,
                CaptionInput.FIXED.equals(priceMode) ? body.priceTnd() : null, Boolean.TRUE.equals(body.negotiable()),
                category, governorate, delegation, publicUrl, lang));
    }

    private static URI publicUrl(String value, List<FieldError> errors) {
        try {
            URI uri = value == null || value.length() > URL_MAX ? null : URI.create(value);
            if (uri != null && uri.getHost() != null
                    && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                return uri;
            }
        } catch (IllegalArgumentException e) {
            // reported below
        }
        errors.add(new FieldError("publicUrl", "public-url.invalid"));
        return null;
    }

    private static void tooLong(String value, int max, String field, List<FieldError> errors) {
        if (value != null && value.codePointCount(0, value.length()) > max) {
            errors.add(new FieldError(field, field + ".too-long"));
        }
    }

    private static String label(Optional<ReferentialLabels.Labels> found, String code, String field, String unknown,
            CaptionLanguage language, List<FieldError> errors) {
        if (code == null) {
            return null;
        }
        if (found.isEmpty()) {
            errors.add(new FieldError(field, unknown));
            return null;
        }
        return found.get().in(language);
    }

    /**
     * Body of the preview: the fields of the draft as the publish form holds them, the link of the listing page and
     * the language of the text.
     *
     * @param priceTnd whole dinars
     */
    record PreviewRequest(String title, String description, String priceMode, Long priceTnd, Boolean negotiable,
            String categoryCode, String governorateCode, String delegationCode, String publicUrl, String language) {
    }
}
