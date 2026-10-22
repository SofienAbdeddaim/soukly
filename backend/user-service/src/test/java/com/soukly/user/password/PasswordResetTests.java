package com.soukly.user.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;
import com.soukly.user.support.TestSmtpServer;
import com.soukly.user.token.OpaqueTokens;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.MimeMessage;

/**
 * The Gherkin scenarios of AUTH-04 (forgotten password), against PostgreSQL and a test SMTP server.
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetTests extends IntegrationTest {

    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";
    private static final String NEW_PASSWORD = "Sidi-Bou-Said-bleu-42";

    private SessionCalls calls;
    private UUID userId;

    @BeforeEach
    void setUp() {
        calls = new SessionCalls(mockMvc);
        userId = createAccount(EMAIL, PASSWORD, "ACTIVE");
        jdbc.update("UPDATE users SET preferred_language = 'fr' WHERE id = ?", userId);
    }

    /**
     * Emails are sent after the commit, on another thread: wait for every link and confirmation of the test, so that
     * none reaches the mailbox of the next one.
     */
    @AfterEach
    void awaitPendingEmails() {
        Integer expected = jdbc.queryForObject(
                "SELECT count(*) + count(used_at) FROM password_reset_tokens", Integer.class);
        SMTP.awaitEmails(expected);
    }

    // --- Scenario: nominal ---------------------------------------------------------------------------------

    @Test
    void resetSignsOutEveryDeviceAndSignsInThisOne(CapturedOutput output) throws Exception {
        String phone = SessionCalls.refreshToken(calls.login(EMAIL, PASSWORD).andReturn());
        String laptop = SessionCalls.refreshToken(calls.login(EMAIL, PASSWORD).andReturn());

        MvcResult forgot = forgot(EMAIL).andExpect(status().isAccepted()).andReturn();
        assertThat(forgot.getResponse().getContentAsString()).isEmpty();
        MimeMessage link = SMTP.awaitEmails(1).getFirst();
        assertThat(link.getRecipients(RecipientType.TO)[0]).hasToString(EMAIL);
        assertThat(link.getSubject()).isEqualTo("Choisissez un nouveau mot de passe Soukly");
        assertThat(TestSmtpServer.part(link, "text/html"))
                .contains("http://localhost:4200/fr/account/reset-password?token=", "60 minutes");
        String token = TestSmtpServer.token(link);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class))
                .isEqualTo(OpaqueTokens.sha256Hex(token));

        MvcResult reset = reset(token, NEW_PASSWORD, FRONT_ORIGIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andReturn();
        String current = SessionCalls.refreshToken(reset);
        assertThat(SessionCalls.setCookie(reset)).contains("HttpOnly", "SameSite=Strict", "Path=/api/users");
        assertThat(reset.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        calls.refresh(phone, FRONT_ORIGIN).andExpect(status().isUnauthorized());
        calls.refresh(laptop, FRONT_ORIGIN).andExpect(status().isUnauthorized());
        calls.refresh(current, FRONT_ORIGIN).andExpect(status().isOk());

        List<MimeMessage> emails = SMTP.awaitEmails(2);
        MimeMessage confirmation = emails.get(1);
        assertThat(confirmation.getSubject()).isEqualTo("Votre mot de passe Soukly a été modifié");
        assertThat(TestSmtpServer.part(confirmation, "text/plain"))
                .contains("(heure de Tunis)", "Ce n'était pas vous ?",
                        "http://localhost:4200/fr/account/forgot-password");

        calls.login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        calls.login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(output.getAll()).doesNotContain(token, NEW_PASSWORD, EMAIL);
    }

    @Test
    void resetClearsTheLoginFailures() throws Exception {
        for (int i = 0; i < 6; i++) {
            calls.login(EMAIL, "wrong-password-" + i);
        }
        calls.login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());

        reset(requestLink(), NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk());

        calls.login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    // --- Scenario: unknown email ---------------------------------------------------------------------------

    @Test
    void unknownEmailAnswersTheSameWithoutEmail() throws Exception {
        MvcResult known = forgot(EMAIL).andExpect(status().isAccepted()).andReturn();
        SMTP.awaitEmails(1);

        MvcResult unknown = forgot("inconnu@example.tn").andExpect(status().isAccepted()).andReturn();

        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(known.getResponse().getContentAsString());
        assertThat(unknown.getResponse().getHeaderNames()).isEqualTo(known.getResponse().getHeaderNames());
        assertNoMoreEmails(1);
    }

    @Test
    void malformedEmailIsRejected() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("email.invalid"));
    }

    // --- Scenarios: single use, expired, replaced ----------------------------------------------------------

    @Test
    void tokenWorksOnlyOnce() throws Exception {
        String token = requestLink();
        reset(token, NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk());

        expectInvalidToken(reset(token, "Another-Strong-Pass-7", FRONT_ORIGIN));
    }

    @Test
    void tokenOlderThanOneHourIsRejected() throws Exception {
        String token = requestLink();

        clock.advance(Duration.ofMinutes(61));

        expectInvalidToken(reset(token, NEW_PASSWORD, FRONT_ORIGIN));
    }

    @Test
    void newRequestReplacesTheOlderLink() throws Exception {
        String first = requestLink();
        forgot(EMAIL).andExpect(status().isAccepted());
        String second = TestSmtpServer.token(SMTP.awaitEmails(2).get(1));

        expectInvalidToken(reset(first, NEW_PASSWORD, FRONT_ORIGIN));
        reset(second, NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = { "unknown-token", "" })
    void unknownTokenIsRejected(String token) throws Exception {
        expectInvalidToken(reset(token, NEW_PASSWORD, FRONT_ORIGIN));
    }

    // --- Scenario: password refused ------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({ "password123, password.too-common", "short, password.length",
            "amira@example.tn, password.same-as-email" })
    void refusedPasswordKeepsTheTokenUsable(String password, String code) throws Exception {
        String token = requestLink();

        reset(token, password, FRONT_ORIGIN)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value(code));

        reset(token, NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk());
    }

    // --- Scenario: per-account limit -----------------------------------------------------------------------

    @Test
    void fourthRequestWithinAnHourSendsNoEmail() throws Exception {
        for (int i = 0; i < 3; i++) {
            forgot(EMAIL).andExpect(status().isAccepted());
            SMTP.awaitEmails(i + 1);
        }

        forgot(EMAIL).andExpect(status().isAccepted());
        assertNoMoreEmails(3);

        clock.advance(Duration.ofMinutes(61));
        forgot(EMAIL).andExpect(status().isAccepted());
        SMTP.awaitEmails(4);
    }

    // --- Scenario: pending account -------------------------------------------------------------------------

    @Test
    void resetActivatesAPendingAccountAndSignsIn() throws Exception {
        jdbc.execute("TRUNCATE users CASCADE");
        UUID pending = createAccount(EMAIL, PASSWORD, "PENDING_VERIFICATION");

        reset(requestLink(), NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(pending.toString()));

        assertThat(jdbc.queryForMap("SELECT status, email_verified_at FROM users WHERE id = ?", pending))
                .containsEntry("status", "ACTIVE")
                .satisfies(row -> assertThat(row.get("email_verified_at")).isNotNull());
    }

    // --- Scenario: Arabic ----------------------------------------------------------------------------------

    @Test
    void emailsAreInThePreferredLanguageWithTheArabicLink() throws Exception {
        jdbc.update("UPDATE users SET preferred_language = 'ar' WHERE id = ?", userId);

        String token = requestLink();
        MimeMessage link = SMTP.received().getFirst();
        assertThat(link.getSubject()).isEqualTo("اختر كلمة مرور جديدة لحسابك على سوقلي");
        assertThat(TestSmtpServer.part(link, "text/html"))
                .contains("dir=\"rtl\"", "http://localhost:4200/ar/account/reset-password?token=");

        reset(token, NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk());
        MimeMessage confirmation = SMTP.awaitEmails(2).get(1);
        assertThat(confirmation.getSubject()).isEqualTo("تم تغيير كلمة مرور حسابك على سوقلي");
        assertThat(TestSmtpServer.part(confirmation, "text/plain")).contains("/ar/account/forgot-password")
                // Western digits, as written in Tunisia
                .containsPattern("[0-9]{1,2}:[0-9]{2}");
    }

    // --- CSRF ----------------------------------------------------------------------------------------------

    @Test
    void resetFromAnotherOriginIsRefusedAndKeepsTheToken() throws Exception {
        String token = requestLink();

        reset(token, NEW_PASSWORD, "https://evil.example").andExpect(status().isForbidden());
        reset(token, NEW_PASSWORD, null).andExpect(status().isForbidden());

        reset(token, NEW_PASSWORD, FRONT_ORIGIN).andExpect(status().isOk());
    }

    // --- Helpers -------------------------------------------------------------------------------------------

    /** Requests a link for {@link #EMAIL} and returns its token. */
    private String requestLink() throws Exception {
        int before = SMTP.received().size();
        forgot(EMAIL).andExpect(status().isAccepted());
        return TestSmtpServer.token(SMTP.awaitEmails(before + 1).get(before));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/users/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String newPassword, String origin) throws Exception {
        var request = post("/users/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Linux; Android 14) Soukly tests")
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}");
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        return mockMvc.perform(request);
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:reset-token-invalid"))
                .andExpect(jsonPath("$.status").value(400));
    }

    /** Emails are sent asynchronously: give a late email the time to arrive before checking the count. */
    private static void assertNoMoreEmails(int expected) {
        await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2))
                .until(() -> SMTP.received().size() == expected);
        assertThat(SMTP.received()).hasSize(expected);
    }
}
