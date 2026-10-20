package com.soukly.gateway.ratelimit;

import java.time.Duration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.soukly.gateway.config.GatewayProperties;
import com.soukly.gateway.error.ProblemResponseWriter;

import reactor.core.publisher.Mono;

/**
 * Limits {@code POST /api/users/login}, {@code POST /api/users/register} and
 * {@code POST /api/users/verify-email/resend} per client IP (each path has its own budget). Beyond the limit the gateway answers {@code 429} with {@code Retry-After} and the request never
 * reaches user-service. Runs after {@code RequestIdFilter}, before security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class AuthRateLimitFilter implements WebFilter {

    private static final ServerWebExchangeMatcher LIMITED = ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
            "/api/users/login", "/api/users/register", "/api/users/verify-email/resend");

    private final RateLimiter limiter;
    private final ClientIpResolver clientIps;
    private final ProblemResponseWriter problems;

    AuthRateLimitFilter(GatewayProperties properties, ProblemResponseWriter problems) {
        this.limiter = new RateLimiter(properties.rateLimit().authPerMinute(), Duration.ofMinutes(1),
                System::nanoTime);
        this.clientIps = new ClientIpResolver(properties.trustedProxies());
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return LIMITED.matches(exchange).flatMap(match -> {
            if (!match.isMatch()) {
                return chain.filter(exchange);
            }
            String key = exchange.getRequest().getPath().value() + '|' + clientIps.resolve(exchange.getRequest());
            Duration wait = limiter.tryAcquire(key);
            if (wait.isZero()) {
                return chain.filter(exchange);
            }
            long retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            return problems.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please try again later.");
        });
    }
}
