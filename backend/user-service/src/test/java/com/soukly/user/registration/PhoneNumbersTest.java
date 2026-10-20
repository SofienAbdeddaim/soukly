package com.soukly.user.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = { "20 123 456", "20123456", "+216 20123456", "+21620123456", "0021620123456",
            " +216 20 123 456 ", "20-123-456", "20.123.456" })
    void tunisianMobileFormsAreNormalizedToE164(String input) {
        assertThat(PhoneNumbers.toE164(input)).contains("+21620123456");
    }

    @ParameterizedTest
    @ValueSource(strings = { "71 123 456", "+216 71 123 456" })
    void fixedLinesAreAccepted(String input) {
        assertThat(PhoneNumbers.toE164(input)).contains("+21671123456");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "+33612345678", "0033612345678", "1234", "2012345", "201234567", "+216 2012345",
            "20123456 ext 12", "two0123456", "+216+20123456" })
    void otherNumbersAreRejected(String input) {
        assertThat(PhoneNumbers.toE164(input)).isEmpty();
    }
}
