package com.soukly.social;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /** Clock of the expiry of the OAuth states and Page choices; replaced by a clock that tests can move. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
