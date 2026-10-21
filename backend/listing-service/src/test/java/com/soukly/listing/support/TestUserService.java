package com.soukly.listing.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulated internal endpoints of user-service (CATA-03) on a local HTTP server, like {@link TestJwks}: every account
 * is active with the same name and number unless a test closes it or takes user-service down. Requests without the
 * right {@code X-Internal-Token} are refused, as by user-service.
 */
public final class TestUserService {

    public static final String TOKEN = "test-internal-" + UUID.randomUUID();
    public static final String DISPLAY_NAME = "Karim Trabelsi";
    public static final String MEMBER_SINCE = "2026-03";
    public static final String PHONE = "+21620123456";

    private static final Pattern PATH = Pattern.compile("^/internal/users/([0-9a-f-]{36})/(public-profile|contact)$");
    private static final Set<UUID> CLOSED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FAILING = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();
    private static final HttpServer SERVER = start();

    private TestUserService() {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** The account of {@code userId} is no longer active: {@code 404}. */
    public static void close(UUID userId) {
        CLOSED.add(userId);
    }

    /** user-service answers {@code 500} for {@code userId}. */
    public static void fail(UUID userId) {
        FAILING.add(userId);
    }

    /** Requests received for an endpoint ({@code public-profile} or {@code contact}) of {@code userId}. */
    public static int calls(UUID userId, String endpoint) {
        AtomicInteger count = CALLS.get(userId + "/" + endpoint);
        return count == null ? 0 : count.get();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/internal/users/", TestUserService::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Matcher matcher = PATH.matcher(exchange.getRequestURI().getPath());
            if (!TOKEN.equals(exchange.getRequestHeaders().getFirst("X-Internal-Token"))) {
                send(exchange, 401, "{}");
                return;
            }
            if (!matcher.matches() || !exchange.getRequestMethod().equals("GET")) {
                send(exchange, 404, "{}");
                return;
            }
            UUID userId = UUID.fromString(matcher.group(1));
            String endpoint = matcher.group(2);
            CALLS.computeIfAbsent(userId + "/" + endpoint, key -> new AtomicInteger()).incrementAndGet();
            if (FAILING.contains(userId)) {
                send(exchange, 500, "{}");
            } else if (CLOSED.contains(userId)) {
                send(exchange, 404, "{}");
            } else if (endpoint.equals("contact")) {
                send(exchange, 200, "{\"phone\":\"" + PHONE + "\"}");
            } else {
                send(exchange, 200, "{\"displayName\":\"" + DISPLAY_NAME + "\",\"memberSince\":\"" + MEMBER_SINCE
                        + "\"}");
            }
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
