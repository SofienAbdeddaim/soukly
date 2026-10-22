package com.soukly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import com.soukly.gateway.support.GatewayIntegrationTest;

// These tests use up the login, register, resend, password, email confirmation and search budgets of the shared
// gateway context: a fresh context afterwards keeps later test classes (RoutingTests registers once) from getting a 429.
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
    void verificationResendIsPublicAndLimitedWithItsOwnBudget() {
        for (int i = 0; i < 10; i++) {
            resend().expectStatus().isOk();
        }

        resend().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER);
        assertThat(USER_SERVICE.received()).hasSize(10);
    }

    @Test
    void passwordForgotResetAndEmailConfirmArePublicAndLimitedWithTheirOwnBudgets() {
        for (String path : new String[] { "/api/users/password/forgot", "/api/users/password/reset",
                "/api/users/email/confirm" }) {
            for (int i = 0; i < 10; i++) {
                post(path).expectStatus().isOk();
            }

            post(path).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                    .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                    .expectBody().jsonPath("$.type").isEqualTo("urn:soukly:problem:too-many-requests");
        }
        assertThat(USER_SERVICE.received()).hasSize(30);
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

    @Test
    void searchIsLimitedToOneHundredTwentyPerMinuteButNotTheReadingOfAListing() {
        for (int i = 0; i < 120; i++) {
            search().expectStatus().isOk();
        }
        // The bucket refills by 2 per second while the 120 searches run: a few more may pass before the 429
        int extra = 0;
        EntityExchangeResult<String> refused = search().expectBody(String.class).returnResult();
        while (refused.getStatus().is2xxSuccessful()) {
            extra++;
            assertThat(extra).isLessThan(10);
            refused = search().expectBody(String.class).returnResult();
        }

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refused.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(refused.getResponseBody()).contains("urn:soukly:problem:too-many-requests");
        client.get().uri("/api/listings/42").exchange().expectStatus().isOk();
        assertThat(LISTING_SERVICE.received()).hasSize(120 + extra + 1);
    }

    private ResponseSpec search() {
        return client.get().uri("/api/listings?q=peugeot").exchange();
    }

    private ResponseSpec login() {
        return client.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();
    }

    private ResponseSpec resend() {
        return post("/api/users/verify-email/resend");
    }

    private ResponseSpec post(String path) {
        return client.post().uri(path)
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
