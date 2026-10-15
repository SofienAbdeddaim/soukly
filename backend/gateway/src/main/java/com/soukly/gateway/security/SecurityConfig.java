package com.soukly.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import com.soukly.gateway.error.ProblemResponseWriter;

/**
 * Access policy of the gateway: stateless, the whitelist of {@link PublicPaths} is public, everything else needs
 * a valid JWT. The {@code Authorization} header is forwarded unchanged; the gateway adds no identity header.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, CorsConfigurationSource cors,
            ProblemResponseWriter problems) {
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) -> {
            // Never echo the reason: it may describe the token.
            String challenge = ex instanceof InvalidBearerTokenException ? "Bearer error=\"invalid_token\"" : "Bearer";
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge);
            return problems.write(exchange, HttpStatus.UNAUTHORIZED, "A valid bearer token is required.");
        };
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> problems.write(exchange, HttpStatus.FORBIDDEN,
                "Access to this resource is denied.");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(spec -> spec.configurationSource(cors))
                .authorizeExchange(exchanges -> exchanges
                        .matchers(PublicPaths.MATCHER).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenConverter(new PublicPathsAwareBearerTokenConverter())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> {
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }
}
