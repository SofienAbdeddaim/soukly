package com.soukly.listing.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void arabicLosesItsDiacriticsAndTatweel() {
        assertThat(SearchText.normalize("سَيَّارَة")).isEqualTo("سياره");
        assertThat(SearchText.normalize("جـــديد")).isEqualTo("جديد");
    }

    @Test
    void arabicLetterVariantsAreUnified() {
        assertThat(SearchText.normalize("أحمد إبراهيم آمال")).isEqualTo("احمد ابراهيم امال");
        assertThat(SearchText.normalize("سيارة")).isEqualTo("سياره");
        assertThat(SearchText.normalize("مستشفى")).isEqualTo("مستشفي");
        // A hamza on waw or yeh is kept, as unaccent does
        assertThat(SearchText.normalize("مؤسسة")).isEqualTo("مؤسسه");
    }

    @Test
    void latinLosesCaseAndAccents() {
        assertThat(SearchText.normalize("Canapé ÉLÉGANT œuvre Straße")).isEqualTo("canape elegant oeuvre strasse");
    }

    @Test
    void queryIsTrimmedAndItsSpacesCollapsed() {
        assertThat(SearchText.query("  peugeot    208 ")).isEqualTo("peugeot 208");
        assertThat(SearchText.query("   ")).isNull();
        assertThat(SearchText.query(null)).isNull();
    }

    @Test
    void queryLengthIsCountedOnceNormalized() {
        // Two letters once the diacritics are gone
        assertThatThrownBy(() -> SearchText.query("سَ")).isInstanceOf(InvalidSearchException.class);
        assertThat(SearchText.query("سَب")).isEqualTo("سَب");
        assertThat(SearchText.query("x".repeat(100))).hasSize(100);
        assertThatThrownBy(() -> SearchText.query("x".repeat(101))).isInstanceOf(InvalidSearchException.class);
    }
}
