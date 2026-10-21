package com.soukly.user.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;
import com.soukly.user.support.TestKeys;
import com.soukly.user.token.OpaqueTokens;

import tools.jackson.databind.json.JsonMapper;

/**
 * Login scenarios of AUTH-02: nominal, invalid credentials, email not verified and failure throttling.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginTests extends IntegrationTest {

    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SessionCalls calls;

    @BeforeEach
    void setUp() {
        calls = new SessionCalls(mockMvc);
    }

    // --- Scenario: nominal login --------------------------------------------------------------------------

    @Test
    void activeAccountGetsAnAccessTokenAndAStrictHttpOnlyRefreshCookie(CapturedOutput output) throws Exception {
        UUID userId = createAccount(EMAIL, PASSWORD, "ACTIVE");

        MvcResult result = calls.login("  Amira@Example.TN ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.displayName").value("Amira Ben Salah"))
                .andExpect(jsonPath("$.user.preferredLanguage").value("ar"))
                .andReturn();

        String setCookie = SessionCalls.setCookie(result);
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/users",
                "Max-Age=2592000");
        String refreshToken = SessionCalls.refreshToken(result);
        assertThat(refreshToken).matches("[A-Za-z0-9_-]{43}");
        // Never in the body, and only its hash in the database.
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(refreshToken);
        assertThat(JSON.readValue(body, Map.class)).containsOnlyKeys("accessToken", "tokenType", "expiresIn", "user");
        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM refresh_tokens");
        assertThat(stored.get("token_hash")).isEqualTo(sha256(refreshToken));
        assertThat(stored.get("user_id")).isEqualTo(userId);
        assertThat(stored.get("user_agent")).isEqualTo("Mozilla/5.0 (Linux; Android 14) Soukly tests");

        String accessToken = JSON.readTree(body).get("accessToken").asString();
        assertThat(output.getAll()).doesNotContain(PASSWORD, refreshToken, accessToken, EMAIL);
    }

    @Test
    void accessTokenIsAnRs256JwtWithTheActiveKidAndNoPersonalData() throws Exception {
        UUID userId = createAccount(EMAIL, PASSWORD, "ACTIVE");

        String body = calls.login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        SignedJWT jwt = SignedJWT.parse(JSON.readTree(body).get("accessToken").asString());

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(TestKeys.ACTIVE_KID);
        assertThat(jwt.verify(new RSASSAVerifier(TestKeys.ACTIVE.toRSAPublicKey()))).isTrue();

        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp", "jti", "roles");
        assertThat(claims.getIssuer()).isEqualTo("soukly-user-service");
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getStringListClaim("roles")).containsExactly("USER");
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getPayload().toString()).doesNotContain(EMAIL, "Amira", "+216");
    }

    // --- Scenario: invalid credentials ---------------------------------------------------------------------

    @Test
    void unknownEmailAndWrongPasswordGetTheSameAnswer() throws Exception {
        createAccount(EMAIL, PASSWORD, "ACTIVE");

        MvcResult unknown = calls.login("nobody@example.tn", PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult wrongPassword = calls.login(EMAIL, "Not-the-right-one-2026")
                .andExpect(status().isUnauthorized()).andReturn();

        for (MvcResult result : new MvcResult[] { unknown, wrongPassword }) {
            assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(SessionCalls.setCookie(result)).isNull();
        }
        assertThat(JSON.readValue(unknown.getResponse().getContentAsString(), Map.class))
                .isEqualTo(JSON.readValue(wrongPassword.getResponse().getContentAsString(), Map.class))
                .containsEntry("type", "urn:soukly:problem:invalid-credentials")
                .containsEntry("status", 401);
        assertThat(unknown.getResponse().getHeaderNames())
                .containsExactlyInAnyOrderElementsOf(wrongPassword.getResponse().getHeaderNames());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void unknownEmailIsHashedLikeAKnownOneSoThatTheResponseTimeIsTheSame() throws Exception {
        createAccount(EMAIL, PASSWORD, "ACTIVE");
        // Warm-up, then the fastest of two attempts each (4 failures in all, below the throttling threshold).
        calls.login("warm-up@example.tn", PASSWORD);
        long unknown = Math.min(timed("nobody@example.tn"), timed("nobody-else@example.tn"));
        long wrongPassword = Math.min(timed(EMAIL), timed(EMAIL));

        // Without the dummy hash, an unknown email would answer in a small fraction of an Argon2 hash.
        assertThat(unknown).isGreaterThan(wrongPassword / 2);
    }

    @Test
    void missingFieldsAreInvalidCredentials() throws Exception {
        mockMvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:invalid-credentials"));
    }

    // --- Scenario: email not verified ----------------------------------------------------------------------

    @Test
    void pendingAccountWithTheRightPasswordIsForbiddenAndGetsNoCookie() throws Exception {
        createAccount(EMAIL, PASSWORD, "PENDING_VERIFICATION");

        MvcResult result = calls.login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:email-not-verified"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        assertThat(SessionCalls.setCookie(result)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void pendingAccountWithAWrongPasswordIsInvalidCredentials() throws Exception {
        createAccount(EMAIL, PASSWORD, "PENDING_VERIFICATION");

        calls.login(EMAIL, "Not-the-right-one-2026")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:invalid-credentials"));
    }

    // --- Scenario: failure throttling ----------------------------------------------------------------------

    @Test
    void afterFiveFailuresEvenTheRightPasswordWaitsThenSucceedsAndResetsTheCounter() throws Exception {
        createAccount(EMAIL, PASSWORD, "ACTIVE");
        failTimes(EMAIL, 5);

        MvcResult throttled = calls.login(EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:too-many-requests"))
                .andReturn();
        assertThat(Long.parseLong(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isGreaterThanOrEqualTo(30);
        assertThat(SessionCalls.setCookie(throttled)).isNull();

        clock.advance(Duration.ofSeconds(30));
        calls.login(EMAIL, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_throttle", Integer.class)).isZero();

        // The counter starts again from zero: 4 more failures are free.
        failTimes(EMAIL, 4);
        calls.login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void delayDoublesWithEveryFurtherFailureUpToFifteenMinutes() throws Exception {
        createAccount(EMAIL, PASSWORD, "ACTIVE");
        failTimes(EMAIL, 5);

        long[] expected = { 60, 120, 240, 480, 900, 900 };
        clock.advance(Duration.ofSeconds(30));
        for (long seconds : expected) {
            calls.login(EMAIL, "Not-the-right-one-2026").andExpect(status().isUnauthorized());
            MvcResult throttled = calls.login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests()).andReturn();
            assertThat(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo(Long.toString(seconds));
            clock.advance(Duration.ofSeconds(seconds));
        }
        // Never locked for good.
        calls.login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailIsThrottledLikeAKnownOneSoThatThrottlingRevealsNoAccount() throws Exception {
        failTimes("nobody@example.tn", 5);

        calls.login("nobody@example.tn", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
        assertThat(jdbc.queryForList("SELECT email_hash FROM login_throttle", String.class))
                .containsExactly(sha256("nobody@example.tn"));
    }

    @Test
    void failuresOnOneAccountDoNotThrottleAnother() throws Exception {
        createAccount(EMAIL, PASSWORD, "ACTIVE");
        createAccount("youssef@example.tn", PASSWORD, "ACTIVE");
        failTimes(EMAIL, 5);

        calls.login("youssef@example.tn", PASSWORD).andExpect(status().isOk());
    }

    private void failTimes(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            calls.login(email, "Not-the-right-one-2026").andExpect(status().isUnauthorized());
        }
    }

    private long timed(String email) throws Exception {
        long start = System.nanoTime();
        calls.login(email, "Not-the-right-one-2026").andExpect(status().isUnauthorized());
        return System.nanoTime() - start;
    }

    private static String sha256(String value) {
        return OpaqueTokens.sha256Hex(value);
    }
}
