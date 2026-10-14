package com.soukly.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SouklyServicesTest {

    @Test
    void serviceNamesAreDistinct() {
        assertThat(java.util.Set.of(
                SouklyServices.GATEWAY,
                SouklyServices.USER_SERVICE,
                SouklyServices.LISTING_SERVICE,
                SouklyServices.SOCIAL_PUBLISHER)).hasSize(4);
    }
}
