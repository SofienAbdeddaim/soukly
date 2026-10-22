package com.soukly.user.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;
import com.soukly.user.support.TestSmtpServer;
import com.soukly.user.token.OpaqueTokens;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;

/**
 * The Gherkin scenarios of AUTH-05 about the profile, the password and the email, against PostgreSQL and a test SMTP
 * server. The deletion of the account is in {@link AccountDeletionTests}.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProfileTests extends IntegrationTest {

    static final String EMAIL = "amira@example.tn";
    static final String PASSWORD = "Jasmin-de-Carthage-2026";
    private static final String NEW_PASSWORD = "Sidi-Bou-Said-bleu-42";
    private static final String NEW_EMAIL = "amira.bs@example.tn";
    private static final String MERGE_PATCH = "application/merge-patch+json";

    private SessionCalls calls;
    private UUID userId;

    @BeforeEach
    void setUp() {
        calls = new SessionCalls(mockMvc);
        userId = createAccount(EMAIL, PASSWORD, "ACTIVE");
        jdbc.update("UPDATE users SET preferred_language = 'fr' WHERE id = ?", userId);
    }

    // --- Reading -------------------------------------------------------------------------------------------

    @Test
    void theProfileOfTheSignedInAccount() throws Exception {
        String accessToken = accessToken(login());

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist())
                .andExpect(jsonPath("$.displayName").value("Amira Ben Salah"))
                .andExpect(jsonPath("$.phone").value("+21620123456"))
                .andExpect(jsonPath("$.preferredLanguage").value("fr"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void theProfileNeedsAnAccessToken() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/users/me").contentType(MERGE_PATCH).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/users/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/users/me/email-change")).andExpect(status().isUnauthorized());
    }

    // --- Scenario: change the phone number -----------------------------------------------------------------

    @Test
    void theNewPhoneNumberIsTheOneEveryListingShows() throws Exception {
        String accessToken = accessToken(login());

        authorized(patch("/users/me").contentType(MERGE_PATCH).content("{\"phone\": \"98 765 432\"}"), accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+21698765432"))
                .andExpect(jsonPath("$.displayName").value("Amira Ben Salah"))
                .andExpect(jsonPath("$.preferredLanguage").value("fr"));

        authorized(get("/users/me"), accessToken).andExpect(jsonPath("$.phone").value("+21698765432"));
        // What listing-service reads at each "Show the number" (CATA-03)
        mockMvc.perform(get("/internal/users/{id}/contact", userId).header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+21698765432"));
    }

    @Test
    void theNameAndTheLanguageOfTheEmailsChangeTogether() throws Exception {
        String accessToken = accessToken(login());

        authorized(patch("/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"  Amira B. S.  \", \"preferredLanguage\": \"ar\", \"email\": \"x@y.tn\"}"),
                accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Amira B. S."))
                .andExpect(jsonPath("$.preferredLanguage").value("ar"))
                // The email has its own confirmed change
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void invalidFieldsAreAllListedAndNothingChanges() throws Exception {
        String accessToken = accessToken(login());

        authorized(patch("/users/me").contentType(MERGE_PATCH)
                .content("{\"displayName\": null, \"phone\": \"123\", \"preferredLanguage\": \"de\"}"), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[*].field").value(
                        containsInAnyOrder("displayName", "phone", "preferredLanguage")))
                .andExpect(jsonPath("$.errors[?(@.field == 'displayName')].code").value("displayName.required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')].code").value("phone.invalid"))
                .andExpect(jsonPath("$.errors[?(@.field == 'preferredLanguage')].code")
                        .value("preferredLanguage.invalid"));
        authorized(patch("/users/me").contentType(MERGE_PATCH).content("{\"displayName\": \"A\"}"), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("displayName.length"));

        authorized(get("/users/me"), accessToken)
                .andExpect(jsonPath("$.displayName").value("Amira Ben Salah"))
                .andExpect(jsonPath("$.phone").value("+21620123456"));
    }

    // --- Scenario: change the password ---------------------------------------------------------------------

    @Test
    void changingThePasswordKeepsThisDeviceAndSignsOutTheOthers(CapturedOutput output) throws Exception {
        MvcResult phone = login();
        String phoneRefresh = SessionCalls.refreshToken(phone);
        String laptopRefresh = SessionCalls.refreshToken(login());

        authorized(post("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(changePassword(PASSWORD, NEW_PASSWORD))
                .cookie(new Cookie(SessionCalls.COOKIE, phoneRefresh)), accessToken(phone))
                .andExpect(status().isNoContent());

        calls.refresh(phoneRefresh, FRONT_ORIGIN).andExpect(status().isOk());
        calls.refresh(laptopRefresh, FRONT_ORIGIN).andExpect(status().isUnauthorized());
        calls.login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        calls.login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());

        MimeMessage confirmation = SMTP.awaitEmails(1).getFirst();
        assertThat(confirmation.getRecipients(RecipientType.TO)[0]).hasToString(EMAIL);
        assertThat(confirmation.getSubject()).isEqualTo("Votre mot de passe Soukly a été modifié");
        assertThat(output.getAll()).doesNotContain(PASSWORD, NEW_PASSWORD, EMAIL);
    }

    @Test
    void aWrongCurrentPasswordIsRefusedThenThrottled() throws Exception {
        String accessToken = accessToken(login());

        authorized(post("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(changePassword("not-my-password", NEW_PASSWORD)), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:current-password-invalid"));
        for (int i = 0; i < 4; i++) {
            authorized(post("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                    .content(changePassword("not-my-password-" + i, NEW_PASSWORD)), accessToken)
                    .andExpect(status().isBadRequest());
        }

        // The 5 free failures are used up, as for the login (AUTH-02): even the right password waits
        authorized(post("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(changePassword(PASSWORD, NEW_PASSWORD)), accessToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        calls.login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void theNewPasswordFollowsTheRulesOfTheRegistration() throws Exception {
        String accessToken = accessToken(login());

        authorized(post("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(changePassword(PASSWORD, "short")), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("password.length"));
        calls.login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    // --- Scenario: change the email ------------------------------------------------------------------------

    @Test
    void theEmailChangesOnlyOnceTheNewAddressConfirmsIt(CapturedOutput output) throws Exception {
        String accessToken = accessToken(login());

        authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail(" Amira.BS@example.tn ", PASSWORD)), accessToken)
                .andExpect(status().isAccepted());

        List<MimeMessage> emails = SMTP.awaitEmails(2);
        MimeMessage link = to(emails, NEW_EMAIL);
        assertThat(link.getSubject()).isEqualTo("Confirmez votre nouvelle adresse email Soukly");
        assertThat(TestSmtpServer.part(link, "text/html"))
                .contains("http://localhost:4200/fr/account/confirm-email?token=", "24 heures");
        String token = TestSmtpServer.token(link);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM email_change_tokens", String.class))
                .isEqualTo(OpaqueTokens.sha256Hex(token));
        MimeMessage notice = to(emails, EMAIL);
        assertThat(notice.getSubject()).isEqualTo("Une demande de changement de votre adresse email Soukly a été faite");
        assertThat(TestSmtpServer.part(notice, "text/plain"))
                .contains(NEW_EMAIL, "changez votre mot de passe", "http://localhost:4200/fr/account/forgot-password");

        // Until the confirmation, the login keeps the current address
        authorized(get("/users/me"), accessToken)
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.pendingEmail").value(NEW_EMAIL));
        calls.login(EMAIL, PASSWORD).andExpect(status().isOk());
        calls.login(NEW_EMAIL, PASSWORD).andExpect(status().isUnauthorized());

        // Opened on another device: no access token
        confirm(token).andExpect(status().isNoContent());

        authorized(get("/users/me"), accessToken)
                .andExpect(jsonPath("$.email").value(NEW_EMAIL))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());
        calls.login(NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        calls.login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        // Single use
        confirm(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:email-change-token-invalid"));
        assertThat(output.getAll()).doesNotContain(token, NEW_EMAIL, PASSWORD);
    }

    @Test
    void anAddressOfAnotherAccountGetsTheSameAnswerAndTheAccountExistsEmail() throws Exception {
        UUID other = createAccount("pris@example.tn", "Autre-mot-de-passe-2026", "ACTIVE");
        jdbc.update("UPDATE users SET preferred_language = 'en', display_name = 'Karim' WHERE id = ?", other);
        String accessToken = accessToken(login());

        MvcResult result = authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail("pris@example.tn", PASSWORD)), accessToken)
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();

        List<MimeMessage> emails = SMTP.awaitEmails(2);
        MimeMessage exists = to(emails, "pris@example.tn");
        assertThat(exists.getSubject()).isEqualTo("You already have a Soukly account");
        assertThat(TestSmtpServer.part(exists, "text/plain")).contains("Hello Karim").doesNotContain("token=");
        assertThat(to(emails, EMAIL).getSubject())
                .isEqualTo("Une demande de changement de votre adresse email Soukly a été faite");
        // Shown as pending, like any other address: nothing tells that it is taken
        authorized(get("/users/me"), accessToken).andExpect(jsonPath("$.pendingEmail").value("pris@example.tn"));
    }

    @Test
    void anAddressTakenBeforeTheConfirmationIsAConflict() throws Exception {
        String token = requestEmailChange(accessToken(login()));
        createAccount(NEW_EMAIL, "Autre-mot-de-passe-2026", "PENDING_VERIFICATION");

        confirm(token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:email-taken"));
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId)).isEqualTo(EMAIL);
    }

    @Test
    void aNewRequestReplacesThePreviousOneAndACancelledRequestHasNoLink() throws Exception {
        String accessToken = accessToken(login());
        String first = requestEmailChange(accessToken);
        SMTP.purge();
        authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail("amira.tn@example.tn", PASSWORD)), accessToken)
                .andExpect(status().isAccepted());
        String second = TestSmtpServer.token(to(SMTP.awaitEmails(2), "amira.tn@example.tn"));

        confirm(first).andExpect(status().isBadRequest());

        authorized(delete("/users/me/email-change"), accessToken).andExpect(status().isNoContent());
        authorized(get("/users/me"), accessToken).andExpect(jsonPath("$.pendingEmail").doesNotExist());
        confirm(second).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:email-change-token-invalid"));
    }

    @Test
    void theNewAddressIsCheckedWithTheCurrentPassword() throws Exception {
        String accessToken = accessToken(login());

        authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail(NEW_EMAIL, "not-my-password")), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:current-password-invalid"));
        authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail("not-an-email", PASSWORD)), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newEmail"))
                .andExpect(jsonPath("$.errors[0].code").value("email.invalid"));
        authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail(EMAIL, PASSWORD)), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("email.unchanged"));
        assertThat(SMTP.received()).isEmpty();
    }

    // --- Helpers -------------------------------------------------------------------------------------------

    private MvcResult login() throws Exception {
        return calls.login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
    }

    static String accessToken(MvcResult login) throws Exception {
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions authorized(MockHttpServletRequestBuilder request, String accessToken) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** Asks for {@link #NEW_EMAIL} and returns the token of its link; the mailbox is empty again afterwards. */
    private String requestEmailChange(String accessToken) throws Exception {
        authorized(post("/users/me/email").contentType(MediaType.APPLICATION_JSON)
                .content(changeEmail(NEW_EMAIL, PASSWORD)), accessToken)
                .andExpect(status().isAccepted());
        String token = TestSmtpServer.token(to(SMTP.awaitEmails(2), NEW_EMAIL));
        SMTP.purge();
        return token;
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/users/email/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"));
    }

    private static MimeMessage to(List<MimeMessage> emails, String address) {
        return emails.stream().filter(email -> {
            try {
                return email.getRecipients(RecipientType.TO)[0].toString().equals(address);
            } catch (MessagingException e) {
                throw new IllegalStateException(e);
            }
        }).findFirst().orElseThrow(() -> new AssertionError("No email to " + address));
    }

    private static String changePassword(String current, String next) {
        return "{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}";
    }

    private static String changeEmail(String newEmail, String current) {
        return "{\"newEmail\": \"" + newEmail + "\", \"currentPassword\": \"" + current + "\"}";
    }
}
