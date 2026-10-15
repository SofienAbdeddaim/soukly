package com.soukly.gateway.error;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.soukly.gateway.filter.RequestIdFilter;

import io.netty.channel.ConnectTimeoutException;
import reactor.core.publisher.Mono;

/**
 * Turns every error that reaches the gateway into {@code application/problem+json}.
 *
 * <ul>
 * <li>Target service down or unreachable (connection refused or connection timeout): 503.</li>
 * <li>Target service too slow (response timeout): 504, raised by Spring Cloud Gateway.</li>
 * <li>Any other {@link ResponseStatusException} (for example 404 when no route matches): its status.</li>
 * <li>Anything else: 500, logged without the request content.</li>
 * </ul>
 * Runs before the default Spring Boot handler.
 */
@Component
@Order(-2)
class ProblemDetailsHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    private final ProblemResponseWriter problems;

    ProblemDetailsHandler(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (isUnreachable(ex)) {
            log.warn("Target service unavailable for {} {} [requestId={}]: {}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(), RequestIdFilter.requestId(exchange),
                    ex.getClass().getSimpleName());
            return problems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    "The service is temporarily unavailable. Please try again later.");
        }
        if (ex instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            if (status != null) {
                return problems.write(exchange, status, detailOf(status));
            }
        }
        log.error("Unexpected error for {} {} [requestId={}]", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), RequestIdFilter.requestId(exchange), ex);
        return problems.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, detailOf(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static boolean isUnreachable(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
                    || cause instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private static String detailOf(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "No resource matches this path.";
            case METHOD_NOT_ALLOWED -> "This method is not allowed on this path.";
            case GATEWAY_TIMEOUT -> "The service took too long to respond. Please try again later.";
            case INTERNAL_SERVER_ERROR -> "An unexpected error occurred.";
            default -> status.getReasonPhrase() + ".";
        };
    }
}
