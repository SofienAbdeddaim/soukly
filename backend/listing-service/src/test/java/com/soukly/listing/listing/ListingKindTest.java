package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListingKindTest {

    @Test
    void servicesAndJobsAreServicesTheRestIsSales() {
        assertThat(ListingKind.of("services.home-repair")).isEqualTo(ListingKind.SERVICE);
        assertThat(ListingKind.of("jobs.offers")).isEqualTo(ListingKind.SERVICE);
        assertThat(ListingKind.of("vehicles.cars")).isEqualTo(ListingKind.SALE);
        // A family whose code starts like another one is not that one
        assertThat(ListingKind.of("jobsite.tools")).isEqualTo(ListingKind.SALE);
    }

    @Test
    void onlyServicesMayBeOnQuote() {
        assertThat(ListingKind.SERVICE.allows(PriceMode.ON_QUOTE)).isTrue();
        assertThat(ListingKind.SALE.allows(PriceMode.ON_QUOTE)).isFalse();
        assertThat(ListingKind.SALE.allows(PriceMode.FIXED)).isTrue();
        assertThat(ListingKind.SALE.allows(null)).isTrue();
    }
}
