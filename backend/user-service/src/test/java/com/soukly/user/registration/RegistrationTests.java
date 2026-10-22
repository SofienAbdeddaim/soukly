package com.soukly.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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

import com.jayway.jsonpath.JsonPath;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;
import com.soukly.user.support.TestSmtpServer;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.MimeMessage;

/**
 * The Gherkin scenarios of AUTH-01, plus the rules around them, against PostgreSQL and a test SMTP server.
 */
@ExtendWith(OutputCaptureExtension.class)
class RegistrationTests extends IntegrationTest {

    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";

    // --- Scenario: nominal registration -------------------------------------------------------------------

    @Test
    void registrationCreatesAPendingAccountAndSendsTheVerificationEmailInTheRequestLanguage(CapturedOutput output)
            throws Exception {
        MvcResult result = register(EMAIL, "20 123 456", "ar")
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();

        Map<String, Object> user = user(EMAIL);
        assertThat(user).containsEntry("status", "PENDING_VERIFICATION")
                .containsEntry("phone_e164", "+21620123456")
                .containsEntry("preferred_language", "ar")
                .containsEntry("display_name", "Amira Ben Salah");
        assertThat(user.get("password_hash")).asString().startsWith("$argon2id$");
        assertThat(user.get("terms_accepted_at")).isNotNull();
        assertThat(user.get("email_verified_at")).isNull();

        MimeMessage email = SMTP.awaitEmails(1).getFirst();
        assertThat(email.getRecipients(RecipientType.TO)[0]).hasToString(EMAIL);
        assertThat(email.getSubject()).isEqualTo("أكّد عنوان بريدك الإلكتروني على سوقلي");
        String body = html(email);
        assertThat(body).contains("dir=\"rtl\"", "lang=\"ar\"", "http://localhost:4200/ar/account/verify-email?token=");

        // Only the hash of the token is stored, and nothing sensitive reaches the logs.
        String token = TestSmtpServer.token(email);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM email_verification_tokens", String.class))
                .isEqualTo(sha256Hex(token)).isNotEqualTo(token);
        assertThat(output.getAll()).doesNotContain(PASSWORD, token, EMAIL, "20 123 456", "+21620123456");
    }

    @ParameterizedTest
    @CsvSource({
            "fr-FR;q=0.9,     fr, Confirmez votre adresse email Soukly",
            "en,              en, Confirm your Soukly email address",
            "'de, en;q=0.5',  en, Confirm your Soukly email address",
            "de,              fr, Confirmez votre adresse email Soukly",
            "'',              fr, Confirmez votre adresse email Soukly" })
    void preferredLanguageComesFromAcceptLanguageWithFrenchByDefault(String acceptLanguage, String expected,
            String subject) throws Exception {
        register(EMAIL, "+216 20123456", acceptLanguage).andExpect(status().isAccepted());

        assertThat(user(EMAIL)).containsEntry("preferred_language", expected);
        MimeMessage email = SMTP.awaitEmails(1).getFirst();
        assertThat(email.getSubject()).isEqualTo(subject);
        assertThat(html(email)).contains("dir=\"ltr\"",
                "http://localhost:4200/" + expected + "/account/verify-email?token=");
    }

    // --- Scenario: verification ---------------------------------------------------------------------------

    @Test
    void validTokenActivatesTheAccountOnlyOnce() throws Exception {
        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());
        String token = TestSmtpServer.token(SMTP.awaitEmails(1).getFirst());

        verify(token).andExpect(status().isNoContent());
        assertThat(user(EMAIL)).containsEntry("status", "ACTIVE");
        assertThat(user(EMAIL).get("email_verified_at")).isNotNull();

        expectInvalidToken(verify(token));
    }

    // --- Scenario: accepted version of the terms (LEG-01) -------------------------------------------------

    @Test
    void registrationRecordsTheCurrentTermsVersionAndTheProfileReturnsIt() throws Exception {
        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());
        assertThat(user(EMAIL)).containsEntry("terms_version", "2026-10-22");
        verify(TestSmtpServer.token(SMTP.awaitEmails(1).getFirst())).andExpect(status().isNoContent());

        String login = new SessionCalls(mockMvc).login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + JsonPath.read(login, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsVersion").value("2026-10-22"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "unknown-token", "" })
    void unknownTokenIsRejected(String token) throws Exception {
        expectInvalidToken(verify(token));
    }

    // --- Scenario: expired token --------------------------------------------------------------------------

    @Test
    void tokenOlderThan24HoursIsRejected() throws Exception {
        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());
        String token = TestSmtpServer.token(SMTP.awaitEmails(1).getFirst());
        jdbc.update("UPDATE email_verification_tokens SET created_at = now() - interval '25 hours', "
                + "expires_at = now() - interval '1 hour'");

        expectInvalidToken(verify(token));
        assertThat(user(EMAIL)).containsEntry("status", "PENDING_VERIFICATION");
    }

    // --- Scenario: email already used ---------------------------------------------------------------------

    @Test
    void registeringTheEmailOfAnActiveAccountAnswersTheSameAndWarnsTheOwner() throws Exception {
        MvcResult first = register(EMAIL, "20123456", "fr").andExpect(status().isAccepted()).andReturn();
        verify(TestSmtpServer.token(SMTP.awaitEmails(1).getFirst())).andExpect(status().isNoContent());

        MvcResult second = register("Amira@Example.tn ", "98 765 432", "en").andExpect(status().isAccepted())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(second.getResponse().getHeaderNames()).isEqualTo(first.getResponse().getHeaderNames());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
        assertThat(user(EMAIL)).containsEntry("phone_e164", "+21620123456");

        MimeMessage warning = SMTP.awaitEmails(2).get(1);
        assertThat(warning.getRecipients(RecipientType.TO)[0]).hasToString(EMAIL);
        // Sent in the language of the account.
        assertThat(warning.getSubject()).isEqualTo("Vous avez déjà un compte Soukly");
        assertThat(html(warning)).contains("http://localhost:4200/fr/account/login",
                "http://localhost:4200/fr/account/forgot-password");
    }

    @Test
    void registeringAPendingEmailAgainSendsANewLinkAndInvalidatesTheOldOne() throws Exception {
        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());
        String oldToken = TestSmtpServer.token(SMTP.awaitEmails(1).getFirst());

        register(" AMIRA@example.tn", "98 765 432", "en").andExpect(status().isAccepted());
        MimeMessage email = SMTP.awaitEmails(2).get(1);
        String newToken = TestSmtpServer.token(email);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isOne();
        // The latest registration wins: its data and language replace the earlier ones.
        assertThat(user(EMAIL)).containsEntry("phone_e164", "+21698765432").containsEntry("preferred_language", "en");
        assertThat(email.getSubject()).isEqualTo("Confirm your Soukly email address");
        expectInvalidToken(verify(oldToken));
        verify(newToken).andExpect(status().isNoContent());
    }

    // --- Scenarios: invalid fields ------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "+33612345678", "1234", "20 123 45", "+216 20 123 456 7", "phone 20123456" })
    void nonTunisianOrMalformedPhoneIsRejected(String phone) throws Exception {
        expectFieldError(register(EMAIL, phone, "fr"), "phone", "phone.invalid");
    }

    @Test
    void commonPasswordIsRejected() throws Exception {
        expectFieldError(register(body(EMAIL, "password123", "20123456", true), "fr"), "password",
                "password.too-common");
        expectFieldError(register(body(EMAIL, "PassWord123", "20123456", true), "fr"), "password",
                "password.too-common");
    }

    @Test
    void passwordEqualToTheEmailIsRejected() throws Exception {
        expectFieldError(register(body(EMAIL, "Amira@Example.tn", "20123456", true), "fr"), "password",
                "password.same-as-email");
    }

    @ParameterizedTest
    @CsvSource({ "short, password.length", "'', password.required" })
    void passwordLengthIsChecked(String password, String code) throws Exception {
        expectFieldError(register(body(EMAIL, password, "20123456", true), "fr"), "password", code);
        expectFieldError(register(body(EMAIL, "x".repeat(129), "20123456", true), "fr"), "password",
                "password.length");
    }

    @Test
    void termsMustBeAccepted() throws Exception {
        expectFieldError(register(body(EMAIL, PASSWORD, "20123456", false), "fr"), "acceptTerms", "terms.required");
    }

    @Test
    void emptyBodyListsEveryMissingField() throws Exception {
        perform("/users/register", "{}", "fr")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("email.required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("password.required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'displayName')].code").value("displayName.required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')].code").value("phone.required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'acceptTerms')].code").value("terms.required"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void emailAndDisplayNameFormatsAreChecked() throws Exception {
        String body = """
                {"email": "amira@", "password": "%s", "displayName": " A ", "phone": "20123456", "acceptTerms": true}
                """.formatted(PASSWORD);
        perform("/users/register", body, "fr")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("email.invalid"))
                .andExpect(jsonPath("$.errors[?(@.field == 'displayName')].code").value("displayName.length"));
    }

    @Test
    void malformedJsonIsAProblem() throws Exception {
        perform("/users/register", "{not json", "fr")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:bad-request"));
    }

    // --- Scenario: resend ---------------------------------------------------------------------------------

    @Test
    void resendSendsANewLinkAndTheOldOneIsRefused() throws Exception {
        register(EMAIL, "20123456", "ar").andExpect(status().isAccepted());
        String oldToken = TestSmtpServer.token(SMTP.awaitEmails(1).getFirst());

        resend("Amira@example.tn").andExpect(status().isAccepted());
        MimeMessage email = SMTP.awaitEmails(2).get(1);
        assertThat(email.getSubject()).isEqualTo("أكّد عنوان بريدك الإلكتروني على سوقلي");
        String newToken = TestSmtpServer.token(email);

        assertThat(newToken).isNotEqualTo(oldToken);
        expectInvalidToken(verify(oldToken));
        verify(newToken).andExpect(status().isNoContent());
    }

    @Test
    void resendIsLimitedToThreePerHourAndPerAccount() throws Exception {
        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());
        SMTP.awaitEmails(1);
        for (int i = 0; i < 3; i++) {
            resend(EMAIL).andExpect(status().isAccepted());
            // Emails are sent asynchronously: wait for each one so that they arrive in order.
            SMTP.awaitEmails(2 + i);
        }
        String lastToken = TestSmtpServer.token(SMTP.awaitEmails(4).get(3));

        resend(EMAIL).andExpect(status().isAccepted());
        register(EMAIL, "98765432", "en").andExpect(status().isAccepted());

        assertNoMoreEmails(4);
        // Over the limit nothing changes: no new token and no new registration data.
        assertThat(user(EMAIL)).containsEntry("phone_e164", "+21620123456");
        verify(lastToken).andExpect(status().isNoContent());
    }

    @Test
    void resendForAnUnknownOrActiveEmailAnswersTheSameWithoutEmail() throws Exception {
        resend("nobody@example.tn").andExpect(status().isAccepted());

        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());
        verify(TestSmtpServer.token(SMTP.awaitEmails(1).getFirst())).andExpect(status().isNoContent());
        resend(EMAIL).andExpect(status().isAccepted());

        assertNoMoreEmails(1);
    }

    @Test
    void resendWithAMalformedEmailIsRejected() throws Exception {
        expectFieldError(resend("not-an-email"), "email", "email.invalid");
    }

    // --- Scenario: SMTP failure ---------------------------------------------------------------------------

    @Test
    void registrationSucceedsWhenTheSmtpServerIsDown(CapturedOutput output) throws Exception {
        SMTP.stop();

        register(EMAIL, "20123456", "fr").andExpect(status().isAccepted());

        assertThat(user(EMAIL)).containsEntry("status", "PENDING_VERIFICATION");
        await().atMost(Duration.ofSeconds(15))
                .until(() -> output.getAll().contains("Could not send verification email to user"));
        assertThat(output.getAll()).doesNotContain(EMAIL);
    }

    // --- Helpers ------------------------------------------------------------------------------------------

    private ResultActions register(String email, String phone, String acceptLanguage) throws Exception {
        return register(body(email, PASSWORD, phone, true), acceptLanguage);
    }

    private ResultActions register(String body, String acceptLanguage) throws Exception {
        return perform("/users/register", body, acceptLanguage);
    }

    private ResultActions verify(String token) throws Exception {
        return perform("/users/verify-email", "{\"token\": \"%s\"}".formatted(token), null);
    }

    private ResultActions resend(String email) throws Exception {
        return perform("/users/verify-email/resend", "{\"email\": \"%s\"}".formatted(email), null);
    }

    private ResultActions perform(String path, String body, String acceptLanguage) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return mockMvc.perform(request);
    }

    private static String body(String email, String password, String phone, boolean acceptTerms) {
        return """
                {"email": "%s", "password": "%s", "displayName": " Amira Ben Salah ", "phone": "%s", "acceptTerms": %s}
                """.formatted(email, password, phone, acceptTerms);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String html(MimeMessage email) {
        return TestSmtpServer.part(email, "text/html");
    }

    private Map<String, Object> user(String email) {
        return jdbc.queryForMap("SELECT * FROM users WHERE email = ?", email);
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:verification-token-invalid"))
                .andExpect(jsonPath("$.status").value(400));
    }

    private static void expectFieldError(ResultActions result, String field, String code) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[?(@.field == '%s')].code".formatted(field)).value(code));
    }

    /** Emails are sent asynchronously: give a late email the time to arrive before checking the count. */
    private static void assertNoMoreEmails(int expected) {
        await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2))
                .until(() -> SMTP.received().size() == expected);
        List<MimeMessage> received = SMTP.received();
        assertThat(received).hasSize(expected);
    }
}
