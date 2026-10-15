package com.soukly.gateway.error;

import java.net.URI;
import java.util.Map;

import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.soukly.common.error.ProblemDetails;
import com.soukly.gateway.filter.RequestIdFilter;

import reactor.core.publisher.Mono;

/**
 * Writes an {@code application/problem+json} body (format shared in {@code common}) for the errors produced by
 * the gateway itself: 401, 403, 404, 429, 503, 504 and 500.
 */
@Component
public class ProblemResponseWriter {

    private static final ResolvableType PROBLEM_TYPE = ResolvableType.forClass(ProblemDetail.class);

    private final HttpMessageWriter<ProblemDetail> writer;

    @SuppressWarnings("unchecked")
    public ProblemResponseWriter(ServerCodecConfigurer codecs) {
        this.writer = (HttpMessageWriter<ProblemDetail>) codecs.getWriters().stream()
                .filter(candidate -> candidate.canWrite(PROBLEM_TYPE, MediaType.APPLICATION_PROBLEM_JSON))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No JSON encoder for application/problem+json"));
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetails.forStatus(status, detail,
                URI.create(exchange.getRequest().getPath().value()), RequestIdFilter.requestId(exchange));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return writer.write(Mono.just(problem), PROBLEM_TYPE, MediaType.APPLICATION_PROBLEM_JSON, response, Map.of());
    }
}
