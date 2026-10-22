package com.soukly.user.web;

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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.soukly.user.internal.InternalApiProperties;
import com.soukly.user.internal.InternalTokenFilter;

/**
 * Access policy of user-service, the same as the gateway's (defense in depth): the endpoints of AUTH-01, AUTH-02
 * and AUTH-04, the confirmation of a new address (AUTH-05), the JWKS and the health check are public; everything else needs a valid access token, validated with
 * the service's own keys. Stateless: no HTTP session, no CSRF token (the cookie endpoints check {@code Origin}).
 *
 * <p>The service-to-service endpoints {@code /internal/**} (CATA-03) have their own chain: the shared token of
 * {@code X-Internal-Token}, never a user's access token.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    static final RequestMatcher PUBLIC = new OrRequestMatcher(
            post("/users/register"),
            post("/users/verify-email"),
            post("/users/verify-email/resend"),
            post("/users/login"),
            post("/users/refresh"),
            post("/users/logout"),
            post("/users/password/forgot"),
            post("/users/password/reset"),
            post("/users/email/confirm"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/.well-known/jwks.json"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/actuator/health"),
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
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(publicPathsIgnored())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    /**
     * On a public path the bearer token is not read at all, like on the gateway: an expired token sent along with
     * a refresh must not fail it.
     */
    private static BearerTokenResolver publicPathsIgnored() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> PUBLIC.matches(request) ? null : delegate.resolve(request);
    }

    private static RequestMatcher post(String path) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, path);
    }
}
