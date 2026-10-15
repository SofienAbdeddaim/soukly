package com.soukly.gateway.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.nimbusds.jose.jwk.RSAKey;

/**
 * Base of the gateway integration tests: a real gateway on a random port, in front of fake user-service (which
 * also serves the JWKS) and listing-service. social-publisher points to a closed port, as if it were stopped.
 * All subclasses share one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public abstract class GatewayIntegrationTest {

    protected static final TestJwts JWTS = new TestJwts();
    protected static final RSAKey SIGNING_KEY = TestJwts.newKey("key-1");
    protected static final FakeService USER_SERVICE = FakeService.startWithJwks("user-service", JWTS::jwks);
    protected static final FakeService LISTING_SERVICE = FakeService.start("listing-service");
    private static final int STOPPED_SERVICE_PORT = freePort();

    static {
        JWTS.publish(SIGNING_KEY);
    }

    @Autowired
    protected WebTestClient client;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", USER_SERVICE::url);
        registry.add("LISTING_SERVICE_URL", LISTING_SERVICE::url);
        registry.add("SOCIAL_SERVICE_URL", () -> "http://127.0.0.1:" + STOPPED_SERVICE_PORT);
    }

    @BeforeEach
    void resetServices() {
        USER_SERVICE.reset();
        LISTING_SERVICE.reset();
        client = client.mutate().responseTimeout(Duration.ofSeconds(15)).build();
    }

    protected static String validToken() {
        return TestJwts.rs256(SIGNING_KEY, TestJwts.validClaims().build());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
