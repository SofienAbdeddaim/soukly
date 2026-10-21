package com.soukly.gateway.ratelimit;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.soukly.gateway.error.ProblemResponseWriter;

import reactor.core.publisher.Mono;

/**
 * Limits the matching requests per client IP with an in-memory {@link RateLimiter}. Beyond the limit the gateway
 * answers {@code 429} with {@code Retry-After} and the request never reaches the service.
 */
abstract class RateLimitFilter implements WebFilter {

    private final ServerWebExchangeMatcher limited;
    private final RateLimiter limiter;
    private final ClientIpResolver clientIps;
    private final ProblemResponseWriter problems;
    private final String detail;

    RateLimitFilter(ServerWebExchangeMatcher limited, int perMinute, ClientIpResolver clientIps,
            ProblemResponseWriter problems, String detail) {
        this.limited = limited;
        this.limiter = new RateLimiter(perMinute, Duration.ofMinutes(1), System::nanoTime);
        this.clientIps = clientIps;
        this.problems = problems;
        this.detail = detail;
    }

    /** Budget the request counts against, from the client IP. */
    abstract String key(ServerWebExchange exchange, String clientIp);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return limited.matches(exchange).flatMap(match -> {
            if (!match.isMatch()) {
                return chain.filter(exchange);
            }
            Duration wait = limiter.tryAcquire(key(exchange, clientIps.resolve(exchange.getRequest())));
            if (wait.isZero()) {
                return chain.filter(exchange);
            }
            long retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            return problems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, detail);
        });
    }
}
