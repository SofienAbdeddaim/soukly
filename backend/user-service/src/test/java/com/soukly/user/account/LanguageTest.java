package com.soukly.user.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class LanguageTest {

    @ParameterizedTest
    @CsvSource({ "ar, AR", "ar-TN, AR", "'en-US,en;q=0.9', EN", "'de, ar;q=0.2, en;q=0.5', EN", "fr, FR", "de, FR",
            "'not a header;;', FR" })
    void picksTheBestSupportedLanguage(String header, Language expected) {
        assertThat(Language.fromAcceptLanguage(header)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void defaultsToFrench(String header) {
        assertThat(Language.fromAcceptLanguage(header)).isEqualTo(Language.FR);
    }
}
