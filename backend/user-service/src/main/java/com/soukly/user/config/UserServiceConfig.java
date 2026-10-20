package com.soukly.user.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.soukly.user.session.SessionProperties;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({ UserProperties.class, SessionProperties.class })
class UserServiceConfig {

    /**
     * Argon2id with the parameters recommended by Spring Security 7.1 (16-byte salt, 32-byte hash, parallelism 1,
     * 16 MiB of memory, 2 iterations).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
