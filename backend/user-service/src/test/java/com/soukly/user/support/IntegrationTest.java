package com.soukly.user.support;

import java.sql.Timestamp;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base of the integration tests: one Spring context for every class, a PostgreSQL container (Testcontainers), an
 * in-memory SMTP server (GreenMail), JWT keys generated for the run (active {@code k2}, previous {@code k1}) and a
 * clock that tests can move. Each test starts with empty tables, an empty mailbox and the clock at the real time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgresContainerConfig.class, IntegrationTest.ClockConfig.class })
public abstract class IntegrationTest {

    protected static final TestSmtpServer SMTP = new TestSmtpServer();
    protected static final String FRONT_ORIGIN = "http://localhost:4200";

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
    }

    @BeforeEach
    void resetState() throws Exception {
        SMTP.ensureRunning();
        SMTP.purge();
        clock.reset();
        jdbc.execute("TRUNCATE users, login_throttle CASCADE");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }
}
