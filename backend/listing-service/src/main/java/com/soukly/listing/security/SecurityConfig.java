package com.soukly.listing.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.soukly.listing.internal.InternalApiProperties;
import com.soukly.listing.internal.InternalTokenFilter;

/**
 * Access policy of listing-service, the same as the gateway's (defense in depth): the referentials (CAT-01), the
 * search (CATA-01), the reading of a listing, its short reference (SOC-05) and the health check are public; everything else needs a valid access token. Stateless.
 *
 * <p>Unlike the gateway, a token sent on {@code GET /listings/{id}} is validated: it identifies the owner of the
 * listing (full view, CATA-03), and an invalid one is answered with {@code 401} so that the front end refreshes it.
 * {@code POST /listings/{id}/contact} needs a token, like every path not listed here, and {@code GET /listings/mine}
 * (ANN-05), although it looks like the reading of a listing.
 *
 * <p>The service-to-service endpoints {@code /internal/**} (SOC-06) have their own chain: the shared token of
 * {@code X-Internal-Token}, never a user's access token, as in user-service.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    static final RequestMatcher PUBLIC = new OrRequestMatcher(
            get("/categories/**"),
            get("/locations/**"),
            get("/listings"),
            get("/listings/*"),
            get("/listings/by-ref/*"),
            get("/actuator/health"),
            PathPatternRequestMatcher.pathPattern("/error"));

    @Bean
    @Order(1)
    SecurityFilterChain internalFilterChain(HttpSecurity http, InternalApiProperties internalApi,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions) throws Exception {
        if (!internalApi.configured()) {
            log.warn("INTERNAL_API_TOKEN is not set: every /internal/** request is refused");
        }
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                exceptions.resolveException(request, response, null, e);
        return http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalTokenFilter(internalApi), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("INTERNAL"))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions) throws Exception {
        // Errors go through ApiExceptionHandler, so they share the problem+json format of the service.
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                exceptions.resolveException(request, response, null, e);
        AccessDeniedHandler forbidden = (request, response, e) -> exceptions.resolveException(request, response, null, e);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Before GET /listings/*: the listings of the caller (ANN-05)
                        .requestMatchers(get("/listings/mine")).authenticated()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    private static RequestMatcher get(String path) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.GET, path);
    }
}
