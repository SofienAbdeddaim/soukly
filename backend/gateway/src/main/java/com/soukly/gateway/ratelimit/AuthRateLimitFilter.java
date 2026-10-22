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
 * Limits {@code POST /api/users/login}, {@code POST /api/users/register}, {@code POST /api/users/verify-email/resend},
 * the password reset paths {@code POST /api/users/password/forgot} and {@code /reset} (AUTH-04) and the confirmation
 * of a new address {@code POST /api/users/email/confirm} (AUTH-05) per client IP (each path has its own budget). Runs
 * after {@code RequestIdFilter}, before security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class AuthRateLimitFilter extends RateLimitFilter {

    AuthRateLimitFilter(GatewayProperties properties, ProblemResponseWriter problems) {
        super(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/users/login", "/api/users/register",
                "/api/users/verify-email/resend", "/api/users/password/forgot", "/api/users/password/reset",
                "/api/users/email/confirm"),
                properties.rateLimit().authPerMinute(),
                new ClientIpResolver(properties.trustedProxies()), problems,
                "Too many attempts. Please try again later.");
    }

    @Override
    String key(ServerWebExchange exchange, String clientIp) {
        return exchange.getRequest().getPath().value() + '|' + clientIp;
    }
}
