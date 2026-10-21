package com.soukly.listing.listing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.soukly.listing.referential.Attribute;
import com.soukly.listing.referential.ReferentialService;

/**
 * Completeness of a listing ({@code missingFields}) and display order of its attributes, both from the definitions
 * of its subcategory (CAT-01). Photos join {@code missingFields} with ANN-02.
 */
@Component
class ListingViews {

    private final ReferentialService referentials;

    ListingViews(ReferentialService referentials) {
        this.referentials = referentials;
    }

    ListingView view(Listing listing, List<String> warnings) {
        ListingContent content = listing.content();
        List<Attribute> definitions = definitions(content.categoryCode());
        Listing ordered = new Listing(listing.id(), listing.ownerId(), listing.status(), ordered(content, definitions),
                listing.version(), listing.createdAt(), listing.updatedAt(), listing.publishedAt());
        return new ListingView(ordered, missingFields(content, definitions), warnings);
    }

    /** Required fields still empty, in the order of the form. */
    private static List<String> missingFields(ListingContent content, List<Attribute> definitions) {
        List<String> missing = new ArrayList<>();
        if (definitions == null) {
            // The category was deactivated since the draft was created: another one must be chosen.
            missing.add(DraftEditor.CATEGORY_CODE);
        }
        if (content.title() == null) {
            missing.add(DraftEditor.TITLE);
        }
        if (content.description() == null) {
            missing.add(DraftEditor.DESCRIPTION);
        }
        if (content.priceMode() == null) {
            missing.add(DraftEditor.PRICE_MODE);
        } else if (content.priceMode() == PriceMode.FIXED && content.price() == null) {
            missing.add(DraftEditor.PRICE);
        }
        if (content.governorateCode() == null) {
            missing.add(DraftEditor.GOVERNORATE_CODE);
        }
        if (definitions != null) {
            definitions.stream()
                    .filter(attribute -> attribute.required() && !content.attributes().containsKey(attribute.code()))
                    .forEach(attribute -> missing.add(DraftEditor.ATTRIBUTES + "." + attribute.code()));
        }
        return List.copyOf(missing);
    }

    /** Attributes in the display order of the subcategory (JSONB does not keep the order of the keys). */
    private static ListingContent ordered(ListingContent content, List<Attribute> definitions) {
        if (definitions == null) {
            return content;
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        definitions.stream().map(Attribute::code).filter(content.attributes()::containsKey)
                .forEach(code -> ordered.put(code, content.attributes().get(code)));
        content.attributes().forEach(ordered::putIfAbsent);
        return new ListingContent(content.categoryCode(), content.title(), content.description(),
                content.priceMode(), content.price(), content.negotiable(), content.governorateCode(),
                content.delegationCode(), ordered);
    }

    /** Attributes of the subcategory, or null when it is no longer active. */
    private List<Attribute> definitions(String categoryCode) {
        return referentials.findSubcategory(categoryCode).isPresent()
                ? referentials.attributesOf(categoryCode)
                : null;
    }
}
