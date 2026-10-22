package com.soukly.listing.support;

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
 * Base of the integration tests: one Spring context for every class, a PostgreSQL container (Testcontainers)
 * migrated by Flyway, a Garage container for the photos ({@link TestGarage}) and a Redpanda container for the events
 * ({@link TestRedpanda}). The referential data is read-only, so no state is reset between tests; listing tests use a
 * new account id each time instead. Access tokens are validated against the simulated JWKS of {@link TestJwks}, and
 * sellers are read from the simulated user-service of {@link TestUserService}.
 *
 * <p>The scheduled tasks (outbox relay, expiry, photo cleanup) never run on their own: tests call them, with a clock
 * they can move ({@link MutableClock}, back to the real time before each test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgresContainerConfig.class, IntegrationTest.ClockConfig.class, IntegrationTest.TopicsConfig.class })
public abstract class IntegrationTest {

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
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("soukly.listing.jwt.jwk-set-uri", TestJwks::uri);
        registry.add("soukly.listing.storage.endpoint", TestGarage::endpoint);
        registry.add("soukly.listing.storage.public-endpoint", TestGarage::endpoint);
        registry.add("soukly.listing.storage.region", () -> TestGarage.REGION);
        registry.add("soukly.listing.storage.bucket", () -> TestGarage.BUCKET);
        registry.add("soukly.listing.storage.access-key", () -> TestGarage.ACCESS_KEY);
        registry.add("soukly.listing.storage.secret-key", () -> TestGarage.SECRET_KEY);
        registry.add("soukly.listing.photos.public-base-url", () -> "https://photos.example.test");
        registry.add("spring.kafka.bootstrap-servers", TestRedpanda::bootstrapServers);
        registry.add("soukly.listing.publication.public-base-url", () -> "https://soukly.example.test");
        registry.add("soukly.listing.user-service.base-url", TestUserService::baseUrl);
        registry.add("soukly.listing.user-service.internal-api-token", () -> TestUserService.TOKEN);
        // Run by the tests only
        registry.add("soukly.listing.outbox.relay-initial-delay", () -> "P1D");
        registry.add("soukly.listing.outbox.purge-interval", () -> "P1D");
        registry.add("soukly.listing.publication.check-initial-delay", () -> "P1D");
        registry.add("soukly.listing.photos.cleanup-interval", () -> "P1D");
        registry.add("soukly.listing.user-events.retry-interval", () -> "100ms");
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
