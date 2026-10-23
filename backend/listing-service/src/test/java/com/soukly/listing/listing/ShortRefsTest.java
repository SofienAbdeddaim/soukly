package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class ShortRefsTest {

    @Test
    void aReferenceHasEightCharactersWithoutTheAmbiguousOnes() {
        SplittableRandom random = new SplittableRandom(42);
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String ref = ShortRefs.generate(random);
            assertThat(ref).hasSize(8).matches("[2-9A-HJ-NP-Z]{8}");
            ref.chars().forEach(c -> seen.add((char) c));
        }
        assertThat(seen).doesNotContain('0', 'O', '1', 'I').hasSize(ShortRefs.ALPHABET.length());
    }

    @Test
    void referencesAreRandom() {
        Set<String> refs = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            refs.add(ShortRefs.generate());
        }
        assertThat(refs).hasSize(1_000);
    }

    @Test
    void aTypedReferenceIsReadInAnyCase() {
        assertThat(ShortRefs.normalize("8f3c2k9a")).contains("8F3C2K9A");
        assertThat(ShortRefs.normalize("8F3C2K9A")).contains("8F3C2K9A");
    }

    @Test
    void somethingElseIsNoReference() {
        assertThat(ShortRefs.normalize(null)).isEmpty();
        assertThat(ShortRefs.normalize("8F3C2K9")).isEmpty();
        assertThat(ShortRefs.normalize("8F3C2K9AB")).isEmpty();
        assertThat(ShortRefs.normalize("8F3C2K9O")).isEmpty();
        assertThat(ShortRefs.normalize("8F3C2K9-")).isEmpty();
        assertThat(ShortRefs.normalize("8F3C2K9ı")).isEmpty();
    }
}
