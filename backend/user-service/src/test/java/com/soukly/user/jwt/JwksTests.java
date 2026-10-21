package com.soukly.user.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.TestKeys;

/**
 * Scenario "JWKS and rotation" of AUTH-02, and the validation of the access tokens by user-service itself
 * (defense in depth: same rules as the gateway).
 */
class JwksTests extends IntegrationTest {

    /** Not mapped yet: with a valid token the request passes security and gets 404. */
    private static final String PROTECTED_PATH = "/users/me";

    @Test
    void jwksPublishesTheActiveAndThePreviousPublicKeysCachedFiveMinutes() throws Exception {
        String body = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300"))
                .andReturn().getResponse().getContentAsString();

        JWKSet jwks = JWKSet.parse(body);
        assertThat(jwks.getKeys()).extracting(key -> key.getKeyID()).containsExactly("k2", "k1");
        for (var key : jwks.getKeys()) {
            RSAKey rsa = key.toRSAKey();
            assertThat(rsa.isPrivate()).isFalse();
            assertThat(key.getKeyUse().identifier()).isEqualTo("sig");
            assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
            assertThat(rsa.getModulus()).isNotNull();
            assertThat(rsa.getPublicExponent()).isNotNull();
        }
        assertThat(jwks.getKeyByKeyId("k2").toRSAKey().toRSAPublicKey())
                .isEqualTo(TestKeys.ACTIVE.toRSAPublicKey());
        assertThat(jwks.getKeyByKeyId("k1").toRSAKey().toRSAPublicKey())
                .isEqualTo(TestKeys.PREVIOUS.toRSAPublicKey());
        // No private parameter at all.
        assertThat(body).doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
    }

    @Test
    void tokenSignedByThePreviousKeyIsStillAccepted() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bearer(TestKeys.PREVIOUS, claims())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenSignedByTheActiveKeyIsAccepted() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bearer(TestKeys.ACTIVE, claims())))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTokenOnAProtectedPathIsUnauthorizedProblem() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:unauthorized"))
                .andExpect(jsonPath("$.instance").value(PROTECTED_PATH));
    }

    @Test
    void forgedTokensAreRejected() throws Exception {
        RSAKey attacker = TestKeys.generate(TestKeys.ACTIVE_KID, 2048);
        Instant past = Instant.now().minusSeconds(600);
        List<String> forged = List.of(
                bearer(attacker, claims()),
                bearer(TestKeys.ACTIVE, claimsBuilder().issuer("someone-else").build()),
                bearer(TestKeys.ACTIVE, claimsBuilder().issueTime(Date.from(past.minusSeconds(900)))
                        .expirationTime(Date.from(past)).build()),
                "Bearer " + hs256(claims()),
                "Bearer " + new PlainJWT(claims()).serialize());

        for (String authorization : forged) {
            mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void anInvalidTokenIsIgnoredOnPublicPaths() throws Exception {
        // The front end may send an expired access token along with a refresh: the cookie decides.
        mockMvc.perform(post("/users/refresh").header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:session-expired"));
        mockMvc.perform(get("/.well-known/jwks.json").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    private static JWTClaimsSet claims() {
        return claimsBuilder().build();
    }

    private static JWTClaimsSet.Builder claimsBuilder() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("soukly-user-service")
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("USER"));
    }

    private static String bearer(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return "Bearer " + jwt.serialize();
    }

    private static String hs256(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(TestKeys.ACTIVE_KID).build(),
                claims);
        jwt.sign(new MACSigner(new byte[32]));
        return jwt.serialize();
    }
}
