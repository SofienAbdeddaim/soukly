package com.soukly.user.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Access policy of user-service, the same as the gateway's (defense in depth): the endpoints of AUTH-01 and
 * AUTH-02, the JWKS and the health check are public; everything else needs a valid access token, validated with
 * the service's own keys. Stateless: no HTTP session, no CSRF token (the cookie endpoints check {@code Origin}).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    static final RequestMatcher PUBLIC = new OrRequestMatcher(
            post("/users/register"),
            post("/users/verify-email"),
            post("/users/verify-email/resend"),
            post("/users/login"),
            post("/users/refresh"),
            post("/users/logout"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/.well-known/jwks.json"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/actuator/health"),
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
