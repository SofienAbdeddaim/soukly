package com.soukly.listing.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Access policy of listing-service, the same as the gateway's (defense in depth): the referentials (CAT-01), the
 * search (CATA-01), the reading of a listing and the health check are public; everything else needs a valid access token. Stateless.
 *
 * <p>Unlike the gateway, a token sent on {@code GET /listings/{id}} is validated: it identifies the owner of the
 * listing (full view, CATA-03), and an invalid one is answered with {@code 401} so that the front end refreshes it.
 * {@code POST /listings/{id}/contact} needs a token, like every path not listed here, and {@code GET /listings/mine}
 * (ANN-05), although it looks like the reading of a listing.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    static final RequestMatcher PUBLIC = new OrRequestMatcher(
            get("/categories/**"),
            get("/locations/**"),
            get("/listings"),
            get("/listings/*"),
            get("/actuator/health"),
            PathPatternRequestMatcher.pathPattern("/error"));

    @Bean
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
