package com.soukly.social.photo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class InstagramVariantsTest {

    private final InstagramVariants variants = new InstagramVariants(URI.create("https://photos.example.test"));

    @Test
    void theVersionIsNextToTheSourceInTheSamePrefix() {
        String source = variants.sourceKey(URI.create("https://photos.example.test/listings/l-1/p-1/1600.webp"))
                .orElseThrow();

        assertThat(source).isEqualTo("listings/l-1/p-1/1600.webp");
        assertThat(InstagramVariants.variantKey(source)).isEqualTo("listings/l-1/p-1/ig-1080.jpg");
        assertThat(variants.url("listings/l-1/p-1/ig-1080.jpg"))
                .hasToString("https://photos.example.test/listings/l-1/p-1/ig-1080.jpg");
    }

    @Test
    void onlyThe1600VersionsOfTheBucketAreRead() {
        assertThat(variants.sourceKey(URI.create("https://other.example.test/listings/l/p/1600.webp"))).isEmpty();
        assertThat(variants.sourceKey(URI.create("https://photos.example.test/listings/l/p/400.webp"))).isEmpty();
        assertThat(variants.sourceKey(URI.create("https://photos.example.test/incoming/l/p/1600.webp"))).isEmpty();
        assertThat(variants.sourceKey(URI.create("https://photos.example.test/listings/../p/1600.webp"))).isEmpty();
        assertThat(variants.sourceKey(URI.create("https://photos.example.test/listings/l/x/p/1600.webp"))).isEmpty();
        assertThat(variants.sourceKey(URI.create("https://photos.example.test/listings/l/p/1600.webp?x=1")))
                .isEmpty();
    }
}
