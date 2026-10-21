package com.soukly.listing.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base of the integration tests: one Spring context for every class, a PostgreSQL container (Testcontainers)
 * migrated by Flyway and a Garage container for the photos ({@link TestGarage}). The referential data is read-only,
 * so no state is reset between tests; listing tests use a new account id each time instead. Access tokens are
 * validated against the simulated JWKS of {@link TestJwks}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

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
    }
}
