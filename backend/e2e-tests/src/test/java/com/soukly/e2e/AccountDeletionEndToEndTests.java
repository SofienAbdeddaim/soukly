package com.soukly.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;

import com.soukly.e2e.support.ServiceProcesses;

/**
 * Deletion of an account from end to end (AUTH-05): the packaged gateway, user-service and listing-service run as
 * processes, in front of PostgreSQL (one database per service) and Redpanda. The member publishes listings through the
 * gateway, deletes their account, and {@code user.deleted} travels from the outbox of user-service to the consumer of
 * listing-service, which deletes the listings.
 *
 * <p>No object storage runs: the photos of the test are rows only, and nothing reads or deletes their files.
 */
class AccountDeletionEndToEndTests {

    private static final String ORIGIN = "http://localhost:4200";
    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final String LISTING = """
            { "categoryCode": "home.furniture", "title": "Canapé d'angle en cuir",
              "description": "Canapé d'angle en cuir marron, très bon état, à récupérer sur place.",
              "priceMode": "FIXED", "price": 1200, "negotiable": true, "governorateCode": "sfax" }""";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static final RedpandaContainer REDPANDA = new RedpandaContainer("redpandadata/redpanda:v26.2.4");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static ServiceProcesses services;
    private static String gateway;
    private static String listingDatabase;

    @BeforeAll
    static void startTheStack() throws Exception {
        POSTGRES.start();
        REDPANDA.start();
        // As infra/compose/redpanda/init-topics.sh does before the services start: a consumer that starts first would
        // otherwise see a topic without all its partitions until its next metadata refresh (5 min)
        for (String topic : List.of("soukly.user-events 3", "soukly.user-events.dlq 1", "soukly.listing-events 3",
                "soukly.listing-events.dlq 1")) {
            String[] spec = topic.split(" ");
            var created = REDPANDA.execInContainer("rpk", "topic", "create", spec[0], "--partitions", spec[1]);
            assertThat(created.getExitCode()).as(created.getStderr()).isZero();
        }
        try (Connection connection = userDatabase(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE soukly_listing");
        }
        listingDatabase = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/soukly_listing");

        services = new ServiceProcesses("deletion");
        int userServicePort = ServiceProcesses.freePort();
        int listingServicePort = ServiceProcesses.freePort();
        int gatewayPort = ServiceProcesses.freePort();
        String userService = "http://127.0.0.1:" + userServicePort;
        String listingService = "http://127.0.0.1:" + listingServicePort;
        gateway = "http://127.0.0.1:" + gatewayPort;
        String internalToken = "e2e-" + UUID.randomUUID();

        services.start("user-service", Map.ofEntries(
                Map.entry("USER_SERVICE_PORT", Integer.toString(userServicePort)),
                Map.entry("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl()),
                Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                Map.entry("KAFKA_BOOTSTRAP_SERVERS", REDPANDA.getBootstrapServers()),
                // The goodbye email is not checked here; an unreachable SMTP server never fails a request.
                Map.entry("MAIL_HOST", "127.0.0.1"),
                Map.entry("MAIL_PORT", Integer.toString(ServiceProcesses.freePort())),
                Map.entry("JWT_KEY_ID", "e2e-key"),
                Map.entry("JWT_PRIVATE_KEY_PATH", ServiceProcesses.privateKey().toString()),
                Map.entry("INTERNAL_API_TOKEN", internalToken),
                Map.entry("ALLOWED_ORIGINS", ORIGIN)));
        services.start("listing-service", Map.ofEntries(
                Map.entry("LISTING_SERVICE_PORT", Integer.toString(listingServicePort)),
                Map.entry("SPRING_DATASOURCE_URL", listingDatabase),
                Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                Map.entry("KAFKA_BOOTSTRAP_SERVERS", REDPANDA.getBootstrapServers()),
                Map.entry("USER_SERVICE_JWKS_URI", userService + "/.well-known/jwks.json"),
                Map.entry("USER_SERVICE_URL", userService),
                Map.entry("INTERNAL_API_TOKEN", internalToken),
                // Never called: no photo file is read or deleted in this scenario
                Map.entry("S3_ENDPOINT", "http://127.0.0.1:" + ServiceProcesses.freePort()),
                Map.entry("S3_ACCESS_KEY", "e2e"),
                Map.entry("S3_SECRET_KEY", "e2e")));
        services.start("gateway", Map.of(
                "GATEWAY_PORT", Integer.toString(gatewayPort),
                "USER_SERVICE_URL", userService,
                "LISTING_SERVICE_URL", listingService,
                "SOCIAL_SERVICE_URL", "http://127.0.0.1:" + ServiceProcesses.freePort(),
                "CORS_ALLOWED_ORIGINS", ORIGIN));
        services.awaitHealthy(userService, "user-service");
        services.awaitHealthy(listingService, "listing-service");
        services.awaitHealthy(gateway, "gateway");

        createActiveAccount();
    }

    @AfterAll
    static void stopTheStack() {
        if (services != null) {
            services.stopAll();
        }
        REDPANDA.stop();
        POSTGRES.stop();
    }

    @Test
    void deletingTheAccountDeletesItsListingsThroughTheUserEvents() throws Exception {
        String accessToken = group(ACCESS_TOKEN, send(request("/api/users/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")), 200));
        List<String> online = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String id = draft(accessToken);
            readyPhoto(id);
            send(request("/api/listings/" + id + "/publish").header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.noBody()), 200);
            online.add(id);
        }
        String draft = draft(accessToken);

        send(request("/api/users/me").header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json").header("Origin", ORIGIN)
                .method("DELETE", HttpRequest.BodyPublishers.ofString("{\"currentPassword\":\"" + PASSWORD + "\"}")),
                204);

        // The relay sends user.deleted within seconds, then listing-service deletes the listings
        Instant deadline = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(deadline) && !"DELETED".equals(status(online.getLast()))) {
            Thread.sleep(500);
        }
        for (String id : online) {
            assertThat(status(id)).isEqualTo("DELETED");
            send(request("/api/listings/" + id).GET(), 410);
        }
        assertThat(status(draft)).isNull();
        assertThat(listingEvents("listing.deleted")).isEqualTo(3);

        send(request("/api/users/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")), 401);
        send(request("/api/users/register").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email": "amira@example.tn", "password": "Nouveau-depart-2027", "displayName": "Amira",
                         "phone": "20 123 456", "acceptTerms": true}""")), 202);
    }

    private static String draft(String accessToken) throws Exception {
        return group(ID, send(request("/api/listings").header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(LISTING)), 201));
    }

    /** A READY photo row: publishing needs one, and its files are never read here. */
    private static void readyPhoto(String listingId) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection connection = listingDatabase(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO listing_photos (id, listing_id, status, position, width, height, bytes, created_at,
                    updated_at)
                VALUES (?, ?, 'READY', 0, 1600, 1200, 500000, ?, ?)""")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, UUID.fromString(listingId));
            insert.setTimestamp(3, now);
            insert.setTimestamp(4, now);
            insert.executeUpdate();
        }
    }

    private static String status(String listingId) throws SQLException {
        try (Connection connection = listingDatabase();
                PreparedStatement select = connection.prepareStatement("SELECT status FROM listings WHERE id = ?")) {
            select.setObject(1, UUID.fromString(listingId));
            var rows = select.executeQuery();
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static int listingEvents(String type) throws SQLException {
        try (Connection connection = listingDatabase(); PreparedStatement select = connection.prepareStatement(
                "SELECT count(*) FROM outbox_events WHERE type = ?")) {
            select.setString(1, type);
            var rows = select.executeQuery();
            rows.next();
            return rows.getInt(1);
        }
    }

    /** Inserted directly: registration and verification are covered by the user-service tests (AUTH-01). */
    private static void createActiveAccount() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection connection = userDatabase(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO users (id, email, password_hash, display_name, phone_e164, preferred_language,
                    status, terms_accepted_at, email_verified_at, created_at, updated_at)
                VALUES (?, ?, ?, 'Amira', '+21620123456', 'fr', 'ACTIVE', ?, ?, ?, ?)""")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, EMAIL);
            insert.setString(3, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(PASSWORD));
            for (int column = 4; column <= 7; column++) {
                insert.setTimestamp(column, now);
            }
            insert.executeUpdate();
        }
    }

    private static Connection userDatabase() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection listingDatabase() throws SQLException {
        return DriverManager.getConnection(listingDatabase, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(gateway + path)).timeout(Duration.ofSeconds(15));
    }

    /** Sends the request and checks its status; returns the body. */
    private static String send(HttpRequest.Builder request, int expectedStatus) throws Exception {
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response.body();
    }

    private static String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No match for " + pattern);
        }
        return matcher.group(1);
    }
}
