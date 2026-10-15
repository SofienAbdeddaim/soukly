package com.soukly.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Logs one line per request: method, path (without query string), status, duration until the response headers
 * are sent, and request identifier. Never logs headers, cookies or bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AccessLogFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger("com.soukly.gateway.access");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            log.info("{} {} {} {}ms requestId={}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(), status != null ? status.value() : "-",
                    (System.nanoTime() - start) / 1_000_000, RequestIdFilter.requestId(exchange));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
