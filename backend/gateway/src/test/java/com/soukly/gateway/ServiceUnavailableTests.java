package com.soukly.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.soukly.gateway.support.GatewayIntegrationTest;

class ServiceUnavailableTests extends GatewayIntegrationTest {

    @Test
    void stoppedServiceIsServiceUnavailableProblem() {
        client.get().uri("/api/social/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .header("X-Request-Id", "req-503")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals("X-Request-Id", "req-503")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:soukly:problem:service-unavailable")
                .jsonPath("$.title").isEqualTo("Service Unavailable")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").isEqualTo("/api/social/accounts")
                .jsonPath("$.requestId").isEqualTo("req-503");
    }
}
