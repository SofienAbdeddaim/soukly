package com.soukly.listing.listing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Names of the members of a listing that a change touched, for {@code changedFields} of {@code listing.updated}. */
final class ListingChanges {

    private ListingChanges() {
    }

    /** Members of the API ({@code price}, {@code attributes}...) whose value differs, in form order. */
    static List<String> between(ListingContent before, ListingContent after) {
        List<String> changed = new ArrayList<>();
        add(changed, DraftEditor.CATEGORY_CODE, before.categoryCode(), after.categoryCode());
        add(changed, DraftEditor.TITLE, before.title(), after.title());
        add(changed, DraftEditor.DESCRIPTION, before.description(), after.description());
        add(changed, DraftEditor.PRICE_MODE, before.priceMode(), after.priceMode());
        add(changed, DraftEditor.PRICE, before.price(), after.price());
        add(changed, DraftEditor.NEGOTIABLE, before.negotiable(), after.negotiable());
        add(changed, DraftEditor.GOVERNORATE_CODE, before.governorateCode(), after.governorateCode());
        add(changed, DraftEditor.DELEGATION_CODE, before.delegationCode(), after.delegationCode());
        add(changed, DraftEditor.ATTRIBUTES, before.attributes(), after.attributes());
        return List.copyOf(changed);
    }

    private static void add(List<String> changed, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(name);
        }
    }
}
