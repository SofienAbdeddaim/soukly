package com.soukly.listing.listing;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.soukly.listing.referential.Attribute;
import com.soukly.listing.referential.Delegation;
import com.soukly.listing.referential.ReferentialService;
import com.soukly.listing.referential.Subcategory;

import tools.jackson.databind.JsonNode;

/**
 * Applies a creation body or a JSON Merge Patch (RFC 7396) to the content of a draft and validates every value sent
 * at once. Completeness is not required here: see {@link Completeness}.
 *
 * <p>The patch is read as a {@link JsonNode} and applied field by field, which keeps the three states of a member
 * apart: absent (unchanged), {@code null} (cleared) and a value (replaced). {@code attributes} is merged member by
 * member, like any JSON object in a Merge Patch.
 *
 * <p>The content after the patch must be coherent: a price is refused with {@code ON_QUOTE}, a delegation must
 * belong to the governorate. The only automatic changes are those of a category change: attributes the new
 * subcategory does not define are removed, and {@code ON_QUOTE} is cleared when it is no longer allowed.
 */
@Component
class DraftEditor {

    static final String CATEGORY_CODE = "categoryCode";
    static final String TITLE = "title";
    static final String DESCRIPTION = "description";
    static final String PRICE_MODE = "priceMode";
    static final String PRICE = "price";
    static final String NEGOTIABLE = "negotiable";
    static final String GOVERNORATE_CODE = "governorateCode";
    static final String DELEGATION_CODE = "delegationCode";
    static final String ATTRIBUTES = "attributes";
    static final String SHARE_TO = "shareTo";
    static final String SHARE_LANGUAGE = "shareLanguage";

    private static final Set<String> FIELDS = Set.of(CATEGORY_CODE, TITLE, DESCRIPTION, PRICE_MODE, PRICE,
            NEGOTIABLE, GOVERNORATE_CODE, DELEGATION_CODE, ATTRIBUTES, SHARE_TO, SHARE_LANGUAGE);

    /** Languages of the post texts (SOC-03): those of the site. */
    static final Set<String> SHARE_LANGUAGES = Set.of("fr", "ar", "en");

    static final int TITLE_MIN = 5;
    static final int TITLE_MAX = 80;
    static final int DESCRIPTION_MIN = 20;
    static final int DESCRIPTION_MAX = 4_000;
    static final long PRICE_MIN = 1;
    static final long PRICE_MAX = 100_000_000;

    static final String WARNING_ATTRIBUTES_REMOVED = "attributes-removed";
    static final String WARNING_PRICE_MODE_RESET = "price-mode-reset";

    private final ReferentialService referentials;
    private final Clock clock;

    DraftEditor(ReferentialService referentials, Clock clock) {
        this.referentials = referentials;
        this.clock = clock;
    }

    /** The validated content and the automatic changes it caused ({@code warnings}). */
    record Edit(ListingContent content, List<String> warnings) {
    }

    /** Content of a new draft: only {@code categoryCode} is required. */
    Edit create(JsonNode body) {
        return apply(null, body);
    }

    Edit update(ListingContent current, JsonNode patch) {
        return apply(current, patch);
    }

    private Edit apply(ListingContent current, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new InvalidListingException(List.of(new FieldError("body", "body.not-an-object")));
        }
        List<FieldError> errors = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>();
        for (String name : patch.propertyNames()) {
            if (!FIELDS.contains(name)) {
                errors.add(new FieldError(name, "field.unknown"));
            }
        }
        Draft draft = current == null ? new Draft() : new Draft(current);

        // Category first: the price mode and the attributes are checked against the new subcategory.
        Map<String, Attribute> definitions = category(draft, current, patch, errors, warnings);

        if (patch.has(TITLE)) {
            draft.title = text(patch.get(TITLE), TITLE, "title", false, TITLE_MIN, TITLE_MAX, errors);
        }
        if (patch.has(DESCRIPTION)) {
            draft.description = text(patch.get(DESCRIPTION), DESCRIPTION, "description", true, DESCRIPTION_MIN,
                    DESCRIPTION_MAX, errors);
        }
        if (patch.has(PRICE_MODE)) {
            draft.priceMode = priceMode(patch.get(PRICE_MODE), draft.categoryCode, errors);
        }
        if (patch.has(PRICE)) {
            draft.price = price(patch.get(PRICE), errors);
        }
        if (patch.has(NEGOTIABLE)) {
            JsonNode value = patch.get(NEGOTIABLE);
            if (value.isNull()) {
                draft.negotiable = false;
            } else if (value.isBoolean()) {
                draft.negotiable = value.booleanValue();
            } else {
                errors.add(new FieldError(NEGOTIABLE, "negotiable.invalid"));
            }
        }
        if (patch.has(GOVERNORATE_CODE)) {
            draft.governorateCode = code(patch.get(GOVERNORATE_CODE), GOVERNORATE_CODE, "governorate.unknown",
                    code -> referentials.findGovernorate(code).isPresent(), errors);
        }
        if (patch.has(DELEGATION_CODE)) {
            draft.delegationCode = code(patch.get(DELEGATION_CODE), DELEGATION_CODE, "delegation.unknown",
                    code -> referentials.findDelegation(code).isPresent(), errors);
        }
        if (patch.has(ATTRIBUTES) && definitions != null) {
            attributes(draft, patch.get(ATTRIBUTES), definitions, errors);
        }
        if (patch.has(SHARE_TO)) {
            draft.shareTo = shareTo(patch.get(SHARE_TO), errors);
        }
        if (patch.has(SHARE_LANGUAGE)) {
            draft.shareLanguage = code(patch.get(SHARE_LANGUAGE), SHARE_LANGUAGE, "share-language.invalid",
                    SHARE_LANGUAGES::contains, errors);
        }

        coherence(draft, errors);
        if (!errors.isEmpty()) {
            throw new InvalidListingException(errors);
        }
        return new Edit(draft.toContent(definitions), List.copyOf(warnings));
    }

    /** Applies {@code categoryCode}; returns the attribute definitions of the category, or null when it is invalid. */
    private Map<String, Attribute> category(Draft draft, ListingContent current, JsonNode patch,
            List<FieldError> errors, Set<String> warnings) {
        if (!patch.has(CATEGORY_CODE)) {
            if (current == null) {
                errors.add(new FieldError(CATEGORY_CODE, "category.required"));
                return null;
            }
            return definitions(current.categoryCode()).orElseGet(() -> {
                // The category of the draft was deactivated since: the seller has to choose another one.
                errors.add(new FieldError(CATEGORY_CODE, "category.unknown"));
                return null;
            });
        }
        JsonNode value = patch.get(CATEGORY_CODE);
        if (value.isNull()) {
            errors.add(new FieldError(CATEGORY_CODE, "category.required"));
            return null;
        }
        Optional<Subcategory> subcategory = value.isString()
                ? referentials.findSubcategory(value.stringValue())
                : Optional.empty();
        if (subcategory.isEmpty()) {
            errors.add(new FieldError(CATEGORY_CODE, "category.unknown"));
            return null;
        }
        String code = subcategory.get().code();
        Map<String, Attribute> definitions = definitions(code).orElseThrow();
        if (current != null && !code.equals(current.categoryCode())) {
            if (draft.attributes.keySet().removeIf(attribute -> !definitions.containsKey(attribute))) {
                warnings.add(WARNING_ATTRIBUTES_REMOVED);
            }
            if (!ListingKind.of(code).allows(draft.priceMode) && !patch.has(PRICE_MODE)) {
                draft.priceMode = null;
                warnings.add(WARNING_PRICE_MODE_RESET);
            }
        }
        draft.categoryCode = code;
        return definitions;
    }

    private Optional<Map<String, Attribute>> definitions(String categoryCode) {
        if (referentials.findSubcategory(categoryCode).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(referentials.attributesOf(categoryCode).stream()
                .collect(Collectors.toMap(Attribute::code, Function.identity(), (a, b) -> a, LinkedHashMap::new)));
    }

    private static String text(JsonNode value, String field, String prefix, boolean multiLine, int min, int max,
            List<FieldError> errors) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            errors.add(new FieldError(field, prefix + ".invalid"));
            return null;
        }
        String text = multiLine ? Texts.multiLine(value.stringValue()) : Texts.singleLine(value.stringValue());
        int length = Texts.length(text);
        if (Texts.hasForbiddenControl(text)) {
            errors.add(new FieldError(field, prefix + ".invalid"));
        } else if (length < min) {
            errors.add(new FieldError(field, prefix + ".too-short"));
        } else if (length > max) {
            errors.add(new FieldError(field, prefix + ".too-long"));
        }
        return text;
    }

    private static PriceMode priceMode(JsonNode value, String categoryCode, List<FieldError> errors) {
        if (value.isNull()) {
            return null;
        }
        PriceMode mode = value.isString() ? parse(value.stringValue()) : null;
        if (mode == null) {
            errors.add(new FieldError(PRICE_MODE, "price-mode.invalid"));
        } else if (categoryCode != null && !ListingKind.of(categoryCode).allows(mode)) {
            errors.add(new FieldError(PRICE_MODE, "price-mode.not-allowed"));
        }
        return mode;
    }

    private static PriceMode parse(String value) {
        for (PriceMode mode : PriceMode.values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        return null;
    }

    private static Long price(JsonNode value, List<FieldError> errors) {
        if (value.isNull()) {
            return null;
        }
        // Whole dinars only: 32000.5 or "32000" are refused, never rounded.
        if (!value.isIntegralNumber()) {
            errors.add(new FieldError(PRICE, "price.invalid"));
            return null;
        }
        if (!value.canConvertToLong() || value.longValue() < PRICE_MIN || value.longValue() > PRICE_MAX) {
            errors.add(new FieldError(PRICE, "price.out-of-range"));
            return null;
        }
        return value.longValue();
    }

    private static String code(JsonNode value, String field, String unknown,
            Predicate<String> exists, List<FieldError> errors) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isString() || !exists.test(value.stringValue())) {
            errors.add(new FieldError(field, unknown));
            return null;
        }
        return value.stringValue();
    }

    /** A list of {@link ShareNetwork} without duplicates, kept in the order of the enum; {@code null} clears it. */
    private static List<ShareNetwork> shareTo(JsonNode value, List<FieldError> errors) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            errors.add(new FieldError(SHARE_TO, "share-to.invalid"));
            return null;
        }
        Set<ShareNetwork> networks = new LinkedHashSet<>();
        for (JsonNode element : value) {
            ShareNetwork network = element.isString() ? network(element.stringValue()) : null;
            if (network == null) {
                errors.add(new FieldError(SHARE_TO, "share-to.invalid"));
                return null;
            }
            if (!networks.add(network)) {
                errors.add(new FieldError(SHARE_TO, "share-to.duplicate"));
                return null;
            }
        }
        return Arrays.stream(ShareNetwork.values()).filter(networks::contains).toList();
    }

    private static ShareNetwork network(String value) {
        for (ShareNetwork network : ShareNetwork.values()) {
            if (network.name().equals(value)) {
                return network;
            }
        }
        return null;
    }

    private void attributes(Draft draft, JsonNode patch, Map<String, Attribute> definitions,
            List<FieldError> errors) {
        if (patch.isNull()) {
            draft.attributes.clear();
            return;
        }
        if (!patch.isObject()) {
            errors.add(new FieldError(ATTRIBUTES, "attributes.invalid"));
            return;
        }
        Year currentYear = Year.now(clock);
        for (Map.Entry<String, JsonNode> member : patch.properties()) {
            String field = ATTRIBUTES + "." + member.getKey();
            Attribute definition = definitions.get(member.getKey());
            if (definition == null) {
                errors.add(new FieldError(field, AttributeValidator.UNKNOWN));
            } else if (member.getValue().isNull()) {
                draft.attributes.remove(member.getKey());
            } else {
                switch (AttributeValidator.validate(definition, member.getValue(), currentYear)) {
                    case AttributeValidator.Result.Valid valid -> draft.attributes.put(member.getKey(), valid.value());
                    case AttributeValidator.Result.Invalid invalid -> errors.add(new FieldError(field, invalid.code()));
                }
            }
        }
    }

    /** Rules between fields, on the content after the patch; a field already in error is not reported twice. */
    private void coherence(Draft draft, List<FieldError> errors) {
        if (draft.priceMode == PriceMode.ON_QUOTE && draft.price != null && !hasError(errors, PRICE)) {
            errors.add(new FieldError(PRICE, "price.not-allowed"));
        }
        if (draft.delegationCode != null && !hasError(errors, DELEGATION_CODE)
                && !hasError(errors, GOVERNORATE_CODE)) {
            String governorate = referentials.findDelegation(draft.delegationCode)
                    .map(Delegation::governorateCode).orElse(null);
            if (!Objects.equals(governorate, draft.governorateCode)) {
                errors.add(new FieldError(DELEGATION_CODE, "delegation.not-in-governorate"));
            }
        }
    }

    private static boolean hasError(List<FieldError> errors, String field) {
        return errors.stream().anyMatch(error -> error.field().equals(field));
    }

    /** Mutable working copy of a {@link ListingContent}. */
    private static final class Draft {

        String categoryCode;
        String title;
        String description;
        PriceMode priceMode;
        Long price;
        boolean negotiable;
        String governorateCode;
        String delegationCode;
        final Map<String, Object> attributes = new LinkedHashMap<>();
        List<ShareNetwork> shareTo;
        String shareLanguage;

        Draft() {
        }

        Draft(ListingContent content) {
            categoryCode = content.categoryCode();
            title = content.title();
            description = content.description();
            priceMode = content.priceMode();
            price = content.price();
            negotiable = content.negotiable();
            governorateCode = content.governorateCode();
            delegationCode = content.delegationCode();
            attributes.putAll(content.attributes());
            shareTo = content.shareTo();
            shareLanguage = content.shareLanguage();
        }

        /** Attributes in the display order of the subcategory. */
        ListingContent toContent(Map<String, Attribute> definitions) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            definitions.keySet().stream().filter(attributes::containsKey)
                    .forEach(code -> ordered.put(code, attributes.get(code)));
            return new ListingContent(categoryCode, title, description, priceMode, price, negotiable,
                    governorateCode, delegationCode, ordered, shareTo, shareLanguage);
        }
    }
}
