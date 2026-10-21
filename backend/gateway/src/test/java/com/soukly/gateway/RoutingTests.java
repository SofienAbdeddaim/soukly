package com.soukly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.soukly.gateway.support.FakeService.Received;
import com.soukly.gateway.support.GatewayIntegrationTest;

class RoutingTests extends GatewayIntegrationTest {

    @Test
    void publicGetIsForwardedWithoutTokenAndPrefixRemoved() {
        String requestId = client.get().uri("/api/listings/42?page=2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.service").isEqualTo("listing-service")
                .returnResult().getResponseHeaders().getFirst("X-Request-Id");

        assertThat(LISTING_SERVICE.received()).singleElement().satisfies(received -> {
            assertThat(received.method()).isEqualTo("GET");
            assertThat(received.uri()).isEqualTo("/listings/42?page=2");
            assertThat(received.headers().getFirst("X-Request-Id")).isEqualTo(requestId);
        });
        assertThat(requestId).matches("[0-9a-f-]{36}");
    }

    @Test
    void keepsTheClientRequestId() {
        client.get().uri("/api/listings")
                .header("X-Request-Id", "front-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "front-123");

        assertThat(LISTING_SERVICE.received().getFirst().headers().getFirst("X-Request-Id")).isEqualTo("front-123");
    }

    @Test
    void replacesAnUnsafeRequestId() {
        client.get().uri("/api/listings")
                .header("X-Request-Id", "bad id with spaces")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Request-Id", id -> assertThat(id).matches("[0-9a-f-]{36}"));
    }

    @Test
    void routesEachPrefixToItsService() {
        client.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("user-service");

        assertThat(USER_SERVICE.received()).singleElement()
                .extracting(Received::uri).isEqualTo("/users/register");
    }

    @Test
    void referentialsArePublicAndRoutedToListingService() {
        for (String path : new String[] { "/api/categories", "/api/categories/vehicles.cars/attributes",
                "/api/locations/governorates", "/api/locations/governorates/sfax/delegations" }) {
            client.get().uri(path)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.service").isEqualTo("listing-service");
        }

        assertThat(LISTING_SERVICE.received()).extracting(Received::uri).containsExactly("/categories",
                "/categories/vehicles.cars/attributes", "/locations/governorates",
                "/locations/governorates/sfax/delegations");
    }

    @Test
    void referentialWritesRequireAToken() {
        client.post().uri("/api/categories").exchange().expectStatus().isUnauthorized();
        client.delete().uri("/api/locations/governorates/sfax").exchange().expectStatus().isUnauthorized();

        assertThat(LISTING_SERVICE.received()).isEmpty();
    }

    @Test
    void theContactOfAListingRequiresATokenThenIsForwarded() {
        client.post().uri("/api/listings/42/contact")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(LISTING_SERVICE.received()).isEmpty();

        client.post().uri("/api/listings/42/contact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk();
        assertThat(LISTING_SERVICE.received()).singleElement().satisfies(received -> {
            assertThat(received.method()).isEqualTo("POST");
            assertThat(received.uri()).isEqualTo("/listings/42/contact");
        });
    }

    @Test
    void internalEndpointsAreNeverRouted() {
        for (String path : new String[] { "/internal/users/42/contact", "/api/internal/users/42/contact",
                "/internal/users/42/public-profile", "/api/internal/users/42/public-profile" }) {
            client.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                    .header("X-Internal-Token", "guessed")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        assertThat(USER_SERVICE.received()).isEmpty();
        assertThat(LISTING_SERVICE.received()).isEmpty();
    }

    @Test
    void invalidTokenOnPublicPathIsForwardedUnchanged() {
        client.get().uri("/api/listings/42")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isOk();

        assertThat(LISTING_SERVICE.received().getFirst().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer not-a-jwt");
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void unknownPathWithValidTokenIsNotFoundProblem() {
        client.get().uri("/api/unknown")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:soukly:problem:not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.instance").isEqualTo("/api/unknown")
                .jsonPath("$.requestId").exists();
    }
}
