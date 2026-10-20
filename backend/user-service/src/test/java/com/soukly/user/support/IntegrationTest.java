package com.soukly.user.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base of the integration tests: one Spring context for every class, a PostgreSQL container (Testcontainers) and
 * an in-memory SMTP server (GreenMail). Each test starts with empty tables and an empty mailbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
public abstract class IntegrationTest {

    protected static final TestSmtpServer SMTP = new TestSmtpServer();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> TestSmtpServer.HOST);
        registry.add("spring.mail.port", SMTP::port);
    }

    @BeforeEach
    void resetState() throws Exception {
        SMTP.ensureRunning();
        SMTP.purge();
        jdbc.execute("TRUNCATE users CASCADE");
    }
}
