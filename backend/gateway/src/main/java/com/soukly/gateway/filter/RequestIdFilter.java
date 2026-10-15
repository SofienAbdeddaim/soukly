package com.soukly.gateway.filter;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.soukly.common.web.SouklyHeaders;

import reactor.core.publisher.Mono;

/**
 * Gives every request an {@code X-Request-Id}: the client's value when it is a safe identifier, otherwise a new
 * UUID. The identifier is forwarded to the service, returned in the response and used in the gateway logs and
 * error bodies. Runs first, before security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    /** Exchange attribute holding the request identifier. */
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    /** Accepted client identifiers: short, without characters that could forge log lines or headers. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(SouklyHeaders.REQUEST_ID);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(SouklyHeaders.REQUEST_ID, requestId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getAttributes().put(ATTRIBUTE, requestId);
        // Set just before the response is committed, so it also reaches error responses and is not
        // overwritten by the headers copied from the service response.
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(SouklyHeaders.REQUEST_ID, requestId);
            return Mono.empty();
        });
        return chain.filter(mutated);
    }

    /**
     * Returns the identifier of the request, or {@code null} if this filter has not run.
     */
    public static String requestId(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }
}
