package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.soukly.listing.listing.AttributeValidator.Result;
import com.soukly.listing.referential.Attribute;
import com.soukly.listing.referential.AttributeType;
import com.soukly.listing.referential.EnumValue;
import com.soukly.listing.referential.Labels;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Every type and every bound, on definitions built here: the validator knows no attribute by its code. */
class AttributeValidatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Labels LABELS = new Labels("fr", "ar", "en");
    private static final Year YEAR = Year.of(2026);

    private static final Attribute TEXT = attribute(AttributeType.TEXT, null, null, null, 2, 10, List.of());
    private static final Attribute TEXT_NO_BOUNDS = attribute(AttributeType.TEXT, null, null, null, null, null,
            List.of());
    private static final Attribute INTEGER = attribute(AttributeType.INTEGER, 0, 100, null, null, null, List.of());
    private static final Attribute YEAR_ATTRIBUTE = attribute(AttributeType.INTEGER, 1950, null, 1, null, null,
            List.of());
    private static final Attribute ENUM = attribute(AttributeType.ENUM, null, null, null, null, null,
            List.of(new EnumValue("manual", LABELS), new EnumValue("automatic", LABELS)));
    private static final Attribute BOOLEAN = attribute(AttributeType.BOOLEAN, null, null, null, null, null,
            List.of());

    @Test
    void textIsNormalizedWithItsCaseKept() {
        assertThat(validate(TEXT, "\"  Kia   Rio \"")).isEqualTo(new Result.Valid("Kia Rio"));
        assertThat(validate(TEXT, "\"\\tAB\\n\"")).isEqualTo(new Result.Valid("AB"));
    }

    @Test
    void textLengthIsCheckedAfterNormalization() {
        assertThat(validate(TEXT, "\"A\"")).isEqualTo(new Result.Invalid("attribute.too-short"));
        assertThat(validate(TEXT, "\"   A    \"")).isEqualTo(new Result.Invalid("attribute.too-short"));
        assertThat(validate(TEXT, "\"AB\"")).isEqualTo(new Result.Valid("AB"));
        assertThat(validate(TEXT, "\"" + "x".repeat(10) + "\"")).isEqualTo(new Result.Valid("x".repeat(10)));
        assertThat(validate(TEXT, "\"" + "x".repeat(11) + "\"")).isEqualTo(new Result.Invalid("attribute.too-long"));
        // Characters, not UTF-16 units: an emoji counts once
        assertThat(validate(TEXT, "\"" + "😀".repeat(10) + "\"")).isInstanceOf(Result.Valid.class);
    }

    @Test
    void aBlankTextIsTooShortEvenWithoutMinimum() {
        assertThat(validate(TEXT_NO_BOUNDS, "\"   \"")).isEqualTo(new Result.Invalid("attribute.too-short"));
        assertThat(validate(TEXT_NO_BOUNDS, "\"" + "x".repeat(500) + "\"")).isInstanceOf(Result.Valid.class);
    }

    @Test
    void controlCharactersAreRefusedInText() {
        assertThat(validate(TEXT, "\"AB\\u0000C\"")).isEqualTo(new Result.Invalid("attribute.invalid-value"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "42", "true", "{}", "[\"AB\"]" })
    void textMustBeAString(String json) {
        assertThat(validate(TEXT, json)).isEqualTo(new Result.Invalid("attribute.invalid-type"));
    }

    @Test
    void integerBoundsAreInclusive() {
        assertThat(validate(INTEGER, "0")).isEqualTo(new Result.Valid(0L));
        assertThat(validate(INTEGER, "100")).isEqualTo(new Result.Valid(100L));
        assertThat(validate(INTEGER, "-1")).isEqualTo(new Result.Invalid("attribute.out-of-range"));
        assertThat(validate(INTEGER, "101")).isEqualTo(new Result.Invalid("attribute.out-of-range"));
        assertThat(validate(INTEGER, "99999999999999999999999")).isEqualTo(
                new Result.Invalid("attribute.out-of-range"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "12.5", "\"12\"", "true", "[12]" })
    void integerMustBeAWholeNumber(String json) {
        assertThat(validate(INTEGER, json)).isEqualTo(new Result.Invalid("attribute.invalid-type"));
    }

    @Test
    void yearMaximumIsTheCurrentYearPlusOne() {
        assertThat(validate(YEAR_ATTRIBUTE, "1950")).isEqualTo(new Result.Valid(1950L));
        assertThat(validate(YEAR_ATTRIBUTE, "1949")).isEqualTo(new Result.Invalid("attribute.out-of-range"));
        assertThat(validate(YEAR_ATTRIBUTE, "2027")).isEqualTo(new Result.Valid(2027L));
        assertThat(validate(YEAR_ATTRIBUTE, "2028")).isEqualTo(new Result.Invalid("attribute.out-of-range"));
        assertThat(AttributeValidator.validate(YEAR_ATTRIBUTE, json("2028"), Year.of(2027)))
                .isEqualTo(new Result.Valid(2028L));
    }

    @Test
    void enumAcceptsOnlyItsCodes() {
        assertThat(validate(ENUM, "\"automatic\"")).isEqualTo(new Result.Valid("automatic"));
        assertThat(validate(ENUM, "\"Automatic\"")).isEqualTo(new Result.Invalid("attribute.invalid-value"));
        assertThat(validate(ENUM, "\"cvt\"")).isEqualTo(new Result.Invalid("attribute.invalid-value"));
        assertThat(validate(ENUM, "1")).isEqualTo(new Result.Invalid("attribute.invalid-type"));
    }

    @Test
    void booleanAcceptsOnlyBooleans() {
        assertThat(validate(BOOLEAN, "true")).isEqualTo(new Result.Valid(true));
        assertThat(validate(BOOLEAN, "false")).isEqualTo(new Result.Valid(false));
        assertThat(validate(BOOLEAN, "\"true\"")).isEqualTo(new Result.Invalid("attribute.invalid-type"));
        assertThat(validate(BOOLEAN, "1")).isEqualTo(new Result.Invalid("attribute.invalid-type"));
    }

    private static Result validate(Attribute attribute, String json) {
        return AttributeValidator.validate(attribute, json(json), YEAR);
    }

    private static JsonNode json(String json) {
        return JSON.readTree(json);
    }

    private static Attribute attribute(AttributeType type, Integer min, Integer max, Integer yearOffset,
            Integer minLength, Integer maxLength, List<EnumValue> values) {
        return new Attribute("test", type, true, min, max, yearOffset, minLength, maxLength, false, LABELS, values);
    }
}
