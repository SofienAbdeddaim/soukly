package com.soukly.listing.listing;

import java.time.Year;

import com.soukly.listing.referential.Attribute;
import com.soukly.listing.referential.EnumValue;

import tools.jackson.databind.JsonNode;

/**
 * Checks one specific attribute against its definition in the referentials (CAT-01): type, bounds, length and enum
 * values. Generic: no attribute is known by its code here, everything comes from {@link Attribute}.
 */
final class AttributeValidator {

    static final String UNKNOWN = "attribute.unknown";
    static final String INVALID_TYPE = "attribute.invalid-type";
    static final String INVALID_VALUE = "attribute.invalid-value";
    static final String OUT_OF_RANGE = "attribute.out-of-range";
    static final String TOO_SHORT = "attribute.too-short";
    static final String TOO_LONG = "attribute.too-long";

    private AttributeValidator() {
    }

    /** The normalized value to store, or the error code. */
    sealed interface Result {

        record Valid(Object value) implements Result {
        }

        record Invalid(String code) implements Result {
        }
    }

    /**
     * @param value       a non-null JSON value
     * @param currentYear year of the server clock, for a maximum relative to the current year ({@code year})
     */
    static Result validate(Attribute attribute, JsonNode value, Year currentYear) {
        return switch (attribute.type()) {
            case TEXT -> text(attribute, value);
            case INTEGER -> integer(attribute, value, currentYear);
            case ENUM -> enumValue(attribute, value);
            case BOOLEAN -> value.isBoolean() ? new Result.Valid(value.booleanValue()) : invalid(INVALID_TYPE);
        };
    }

    private static Result text(Attribute attribute, JsonNode value) {
        if (!value.isString()) {
            return invalid(INVALID_TYPE);
        }
        String text = Texts.singleLine(value.stringValue());
        if (Texts.hasForbiddenControl(text)) {
            return invalid(INVALID_VALUE);
        }
        int length = Texts.length(text);
        // An attribute is never stored empty: a blank text is too short even without a minimum
        if (length == 0 || (attribute.minLength() != null && length < attribute.minLength())) {
            return invalid(TOO_SHORT);
        }
        if (attribute.maxLength() != null && length > attribute.maxLength()) {
            return invalid(TOO_LONG);
        }
        return new Result.Valid(text);
    }

    private static Result integer(Attribute attribute, JsonNode value, Year currentYear) {
        if (!value.isIntegralNumber()) {
            return invalid(INVALID_TYPE);
        }
        if (!value.canConvertToLong()) {
            return invalid(OUT_OF_RANGE);
        }
        long number = value.longValue();
        Integer max = attribute.effectiveMaxValue(currentYear);
        if ((attribute.minValue() != null && number < attribute.minValue()) || (max != null && number > max)) {
            return invalid(OUT_OF_RANGE);
        }
        return new Result.Valid(number);
    }

    private static Result enumValue(Attribute attribute, JsonNode value) {
        if (!value.isString()) {
            return invalid(INVALID_TYPE);
        }
        String code = value.stringValue();
        return attribute.values().stream().map(EnumValue::code).anyMatch(code::equals)
                ? new Result.Valid(code)
                : invalid(INVALID_VALUE);
    }

    private static Result invalid(String code) {
        return new Result.Invalid(code);
    }
}
