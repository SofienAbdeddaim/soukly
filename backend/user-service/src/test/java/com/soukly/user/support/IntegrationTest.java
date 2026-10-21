package com.soukly.user.support;

import java.sql.Timestamp;
import java.util.UUID;

import org.apache.kafka.clients.admin.NewTopic;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.soukly.common.event.ListingEvents;

/**
 * Base of the integration tests: one Spring context for every class, a PostgreSQL container (Testcontainers), an
 * in-memory SMTP server (GreenMail), JWT keys generated for the run (active {@code k2}, previous {@code k1}), a
 * Redpanda container for the listing events ({@link TestRedpanda}) and a clock that tests can move. Each test starts
 * with empty tables, an empty mailbox and the clock at the real time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgresContainerConfig.class, IntegrationTest.ClockConfig.class, IntegrationTest.TopicsConfig.class })
public abstract class IntegrationTest {

    protected static final TestSmtpServer SMTP = new TestSmtpServer();
    protected static final String FRONT_ORIGIN = "http://localhost:4200";
    /** Shared token of the service-to-service calls (CATA-03), generated for the run. */
    protected static final String INTERNAL_TOKEN = "test-" + UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> TestSmtpServer.HOST);
        registry.add("spring.mail.port", SMTP::port);
        registry.add("soukly.user.jwt.key-id", () -> TestKeys.ACTIVE_KID);
        registry.add("soukly.user.jwt.private-key-path", TestKeys.ACTIVE_PRIVATE_PEM::toString);
        registry.add("soukly.user.jwt.previous-key-id", () -> TestKeys.PREVIOUS_KID);
        registry.add("soukly.user.jwt.previous-public-key-path", TestKeys.PREVIOUS_PUBLIC_PEM::toString);
        registry.add("soukly.user.session.allowed-origins", () -> FRONT_ORIGIN);
        registry.add("spring.kafka.bootstrap-servers", TestRedpanda::bootstrapServers);
        registry.add("soukly.user.listing-events.retry-interval", () -> "100ms");
        registry.add("soukly.user.internal-api.token", () -> INTERNAL_TOKEN);
    }

    @BeforeEach
    void resetState() throws Exception {
        SMTP.ensureRunning();
        SMTP.purge();
        clock.reset();
        jdbc.execute("TRUNCATE users, login_throttle, processed_events CASCADE");
    }

    /** Inserts an account directly, {@code ACTIVE} or {@code PENDING_VERIFICATION}, and returns its id. */
    protected UUID createAccount(String email, String password, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, display_name, phone_e164, preferred_language, status,
                    terms_accepted_at, email_verified_at, created_at, updated_at)
                VALUES (?, ?, ?, 'Amira Ben Salah', '+21620123456', 'ar', ?, ?, ?, ?, ?)""",
                id, email, passwordEncoder.encode(password), status, now,
                "ACTIVE".equals(status) ? now : null, now, now);
        return id;
    }

    /** The topics of listing-service, which creates them outside the tests. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TopicsConfig {

        @Bean
        NewTopic listingEventsTopic() {
            return TopicBuilder.name(ListingEvents.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic listingEventsDlqTopic() {
            return TopicBuilder.name(ListingEvents.DLQ_TOPIC).partitions(1).replicas(1).build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }
}
