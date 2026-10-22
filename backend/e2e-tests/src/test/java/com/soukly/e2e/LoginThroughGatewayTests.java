package com.soukly.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.soukly.e2e.support.ServiceProcesses;
import com.sun.net.httpserver.HttpServer;

/**
 * Nominal scenario of AUTH-02 from end to end: the packaged gateway and user-service run as two processes, in front
 * of PostgreSQL. The browser signs in through the gateway, calls a protected route with the access token, renews
 * its session and signs out; a forged token is refused by the gateway.
 *
 * <p>listing-service is played by a local HTTP server that records the {@code Authorization} header it receives.
 * The RSA key of user-service is generated when the test runs, in a temporary directory.
 */
class LoginThroughGatewayTests {

    private static final String ORIGIN = "http://localhost:4200";
    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFRESH_COOKIE = Pattern.compile("^soukly_rt=([^;]*)");

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static final List<String> LISTING_AUTHORIZATIONS = new CopyOnWriteArrayList<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static ServiceProcesses services;
    private static HttpServer listingService;
    private static String gateway;

    @BeforeAll
    static void startTheStack() throws Exception {
        POSTGRES.start();
        listingService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        listingService.createContext("/", exchange -> {
            LISTING_AUTHORIZATIONS.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"service\":\"listing-service\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        listingService.start();

        services = new ServiceProcesses("login");
        int userServicePort = ServiceProcesses.freePort();
        int gatewayPort = ServiceProcesses.freePort();
        String userService = "http://127.0.0.1:" + userServicePort;
        gateway = "http://127.0.0.1:" + gatewayPort;

        services.start("user-service", Map.ofEntries(
                Map.entry("USER_SERVICE_PORT", Integer.toString(userServicePort)),
                Map.entry("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl()),
                Map.entry("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername()),
                Map.entry("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword()),
                // Nothing is emailed in this scenario; an unreachable SMTP server never fails a request.
                Map.entry("MAIL_HOST", "127.0.0.1"),
                Map.entry("MAIL_PORT", Integer.toString(ServiceProcesses.freePort())),
                Map.entry("JWT_KEY_ID", "e2e-key"),
                Map.entry("JWT_PRIVATE_KEY_PATH", ServiceProcesses.privateKey().toString()),
                Map.entry("ALLOWED_ORIGINS", ORIGIN)));
        services.start("gateway", Map.of(
                "GATEWAY_PORT", Integer.toString(gatewayPort),
                "USER_SERVICE_URL", userService,
                "LISTING_SERVICE_URL", "http://127.0.0.1:" + listingService.getAddress().getPort(),
                "SOCIAL_SERVICE_URL", "http://127.0.0.1:" + ServiceProcesses.freePort(),
                "CORS_ALLOWED_ORIGINS", ORIGIN));
        services.awaitHealthy(userService, "user-service");
        services.awaitHealthy(gateway, "gateway");

        createActiveAccount();
    }

    @AfterAll
    static void stopTheStack() {
        if (services != null) {
            services.stopAll();
        }
        if (listingService != null) {
            listingService.stop(0);
        }
        POSTGRES.stop();
    }

    @Test
    void signInCallAProtectedRouteRefreshAndSignOutThroughTheGateway() throws Exception {
        HttpResponse<String> login = send(post("/api/users/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"expiresIn\":900", "\"tokenType\":\"Bearer\"");
        String setCookie = refreshSetCookie(login);
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/users");
        String accessToken = group(ACCESS_TOKEN, login.body());
        String refreshToken = group(REFRESH_COOKIE, setCookie);

        // The access token is accepted by the gateway on a protected route and forwarded unchanged.
        HttpResponse<String> listing = send(post("/api/listings")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(listing.statusCode()).isEqualTo(200);
        assertThat(LISTING_AUTHORIZATIONS).containsExactly("Bearer " + accessToken);

        // A forged token (same header and claims, signature altered) never reaches the service.
        HttpResponse<String> forged = send(post("/api/listings")
                .header("Authorization", "Bearer " + tamper(accessToken))
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(forged.statusCode()).isEqualTo(401);
        assertThat(LISTING_AUTHORIZATIONS).hasSize(1);

        HttpResponse<String> refresh = send(post("/api/users/refresh")
                .header("Origin", ORIGIN)
                .header("Cookie", "soukly_rt=" + refreshToken)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(refresh.statusCode()).isEqualTo(200);
        String renewed = group(REFRESH_COOKIE, refreshSetCookie(refresh));
        assertThat(renewed).isNotEqualTo(refreshToken);

        HttpResponse<String> logout = send(post("/api/users/logout")
                .header("Origin", ORIGIN)
                .header("Cookie", "soukly_rt=" + renewed)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(refreshSetCookie(logout)).contains("Max-Age=0");

        HttpResponse<String> afterLogout = send(post("/api/users/refresh")
                .header("Origin", ORIGIN)
                .header("Cookie", "soukly_rt=" + renewed)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(afterLogout.statusCode()).isEqualTo(401);
    }

    /** AUTH-04: both password paths are public on the gateway; the full flow is covered by user-service tests. */
    @Test
    void passwordResetPathsArePublicThroughTheGateway() throws Exception {
        HttpResponse<String> forgot = send(post("/api/users/password/forgot")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + EMAIL + "\"}")));
        assertThat(forgot.statusCode()).isEqualTo(202);

        HttpResponse<String> reset = send(post("/api/users/password/reset")
                .header("Content-Type", "application/json")
                .header("Origin", ORIGIN)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"token\":\"unknown\",\"newPassword\":\"Sidi-Bou-Said-bleu-42\"}")));
        assertThat(reset.statusCode()).isEqualTo(400);
        assertThat(reset.body()).contains("urn:soukly:problem:reset-token-invalid");
    }

    @Test
    void theJwksIsNotExposedByTheGateway() throws Exception {
        for (String path : List.of("/.well-known/jwks.json", "/api/.well-known/jwks.json")) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(gateway + path)).GET());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).doesNotContain("\"kty\"");
        }
    }

    // --- Stack ---------------------------------------------------------------------------------------------

    /** Inserted directly: registration and verification are covered by the user-service tests (AUTH-01). */
    private static void createActiveAccount() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
                PreparedStatement insert = connection.prepareStatement("""
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

    // --- HTTP ----------------------------------------------------------------------------------------------

    private static HttpRequest.Builder post(String path) {
        return HttpRequest.newBuilder(URI.create(gateway + path)).timeout(Duration.ofSeconds(15));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String refreshSetCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith("soukly_rt="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No soukly_rt cookie in " + response.headers().map()));
    }

    private static String group(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("No match for " + pattern);
        }
        return matcher.group(1);
    }

    /** Changes the first character of the signature: same header and claims, invalid signature. */
    private static String tamper(String jwt) {
        int signature = jwt.lastIndexOf('.') + 1;
        char replaced = jwt.charAt(signature) == 'A' ? 'B' : 'A';
        return jwt.substring(0, signature) + replaced + jwt.substring(signature + 1);
    }
}
