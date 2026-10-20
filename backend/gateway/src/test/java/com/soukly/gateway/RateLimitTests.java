package com.soukly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.soukly.gateway.support.GatewayIntegrationTest;

// These tests use up the login and register budgets of the shared gateway context: a fresh context
// afterwards keeps later test classes (RoutingTests registers once) from getting a 429.
@DirtiesContext
class RateLimitTests extends GatewayIntegrationTest {

    @Test
    void eleventhLoginWithinAMinuteIsTooManyRequests() {
        for (int i = 0; i < 10; i++) {
            login().expectStatus().isOk();
        }

        login().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().value(HttpHeaders.RETRY_AFTER, value -> assertThat(Integer.parseInt(value)).isPositive())
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:soukly:problem:too-many-requests")
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.requestId").exists();

        assertThat(USER_SERVICE.received()).hasSize(10);
    }

    @Test
    void spoofedForwardedForFromAnUntrustedClientIsIgnored() {
        // No trusted proxy is configured: every request counts for the connection IP, whatever X-Forwarded-For says.
        for (int i = 0; i < 10; i++) {
            register("203.0.113." + i);
        }

        register("203.0.113.99").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(USER_SERVICE.received()).hasSizeLessThanOrEqualTo(10);
    }

    private ResponseSpec login() {
        return client.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();
    }

    private ResponseSpec register(String forwardedFor) {
        return client.post().uri("/api/users/register")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();
    }
}
