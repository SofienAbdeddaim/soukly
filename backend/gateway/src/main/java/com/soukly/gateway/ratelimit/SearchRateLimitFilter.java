package com.soukly.gateway.ratelimit;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.soukly.gateway.config.GatewayProperties;
import com.soukly.gateway.error.ProblemResponseWriter;

/**
 * Limits the public search {@code GET /api/listings} (CATA-01) per client IP; the reading of one listing
 * ({@code GET /api/listings/{id}}) is not counted. Runs after {@code RequestIdFilter}, before security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
class SearchRateLimitFilter extends RateLimitFilter {

    SearchRateLimitFilter(GatewayProperties properties, ProblemResponseWriter problems) {
        super(ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/api/listings"),
                properties.rateLimit().searchPerMinute(), new ClientIpResolver(properties.trustedProxies()),
                problems, "Too many searches. Please try again later.");
    }

    @Override
    String key(ServerWebExchange exchange, String clientIp) {
        return clientIp;
    }
}
