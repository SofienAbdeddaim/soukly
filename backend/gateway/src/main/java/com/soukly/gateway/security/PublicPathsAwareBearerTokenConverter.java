package com.soukly.gateway.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Reads the bearer token only on protected paths.
 *
 * <p>On a public path the token is not validated at all: the request goes through even with an invalid token,
 * and the {@code Authorization} header is forwarded unchanged to the service.
 */
class PublicPathsAwareBearerTokenConverter implements ServerAuthenticationConverter {

    private final ServerBearerTokenAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return PublicPaths.MATCHER.matches(exchange)
                .flatMap(match -> match.isMatch() ? Mono.empty() : delegate.convert(exchange));
    }
}
