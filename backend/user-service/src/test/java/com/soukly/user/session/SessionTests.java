package com.soukly.user.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;
import com.soukly.user.token.OpaqueTokens;

/**
 * Refresh, logout and CSRF scenarios of AUTH-02: rotation, reuse detection, expiration, logout and the
 * {@code Origin} check.
 */
@ExtendWith(OutputCaptureExtension.class)
class SessionTests extends IntegrationTest {

    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";
    private static final String CLEARED = "soukly_rt=; Path=/api/users; Max-Age=0;";

    private SessionCalls calls;
    private UUID userId;

    @BeforeEach
    void setUp() {
        calls = new SessionCalls(mockMvc);
        userId = createAccount(EMAIL, PASSWORD, "ACTIVE");
    }

    // --- Scenario: refresh ---------------------------------------------------------------------------------

    @Test
    void refreshGivesANewAccessTokenAndANewCookieValidThirtyDays() throws Exception {
        String first = signIn();

        clock.advance(Duration.ofMinutes(20));
        MvcResult result = calls.refresh(first, FRONT_ORIGIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andReturn();

        String second = SessionCalls.refreshToken(result);
        assertThat(second).isNotEqualTo(first);
        assertThat(SessionCalls.setCookie(result)).contains("HttpOnly", "Secure", "SameSite=Strict",
                "Max-Age=2592000");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(second, first);
        // Same family, the first token marked as replaced, the new one valid 30 days from now.
        assertThat(jdbc.queryForList("SELECT DISTINCT family_id FROM refresh_tokens")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class, sha256(first))).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT expires_at = ?::timestamptz + interval '30 days' FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class, clock.instant().toString(), sha256(second))).isTrue();
    }

    @Test
    void sessionSlidesAsLongAsTheUserComesBackWithinThirtyDays() throws Exception {
        String token = signIn();
        for (int visit = 0; visit < 4; visit++) {
            clock.advance(Duration.ofDays(29));
            token = SessionCalls.refreshToken(calls.refresh(token, FRONT_ORIGIN).andExpect(status().isOk())
                    .andReturn());
        }
    }

    // --- Scenario: reuse (token theft) ---------------------------------------------------------------------

    @Test
    void replacedTokenPresentedAgainRevokesTheWholeFamily(CapturedOutput output) throws Exception {
        String stolen = signIn();
        String latest = SessionCalls.refreshToken(calls.refresh(stolen, FRONT_ORIGIN).andReturn());

        MvcResult reuse = calls.refresh(stolen, FRONT_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:session-revoked"))
                .andReturn();
        assertThat(SessionCalls.setCookie(reuse)).startsWith(CLEARED);

        calls.refresh(latest, FRONT_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:session-revoked"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isZero();
        assertThat(output.getAll()).contains("Replaced refresh token presented again")
                .doesNotContain(stolen, latest);
    }

    @Test
    void anotherDeviceKeepsItsSessionWhenOneFamilyIsRevoked() throws Exception {
        String phone = signIn();
        String laptop = signIn();
        SessionCalls.refreshToken(calls.refresh(phone, FRONT_ORIGIN).andReturn());
        calls.refresh(phone, FRONT_ORIGIN).andExpect(status().isUnauthorized());

        calls.refresh(laptop, FRONT_ORIGIN).andExpect(status().isOk());
    }

    // --- Scenario: expiration ------------------------------------------------------------------------------

    @Test
    void tokenUnusedForMoreThanThirtyDaysIsExpiredAndTheCookieIsCleared() throws Exception {
        String token = signIn();

        clock.advance(Duration.ofDays(30).plusSeconds(1));
        MvcResult result = calls.refresh(token, FRONT_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:session-expired"))
                .andReturn();

        assertThat(SessionCalls.setCookie(result)).startsWith(CLEARED).contains("HttpOnly", "SameSite=Strict");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "unknown-token", "" })
    void missingOrUnknownCookieIsSessionExpired(String cookie) throws Exception {
        MvcResult result = calls.refresh(cookie, FRONT_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:session-expired"))
                .andReturn();

        assertThat(SessionCalls.setCookie(result)).startsWith(CLEARED);
    }

    // --- Scenario: logout ----------------------------------------------------------------------------------

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        String token = signIn();

        MvcResult result = calls.logout(token, FRONT_ORIGIN).andExpect(status().isNoContent()).andReturn();
        assertThat(SessionCalls.setCookie(result)).startsWith(CLEARED);

        calls.refresh(token, FRONT_ORIGIN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:session-revoked"));
    }

    @Test
    void logoutWithoutACookieStillAnswersNoContent() throws Exception {
        MvcResult result = calls.logout(null, FRONT_ORIGIN).andExpect(status().isNoContent()).andReturn();
        assertThat(SessionCalls.setCookie(result)).startsWith(CLEARED);
    }

    // --- Scenario: CSRF ------------------------------------------------------------------------------------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "https://evil.example", "http://localhost:4200.evil.example", "null" })
    void refreshFromAnotherOriginIsForbiddenAndIssuesNothing(String origin) throws Exception {
        String token = signIn();

        MvcResult result = calls.refresh(token, origin)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:forbidden"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        assertThat(SessionCalls.setCookie(result)).isNull();
        // The token was not consumed: the real front end can still use it.
        calls.refresh(token, FRONT_ORIGIN).andExpect(status().isOk());
    }

    @Test
    void logoutFromAnotherOriginIsForbiddenAndKeepsTheSession() throws Exception {
        String token = signIn();

        calls.logout(token, "https://evil.example").andExpect(status().isForbidden());

        calls.refresh(token, FRONT_ORIGIN).andExpect(status().isOk());
    }

    // --- Purge -----------------------------------------------------------------------------------------------

    @Test
    void dailyPurgeDeletesTokensExpiredOrRevokedForMoreThanSevenDays(@Autowired SessionPurge purge) throws Exception {
        String revoked = signIn();
        calls.logout(revoked, FRONT_ORIGIN);
        String expired = signIn();
        clock.advance(Duration.ofDays(8));
        String recent = signIn();
        calls.logout(recent, FRONT_ORIGIN);
        jdbc.update("INSERT INTO login_throttle (email_hash, failed_count, last_failed_at) VALUES ('idle', 3, ?)",
                Timestamp.from(clock.instant().minus(Duration.ofDays(2))));

        // revoked: 8 days ago; expired: 30 days after creation, not yet 7 days ago; recent: revoked just now.
        purge.purge();
        assertThat(hashes()).containsExactlyInAnyOrder(sha256(expired), sha256(recent));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_throttle", Integer.class)).isZero();

        clock.advance(Duration.ofDays(30));
        purge.purge();
        assertThat(hashes()).isEmpty();
    }

    private List<String> hashes() {
        return jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class);
    }

    private String signIn() throws Exception {
        return SessionCalls.refreshToken(calls.login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn());
    }

    private static String sha256(String value) {
        return OpaqueTokens.sha256Hex(value);
    }
}
