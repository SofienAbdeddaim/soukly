package com.soukly.user.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soukly.common.event.UserEvents;
import com.soukly.common.outbox.OutboxRelay;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;
import com.soukly.user.support.TestRedpanda;
import com.soukly.user.support.TestSmtpServer;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;

/**
 * The Gherkin scenarios of AUTH-05 about the deletion of the account and its purge. The deletion of the listings by
 * listing-service is tested there ({@code DeletedAccountTests}) and end to end in {@code e2e-tests}.
 */
@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionTests extends IntegrationTest {

    private static final String EMAIL = ProfileTests.EMAIL;
    private static final String PASSWORD = ProfileTests.PASSWORD;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private AccountPurge purge;

    private SessionCalls calls;
    private UUID userId;

    @BeforeEach
    void setUp() {
        calls = new SessionCalls(mockMvc);
        userId = createAccount(EMAIL, PASSWORD, "ACTIVE");
        jdbc.update("UPDATE users SET preferred_language = 'fr' WHERE id = ?", userId);
    }

    // --- Scenario: delete the account ----------------------------------------------------------------------

    @Test
    void theDeletedAccountCannotSignInAndItsAddressIsFreeAtOnce(CapturedOutput output) throws Exception {
        MvcResult phone = calls.login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();
        String phoneRefresh = SessionCalls.refreshToken(phone);
        String laptopRefresh = SessionCalls.refreshToken(calls.login(EMAIL, PASSWORD).andReturn());
        String accessToken = ProfileTests.accessToken(phone);

        MvcResult deleted = deleteAccount(accessToken, PASSWORD, FRONT_ORIGIN, phoneRefresh)
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(SessionCalls.setCookie(deleted)).contains("Max-Age=0", "Path=/api/users");

        // Status, dates and the address kept until the purge
        assertThat(jdbc.queryForMap("SELECT email, deleted_email, status, deleted_at FROM users WHERE id = ?",
                userId)).containsEntry("email", null).containsEntry("deleted_email", EMAIL)
                .containsEntry("status", "DELETED").extractingByKey("deleted_at").isNotNull();
        calls.refresh(phoneRefresh, FRONT_ORIGIN).andExpect(status().isUnauthorized());
        calls.refresh(laptopRefresh, FRONT_ORIGIN).andExpect(status().isUnauthorized());
        calls.login(EMAIL, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:invalid-credentials"));
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/users/{id}/public-profile", userId).header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound());

        MimeMessage goodbye = SMTP.awaitEmails(1).getFirst();
        assertThat(goodbye.getRecipients(RecipientType.TO)[0]).hasToString(EMAIL);
        assertThat(goodbye.getSubject()).isEqualTo("Votre compte Soukly a été supprimé");
        assertThat(TestSmtpServer.part(goodbye, "text/plain")).contains("sous 30 jours");

        // A new registration with the same address
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "amira@example.tn", "password": "Nouveau-depart-2027", "displayName": "Amira",
                 "phone": "20 123 456", "acceptTerms": true}"""))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE email = ?", String.class, EMAIL))
                .isEqualTo("PENDING_VERIFICATION");
        SMTP.awaitEmails(2);
        assertThat(output.getAll()).doesNotContain(PASSWORD, EMAIL);
    }

    @Test
    void userDeletedLeavesThroughTheOutboxWithoutPersonalData() throws Exception {
        String accessToken = ProfileTests.accessToken(calls.login(EMAIL, PASSWORD).andReturn());

        deleteAccount(accessToken, PASSWORD, FRONT_ORIGIN, null).andExpect(status().isNoContent());
        relay.relayAll();

        List<ConsumerRecord<String, String>> records = TestRedpanda.read(UserEvents.TOPIC,
                record -> userId.toString().equals(record.key()), received -> !received.isEmpty(),
                Duration.ofSeconds(15));
        assertThat(records).hasSize(1);
        String event = records.getFirst().value();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("user.deleted");
        assertThat(JsonPath.<Integer>read(event, "$.schemaVersion")).isEqualTo(1);
        assertThat(JsonPath.<String>read(event, "$.userId")).isEqualTo(userId.toString());
        assertThat(JsonPath.<String>read(event, "$.eventId")).isNotBlank();
        assertThat(JsonPath.<String>read(event, "$.occurredAt")).isNotBlank();
        assertThat(JsonPath.<Object>read(event, "$.data")).asString().isEqualTo("{}");
        assertThat(event).doesNotContain(EMAIL, "Amira", "+216");
        SMTP.awaitEmails(1);
    }

    @Test
    void theDeletionNeedsTheCurrentPasswordAndAnAllowedOrigin() throws Exception {
        String accessToken = ProfileTests.accessToken(calls.login(EMAIL, PASSWORD).andReturn());

        deleteAccount(accessToken, "not-my-password", FRONT_ORIGIN, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:current-password-invalid"));
        deleteAccount(accessToken, PASSWORD, "https://evil.example", null).andExpect(status().isForbidden());
        deleteAccount(accessToken, PASSWORD, null, null).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
        calls.login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void thePendingLinksStopWorking() throws Exception {
        String accessToken = ProfileTests.accessToken(calls.login(EMAIL, PASSWORD).andReturn());
        mockMvc.perform(post("/users/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\"}")).andExpect(status().isAccepted());
        String resetToken = TestSmtpServer.token(SMTP.awaitEmails(1).getFirst());
        SMTP.purge();

        deleteAccount(accessToken, PASSWORD, FRONT_ORIGIN, null).andExpect(status().isNoContent());

        mockMvc.perform(post("/users/password/reset").header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + resetToken + "\", \"newPassword\": \"Sidi-Bou-Said-bleu-42\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:reset-token-invalid"));
        SMTP.awaitEmails(1);
    }

    // --- Scenario: purge of the account --------------------------------------------------------------------

    @Test
    void anAccountDeletedThirtyOneDaysAgoIsErasedWithItsTokensAndSessions() throws Exception {
        String accessToken = ProfileTests.accessToken(calls.login(EMAIL, PASSWORD).andReturn());
        mockMvc.perform(post("/users/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\"}")).andExpect(status().isAccepted());
        deleteAccount(accessToken, PASSWORD, FRONT_ORIGIN, null).andExpect(status().isNoContent());
        UUID recent = createAccount("recent@example.tn", PASSWORD, "ACTIVE");
        jdbc.update("""
                UPDATE users SET status = 'DELETED', deleted_email = email, email = NULL, deleted_at = ?
                WHERE id = ?""", Timestamp.from(clock.instant().minus(Duration.ofDays(29))), recent);
        jdbc.update("UPDATE users SET deleted_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(31))), userId);
        assertThat(count("refresh_tokens", userId)).isEqualTo(1);
        assertThat(count("password_reset_tokens", userId)).isEqualTo(1);

        assertThat(purge.purge()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId)).isZero();
        assertThat(count("refresh_tokens", userId)).isZero();
        assertThat(count("password_reset_tokens", userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, recent))
                .isEqualTo(1);
        SMTP.awaitEmails(2);
    }

    private int count(String table, UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, user);
    }

    private ResultActions deleteAccount(String accessToken, String password, String origin, String refreshToken)
            throws Exception {
        var request = delete("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"" + password + "\"}");
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        if (refreshToken != null) {
            request.cookie(new Cookie(SessionCalls.COOKIE, refreshToken));
        }
        return mockMvc.perform(request);
    }
}
