package com.soukly.user.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import com.soukly.common.event.SocialEvents;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.TestRedpanda;
import com.soukly.user.support.TestSmtpServer;

import jakarta.mail.internet.MimeMessage;

/**
 * Consumer of the social events (SOC-06): the email "connect again" of {@code social.connection.needs-reconnect} in
 * the language of the member, deduplication by {@code eventId}, other types ignored, DLQ of the social events after
 * the retries. Events are produced to the real topic, as social-publisher does.
 */
class SocialReconnectEmailTests extends IntegrationTest {

    private static final long EMAIL_TIMEOUT = 30_000;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private UUID member;

    @BeforeEach
    void member() {
        member = createAccount("amira@example.tn", "Jasmin-de-Carthage-2026", "ACTIVE");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            fr | FACEBOOK  | Sfax Auto  | Reliez à nouveau votre Page Facebook   | Relier à nouveau
            ar | INSTAGRAM | @sfax.auto | أعد ربط حسابك على إنستغرام            | إعادة الربط
            en | INSTAGRAM | @sfax.auto | Connect your Instagram account again   | Connect again""")
    void aConnectionToReconnectSendsAnEmailInTheLanguageOfTheMember(String lang, String network, String account,
            String subject, String button) throws Exception {
        jdbc.update("UPDATE users SET preferred_language = ? WHERE id = ?", lang, member);
        UUID eventId = UUID.randomUUID();

        send(needsReconnect(eventId, member, network, account));

        MimeMessage email = SMTP.awaitEmails(1, EMAIL_TIMEOUT).getFirst();
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo("amira@example.tn");
        assertThat(email.getSubject()).isEqualTo(subject);
        String text = TestSmtpServer.part(email, "text/plain");
        String html = TestSmtpServer.part(email, "text/html");
        String link = FRONT_ORIGIN + "/" + lang + "/account/social";
        assertThat(text).contains("Amira Ben Salah", account, button + ": " + link);
        assertThat(html).contains("href=\"" + link + "\"", button)
                .contains("dir=\"" + ("ar".equals(lang) ? "rtl" : "ltr") + "\"")
                .contains("dir=\"auto\">" + account + "</p>")
                .contains("<title>" + subject + "</title>");
        awaitProcessed(eventId);
    }

    @Test
    void theSameEventDeliveredTwiceSendsOneEmailAndOtherTypesNone() throws Exception {
        jdbc.update("UPDATE users SET preferred_language = 'fr' WHERE id = ?", member);
        UUID eventId = UUID.randomUUID();
        String event = needsReconnect(eventId, member, "FACEBOOK", "Sfax Auto");

        send(event);
        send(event);
        send(envelope(UUID.randomUUID(), "social.post.failed", member, "{ \"network\": \"FACEBOOK\" }"));
        UUID marker = UUID.randomUUID();
        send(needsReconnect(marker, member, "FACEBOOK", null));

        SMTP.awaitEmails(2, EMAIL_TIMEOUT);
        awaitProcessed(marker);
        assertThat(SMTP.received()).hasSize(2);
        assertThat(processed(eventId)).isEqualTo(1);
        // Without the name of the Page, the email still reads
        assertThat(TestSmtpServer.part(SMTP.received().get(1), "text/plain")).contains("Relier à nouveau")
                .doesNotContain("null");
    }

    @Test
    void anEmailThatCannotBeSentGoesToTheSocialDlqAfterThreeRetries() throws Exception {
        UUID eventId = UUID.randomUUID();
        SMTP.stop();

        send(needsReconnect(eventId, member, "INSTAGRAM", "@sfax.auto"));

        List<ConsumerRecord<String, String>> dead = deadLetters(eventId);
        assertThat(dead).hasSize(1);
        assertThat(header(dead.getFirst(), "kafka_dlt-original-topic")).isEqualTo(SocialEvents.TOPIC);
        assertThat(header(dead.getFirst(), "kafka_dlt-exception-message"))
                .contains("Could not send the social-reconnect email to user " + member)
                .doesNotContain("amira@example.tn");
        assertThat(processed(eventId)).isZero();
    }

    @Test
    void anUnknownNetworkGoesToTheDlqWithoutRetry() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(envelope(eventId, SocialEvents.CONNECTION_NEEDS_RECONNECT, member, "{ \"network\": \"TIKTOK\" }"));

        List<ConsumerRecord<String, String>> dead = deadLetters(eventId);
        assertThat(dead).hasSize(1);
        assertThat(header(dead.getFirst(), "kafka_dlt-exception-message"))
                .contains("Missing or unknown data.network in event " + eventId);
        assertThat(SMTP.received()).isEmpty();
    }

    private void send(String value) throws Exception {
        kafka.send(SocialEvents.TOPIC, member.toString(), value).get();
    }

    private String needsReconnect(UUID eventId, UUID userId, String network, String account) {
        return envelope(eventId, SocialEvents.CONNECTION_NEEDS_RECONNECT, userId, "{ \"network\": \"" + network
                + "\", \"accountName\": " + (account == null ? "null" : "\"" + account + "\"") + " }");
    }

    private static String envelope(UUID eventId, String type, UUID userId, String data) {
        return """
                { "eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "%s", "userId": "%s",
                  "data": %s }""".formatted(eventId, type, Instant.now(), userId, data);
    }

    private int processed(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId);
    }

    private void awaitProcessed(UUID eventId) throws InterruptedException {
        Instant deadline = Instant.now().plusMillis(EMAIL_TIMEOUT);
        while (processed(eventId) == 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(processed(eventId)).isEqualTo(1);
    }

    private static List<ConsumerRecord<String, String>> deadLetters(UUID eventId) {
        return TestRedpanda.read(SocialEvents.DLQ_TOPIC,
                record -> record.value() != null && record.value().contains(eventId.toString()),
                records -> !records.isEmpty(), Duration.ofSeconds(40));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
