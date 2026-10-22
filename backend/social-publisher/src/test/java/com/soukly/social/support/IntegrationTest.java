package com.soukly.social.support;

import java.util.Base64;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.soukly.common.event.UserEvents;

/**
 * Base of the integration tests: one Spring context for every class, a PostgreSQL container (Testcontainers) migrated
 * by Flyway and a Redpanda container for the user events ({@link TestRedpanda}). Access tokens are validated against
 * the simulated JWKS of {@link TestJwks}, and the Graph API of Meta is the fake of {@link TestGraph}: tests never call
 * the real one. Tests use a new account id each time instead of resetting the database.
 *
 * <p>The housekeeping task never runs on its own; the clock moves only when a test advances it ({@link MutableClock}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgresContainerConfig.class, IntegrationTest.ClockConfig.class, IntegrationTest.TopicsConfig.class })
public abstract class IntegrationTest {

    /** A test key: 32 bytes in base64, never used outside the tests. */
    public static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
        31, 32 });

    public static final String FRONT_BASE_URL = "https://soukly.example.test";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("soukly.social.jwt.jwk-set-uri", TestJwks::uri);
        registry.add("spring.kafka.bootstrap-servers", TestRedpanda::bootstrapServers);
        registry.add("soukly.social.token-encryption.key", () -> ENCRYPTION_KEY);
        registry.add("soukly.social.facebook.app-id", () -> TestGraph.APP_ID);
        registry.add("soukly.social.facebook.app-secret", () -> TestGraph.APP_SECRET);
        registry.add("soukly.social.facebook.graph-version", () -> TestGraph.VERSION);
        registry.add("soukly.social.facebook.graph-base-url", TestGraph::baseUrl);
        registry.add("soukly.social.facebook.dialog-base-url", () -> "https://www.facebook.example.test");
        registry.add("soukly.social.facebook.front-base-url", () -> FRONT_BASE_URL);
        // Run by the tests only
        registry.add("soukly.social.facebook.purge-initial-delay", () -> "P1D");
        registry.add("soukly.social.user-events.retry-interval", () -> "100ms");
    }

    /** The topics of user-service, which creates them outside the tests. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TopicsConfig {

        @Bean
        NewTopic userEventsTopic() {
            return TopicBuilder.name(UserEvents.TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic userEventsDlqTopic() {
            return TopicBuilder.name(UserEvents.DLQ_TOPIC).partitions(1).replicas(1).build();
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
