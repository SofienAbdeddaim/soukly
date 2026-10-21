package com.soukly.user.listing;

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

import com.soukly.common.event.ListingEvents;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.TestRedpanda;
import com.soukly.user.support.TestSmtpServer;

import jakarta.mail.internet.MimeMessage;

/**
 * Consumer of the listing events (ANN-03): reminder email of {@code listing.expiring-soon} in the language of the
 * seller, deduplication by {@code eventId}, other types ignored, DLQ after the retries. Events are produced to the
 * real topic, as listing-service does.
 */
class ListingReminderTests extends IntegrationTest {

    private static final long EMAIL_TIMEOUT = 30_000;
    private static final String TITLE = "Peugeot 208 Allure";
    /** 11 December 2026 at 23:30 UTC, already 12 December in Tunis (UTC+1): the date shown is the one of Tunisia. */
    private static final Instant EXPIRES_AT = Instant.parse("2026-12-11T23:30:00Z");

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private UUID owner;
    private UUID listing;

    @BeforeEach
    void seller() {
        owner = createAccount("amira@example.tn", "Jasmin-de-Carthage-2026", "ACTIVE");
        listing = UUID.randomUUID();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            fr | Votre annonce expire bientôt : Peugeot 208 Allure | 12 décembre 2026 | Remettre en ligne
            ar | إعلانك سينتهي قريبًا: Peugeot 208 Allure | 12 ديسمبر 2026 | إعادة النشر
            en | Your listing expires soon: Peugeot 208 Allure | December 12, 2026 | Put back online""")
    void aListingThatExpiresSoonSendsAReminderInTheLanguageOfTheSeller(String lang, String subject, String date,
            String button) throws Exception {
        jdbc.update("UPDATE users SET preferred_language = ? WHERE id = ?", lang, owner);
        UUID eventId = UUID.randomUUID();

        send(expiringSoon(eventId, owner));

        MimeMessage email = SMTP.awaitEmails(1, EMAIL_TIMEOUT).getFirst();
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo("amira@example.tn");
        assertThat(email.getSubject()).isEqualTo(subject);
        String text = TestSmtpServer.part(email, "text/plain");
        String html = TestSmtpServer.part(email, "text/html");
        String link = FRONT_ORIGIN + "/" + lang + "/account/listings";
        assertThat(text).contains("Amira Ben Salah", TITLE, date, button + ": " + link);
        assertThat(html).contains(date, "href=\"" + link + "\"", button)
                .contains("dir=\"" + ("ar".equals(lang) ? "rtl" : "ltr") + "\"")
                // The title alone in its box, and the subject in <title>
                .contains("dir=\"auto\">" + TITLE + "</p>")
                .contains("<title>" + subject + "</title>");
        assertThat(processed(eventId)).isEqualTo(1);
    }

    @Test
    void theSameEventDeliveredTwiceSendsOneEmail() throws Exception {
        UUID eventId = UUID.randomUUID();
        String event = expiringSoon(eventId, owner);

        send(event);
        send(event);
        // Same key, so same partition: once the marker is handled, both copies were consumed before it
        UUID marker = UUID.randomUUID();
        send(expiringSoon(marker, owner));

        SMTP.awaitEmails(2, EMAIL_TIMEOUT);
        awaitProcessed(marker);
        assertThat(SMTP.received()).hasSize(2);
        assertThat(processed(eventId)).isEqualTo(1);
    }

    @Test
    void otherTypesAndUnknownOrPendingAccountsSendNothing() throws Exception {
        send(envelope(UUID.randomUUID(), ListingEvents.PUBLISHED, owner,
                "{ \"title\": \"" + TITLE + "\", \"expiresAt\": \"" + EXPIRES_AT + "\" }"));
        send(envelope(UUID.randomUUID(), ListingEvents.RENEWED, owner,
                "{ \"title\": \"" + TITLE + "\", \"expiresAt\": \"" + EXPIRES_AT + "\" }"));
        send(envelope(UUID.randomUUID(), "listing.future-type", owner, "{}"));
        UUID unknownAccount = UUID.randomUUID();
        send(expiringSoon(unknownAccount, UUID.randomUUID()));
        UUID pending = createAccount("pending@example.tn", "Jasmin-de-Carthage-2026", "PENDING_VERIFICATION");
        UUID pendingEvent = UUID.randomUUID();
        send(expiringSoon(pendingEvent, pending));
        UUID marker = UUID.randomUUID();
        send(expiringSoon(marker, owner));

        SMTP.awaitEmails(1, EMAIL_TIMEOUT);
        awaitProcessed(marker);
        assertThat(SMTP.received()).hasSize(1);
        assertThat(processed(unknownAccount)).isEqualTo(1);
        assertThat(processed(pendingEvent)).isEqualTo(1);
    }

    @Test
    void aReminderThatCannotBeSentGoesToTheDlqAfterThreeRetries() throws Exception {
        UUID eventId = UUID.randomUUID();
        SMTP.stop();

        send(expiringSoon(eventId, owner));

        List<ConsumerRecord<String, String>> dead = deadLetters(eventId);
        assertThat(dead).hasSize(1);
        ConsumerRecord<String, String> record = dead.getFirst();
        assertThat(record.key()).isEqualTo(listing.toString());
        assertThat(header(record, "kafka_dlt-original-topic")).isEqualTo(ListingEvents.TOPIC);
        assertThat(header(record, "kafka_dlt-exception-message"))
                .contains("Could not send the listing-expiring email to user " + owner)
                .doesNotContain("amira@example.tn");
        // 1 attempt + 3 retries, all rolled back: the event can be replayed from the DLQ
        assertThat(processed(eventId)).isZero();
    }

    @Test
    void aMalformedMessageGoesToTheDlqWithoutRetry() throws Exception {
        UUID eventId = UUID.randomUUID();
        send(envelope(eventId, ListingEvents.EXPIRING_SOON, owner, "{ \"title\": \"" + TITLE + "\" }"));

        List<ConsumerRecord<String, String>> dead = deadLetters(eventId);
        assertThat(dead).hasSize(1);
        assertThat(header(dead.getFirst(), "kafka_dlt-exception-message"))
                .contains("Missing data.expiresAt in event " + eventId);
        assertThat(processed(eventId)).isZero();
        assertThat(SMTP.received()).isEmpty();
    }

    private void send(String value) throws Exception {
        kafka.send(ListingEvents.TOPIC, listing.toString(), value).get();
    }

    private String expiringSoon(UUID eventId, UUID ownerId) {
        return envelope(eventId, ListingEvents.EXPIRING_SOON, ownerId,
                "{ \"title\": \"" + TITLE + "\", \"expiresAt\": \"" + EXPIRES_AT + "\" }");
    }

    private String envelope(UUID eventId, String type, UUID ownerId, String data) {
        return """
                { "eventId": "%s", "type": "%s", "schemaVersion": 1, "occurredAt": "%s",
                  "listingId": "%s", "ownerId": "%s", "data": %s }""".formatted(eventId, type, Instant.now(),
                listing, ownerId, data);
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
        return TestRedpanda.read(ListingEvents.DLQ_TOPIC,
                record -> record.value() != null && record.value().contains(eventId.toString()),
                records -> !records.isEmpty(), Duration.ofSeconds(40));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
