package com.soukly.gateway.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.soukly.common.web.SouklyHeaders;
import com.soukly.gateway.config.GatewayProperties;

/**
 * CORS for the front end in local development. In production the front end and the API share the same origin
 * behind the reverse proxy, so CORS is not needed there.
 */
@Configuration(proxyBeanMethods = false)
class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        List<String> origins = properties.cors().allowedOrigins();
        if (origins == null || origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must list exact origins: credentials are allowed, so '*' is forbidden");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", SouklyHeaders.REQUEST_ID));
        cors.setExposedHeaders(List.of(SouklyHeaders.REQUEST_ID));
        cors.setMaxAge(properties.cors().maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
