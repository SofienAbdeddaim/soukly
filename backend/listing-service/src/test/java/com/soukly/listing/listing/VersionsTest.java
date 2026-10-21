package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionsTest {

    @Test
    void theEtagIsTheQuotedVersion() {
        assertThat(Versions.etag(4)).isEqualTo("\"4\"");
    }

    @Test
    void ifMatchUsesTheStrongComparison() {
        assertThat(Versions.matches("\"4\"", 4)).isTrue();
        assertThat(Versions.matches("\"3\"", 4)).isFalse();
        assertThat(Versions.matches("W/\"4\"", 4)).isFalse();
        assertThat(Versions.matches("4", 4)).isFalse();
        assertThat(Versions.matches("\"3\", \"4\"", 4)).isTrue();
        assertThat(Versions.matches("*", 4)).isTrue();
    }

    @Test
    void ifMatchIsRequired() {
        assertThatThrownBy(() -> Versions.matches(null, 1)).isInstanceOf(IfMatchRequiredException.class);
        assertThatThrownBy(() -> Versions.matches(" ", 1)).isInstanceOf(IfMatchRequiredException.class);
    }
}
