package com.soukly.listing;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);

    /**
     * Clock of every business date (drafts, publication, expiry, photo cleanup). {@code LISTING_CLOCK_OFFSET} moves it
     * forward to try the expiry and the reminder by hand locally ({@code P58D}); always zero in dev and prod. Access
     * tokens and timeouts use the real time.
     */
    @Bean
    Clock clock(@Value("${soukly.listing.clock-offset:PT0S}") Duration offset) {
        if (offset.isZero()) {
            return Clock.systemDefaultZone();
        }
        log.warn("The business clock of listing-service is offset by {} (soukly.listing.clock-offset)", offset);
        return Clock.offset(Clock.systemDefaultZone(), offset);
    }
}
