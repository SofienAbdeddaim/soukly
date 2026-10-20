package com.soukly.listing.referential;

import java.time.Year;
import java.util.List;

/**
 * A specific attribute as it applies to one subcategory: {@code required} already includes the override of the
 * category. Bounds are inclusive; {@code null} means no bound.
 *
 * @param minValue                  INTEGER only
 * @param maxValue                  INTEGER only, {@code null} when the maximum depends on the current year
 * @param maxValueCurrentYearOffset INTEGER only: the maximum is the current year plus this offset (year: 1)
 * @param minLength                 TEXT only, in characters after trimming
 * @param maxLength                 TEXT only, in characters after trimming
 * @param values                    ENUM only, in display order; empty for the other types
 */
public record Attribute(String code, AttributeType type, boolean required, Integer minValue, Integer maxValue,
        Integer maxValueCurrentYearOffset, Integer minLength, Integer maxLength, boolean filterable, Labels label,
        List<EnumValue> values) {

    /** Maximum to apply in {@code currentYear}, the same as {@link #maxValue()} unless it depends on the year. */
    public Integer effectiveMaxValue(Year currentYear) {
        if (maxValueCurrentYearOffset != null) {
            return currentYear.getValue() + maxValueCurrentYearOffset;
        }
        return maxValue;
    }
}
