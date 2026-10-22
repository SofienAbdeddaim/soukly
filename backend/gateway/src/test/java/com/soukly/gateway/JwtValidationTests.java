package com.soukly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nimbusds.jose.jwk.RSAKey;
import com.soukly.gateway.support.GatewayIntegrationTest;
import com.soukly.gateway.support.TestJwts;

class JwtValidationTests extends GatewayIntegrationTest {

    @Test
    void validTokenIsForwardedWithTheSameAuthorizationHeader() {
        String authorization = "Bearer " + validToken();

        client.post().uri("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Vélo\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(LISTING_SERVICE.received()).singleElement().satisfies(received -> {
            assertThat(received.method()).isEqualTo("POST");
            assertThat(received.uri()).isEqualTo("/listings");
            assertThat(received.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(authorization);
            assertThat(received.headers().containsHeader("X-User-Id")).isFalse();
        });
    }

    @Test
    void ownListingsAreForwardedWithAValidToken() {
        String authorization = "Bearer " + validToken();

        client.get().uri("/api/listings/mine?status=EXPIRED")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange()
                .expectStatus().isOk();

        assertThat(LISTING_SERVICE.received()).singleElement().satisfies(received -> {
            assertThat(received.uri()).isEqualTo("/listings/mine?status=EXPIRED");
            assertThat(received.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(authorization);
        });
    }

    @Test
    void missingTokenIsUnauthorizedProblemAndNeverReachesTheService() {
        client.post().uri("/api/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:soukly:problem:unauthorized")
                .jsonPath("$.title").isEqualTo("Unauthorized")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").isEqualTo("/api/listings")
                .jsonPath("$.requestId").exists();

        assertThat(LISTING_SERVICE.received()).isEmpty();
    }

    // verify-email/resend is public too: RateLimitTests calls it without a token (not here, to keep its budget).
    @Test
    void registrationPathsArePublic() {
        for (String path : new String[] { "/api/users/register", "/api/users/verify-email" }) {
            client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isOk();
        }

        assertThat(USER_SERVICE.received()).extracting(received -> received.uri())
                .containsExactly("/users/register", "/users/verify-email");
    }

    // login is public too: RateLimitTests calls it without a token (not here, to keep its budget).
    @Test
    void sessionPathsArePublicAndKeepTheirCookie() {
        for (String path : new String[] { "/api/users/refresh", "/api/users/logout" }) {
            client.post().uri(path)
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .cookie("soukly_rt", "opaque-refresh-token")
                    .exchange()
                    .expectStatus().isOk();
        }

        assertThat(USER_SERVICE.received()).extracting(received -> received.uri())
                .containsExactly("/users/refresh", "/users/logout");
        assertThat(USER_SERVICE.received()).allSatisfy(received -> {
            assertThat(received.headers().getFirst(HttpHeaders.COOKIE)).isEqualTo("soukly_rt=opaque-refresh-token");
            assertThat(received.headers().getFirst(HttpHeaders.ORIGIN)).isEqualTo("http://localhost:4200");
        });
    }

    @Test
    void profilePathsNeedAToken() {
        client.get().uri("/api/users/me").exchange().expectStatus().isUnauthorized();
        client.patch().uri("/api/users/me").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isUnauthorized();
        client.post().uri("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/users/me/email").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isUnauthorized();
        client.delete().uri("/api/users/me/email-change").exchange().expectStatus().isUnauthorized();
        client.delete().uri("/api/users/me").exchange().expectStatus().isUnauthorized();

        assertThat(USER_SERVICE.received()).isEmpty();
    }

    @Test
    void jwksOfUserServiceIsNotExposed() {
        for (String path : new String[] { "/.well-known/jwks.json", "/api/.well-known/jwks.json",
                "/api/users/.well-known/jwks.json" }) {
            client.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
        client.get().uri("/.well-known/jwks.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void protectedPathsRequireAToken() {
        client.get().uri("/api/users/me").exchange().expectStatus().isUnauthorized();
        client.delete().uri("/api/listings/42").exchange().expectStatus().isUnauthorized();
        client.patch().uri("/api/listings/42").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/listings/42/deactivate").exchange().expectStatus().isUnauthorized();
        // Under the public GET /api/listings/**, yet protected (ANN-05)
        client.get().uri("/api/listings/mine").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/listings/mine?status=DRAFT").header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/social/accounts").exchange().expectStatus().isUnauthorized();

        assertThat(USER_SERVICE.received()).isEmpty();
        assertThat(LISTING_SERVICE.received()).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        Instant past = Instant.now().minusSeconds(600);
        assertRejected(TestJwts.rs256(SIGNING_KEY, TestJwts.validClaims()
                .issueTime(Date.from(past.minusSeconds(900)))
                .expirationTime(Date.from(past))
                .build()));
    }

    @Test
    void tokenExpiredWithinTheClockSkewIsAccepted() {
        Instant now = Instant.now();
        String token = TestJwts.rs256(SIGNING_KEY, TestJwts.validClaims()
                .issueTime(Date.from(now.minusSeconds(900)))
                .expirationTime(Date.from(now.minusSeconds(10)))
                .build());

        client.post().uri("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() {
        RSAKey attackerKey = TestJwts.newKey(SIGNING_KEY.getKeyID());
        assertRejected(TestJwts.rs256(attackerKey, TestJwts.validClaims().build()));
    }

    @Test
    void hs256TokenIsRejected() {
        assertRejected(TestJwts.hs256(SIGNING_KEY.getKeyID(), TestJwts.validClaims().build()));
    }

    @Test
    void unsignedTokenIsRejected() {
        assertRejected(TestJwts.unsigned(TestJwts.validClaims().build()));
    }

    @Test
    void wrongIssuerIsRejected() {
        assertRejected(TestJwts.rs256(SIGNING_KEY, TestJwts.validClaims().issuer("someone-else").build()));
    }

    @Test
    void newKeyIsAcceptedAfterTheJwksIsReloaded() {
        // The current JWKS (key-1 only) is in the gateway cache.
        client.post().uri("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isOk();

        RSAKey rotatedKey = TestJwts.newKey("key-rotated");
        JWTS.publish(rotatedKey);

        client.post().uri("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.rs256(rotatedKey, TestJwts.validClaims().build()))
                .exchange()
                .expectStatus().isOk();
        assertThat(LISTING_SERVICE.received()).hasSize(2);
    }

    @Test
    void unknownKeyNotInTheJwksIsRejected() {
        assertRejected(TestJwts.rs256(TestJwts.newKey("never-published"), TestJwts.validClaims().build()));
    }

    private void assertRejected(String token) {
        client.post().uri("/api/listings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.requestId").exists();

        assertThat(LISTING_SERVICE.received()).isEmpty();
    }
}
