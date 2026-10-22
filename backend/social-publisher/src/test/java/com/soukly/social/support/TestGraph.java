package com.soukly.social.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulated Graph API of Meta on a local HTTP server, like {@link TestJwks}: the real API is never called by the tests.
 * A test registers what the person behind an OAuth code granted and manages ({@link #person}); the tokens this fake
 * hands out derive from the code, so a test can look for them in the database and the logs.
 */
public final class TestGraph {

    public static final String VERSION = "v26.0";
    public static final String APP_ID = "1234567890";
    public static final String APP_SECRET = "test-app-secret-" + UUID.randomUUID();
    public static final Set<String> ALL_PERMISSIONS = Set.of("pages_show_list", "pages_manage_posts",
            "pages_read_engagement");

    private static final Map<String, Person> PEOPLE = new ConcurrentHashMap<>();
    private static final List<String> REVOKED = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVER = start();

    private TestGraph() {
    }

    /** A Page as {@code /me/accounts} returns it. */
    public record Page(String id, String name, String category, List<String> tasks) {

        public Page(String id, String name, String category) {
            this(id, name, category, List.of("ADVERTISE", "ANALYZE", "CREATE_CONTENT", "MESSAGING", "MODERATE",
                    "MANAGE"));
        }
    }

    /** What the person behind {@code code} granted and manages; {@code failExchange}: the code is refused. */
    public record Person(Set<String> permissions, List<Page> pages, boolean failExchange) {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** Registers a person and returns a new OAuth code for them. */
    public static String person(Set<String> permissions, List<Page> pages) {
        String code = "code-" + UUID.randomUUID();
        PEOPLE.put(code, new Person(permissions, pages, false));
        return code;
    }

    /** A code the Graph API refuses. */
    public static String refusedCode() {
        String code = "code-" + UUID.randomUUID();
        PEOPLE.put(code, new Person(Set.of(), List.of(), true));
        return code;
    }

    public static String shortToken(String code) {
        return "EAAshort" + code;
    }

    public static String longToken(String code) {
        return "EAAlong" + code;
    }

    public static String pageToken(String code, String pageId) {
        return "EAApage" + pageId + code;
    }

    /** App-scoped id of the person behind {@code code}. */
    public static String userId(String code) {
        return "fbuser-" + code.substring("code-".length());
    }

    /** App-scoped ids whose permissions were revoked, in order. */
    public static List<String> revoked() {
        return List.copyOf(REVOKED);
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", TestGraph::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            String method = exchange.getRequestMethod();
            String prefix = "/" + VERSION;
            if (!path.startsWith(prefix + "/")) {
                error(exchange, 404);
                return;
            }
            path = path.substring(prefix.length());
            if (method.equals("GET") && path.equals("/oauth/access_token")) {
                accessToken(exchange, query);
            } else if (method.equals("DELETE") && path.matches("^/[^/]+/permissions$")) {
                if (!(APP_ID + "|" + APP_SECRET).equals(query.get("access_token"))) {
                    error(exchange, 400);
                    return;
                }
                REVOKED.add(path.split("/")[1]);
                send(exchange, 200, "{\"success\":true}");
            } else if (method.equals("GET") && path.startsWith("/me")) {
                String code = codeOfLongToken(query.get("access_token"));
                Person person = code == null ? null : PEOPLE.get(code);
                if (person == null) {
                    error(exchange, 401);
                } else if (path.equals("/me")) {
                    send(exchange, 200, "{\"id\":\"" + userId(code) + "\"}");
                } else if (path.equals("/me/permissions")) {
                    send(exchange, 200, permissions(person));
                } else if (path.equals("/me/accounts")) {
                    send(exchange, 200, accounts(code, person, query.get("fields")));
                } else {
                    error(exchange, 404);
                }
            } else {
                error(exchange, 404);
            }
        }
    }

    private static void accessToken(HttpExchange exchange, Map<String, String> query) throws IOException {
        if (!APP_ID.equals(query.get("client_id")) || !APP_SECRET.equals(query.get("client_secret"))) {
            error(exchange, 400);
            return;
        }
        if ("fb_exchange_token".equals(query.get("grant_type"))) {
            String token = query.get("fb_exchange_token");
            String code = token != null && token.startsWith("EAAshort") ? token.substring("EAAshort".length()) : null;
            if (code == null || !PEOPLE.containsKey(code)) {
                error(exchange, 400);
                return;
            }
            send(exchange, 200, "{\"access_token\":\"" + longToken(code) + "\",\"token_type\":\"bearer\","
                    + "\"expires_in\":5183944}");
            return;
        }
        String code = query.get("code");
        Person person = code == null ? null : PEOPLE.get(code);
        if (person == null || person.failExchange() || query.get("redirect_uri") == null) {
            error(exchange, 400);
            return;
        }
        send(exchange, 200, "{\"access_token\":\"" + shortToken(code) + "\",\"token_type\":\"bearer\","
                + "\"expires_in\":5183944}");
    }

    private static String permissions(Person person) {
        String data = ALL_PERMISSIONS.stream()
                .map(permission -> "{\"permission\":\"" + permission + "\",\"status\":\""
                        + (person.permissions().contains(permission) ? "granted" : "declined") + "\"}")
                .collect(Collectors.joining(","));
        return "{\"data\":[{\"permission\":\"public_profile\",\"status\":\"granted\"}," + data + "]}";
    }

    private static String accounts(String code, Person person, String fields) {
        if (fields == null || !fields.contains("access_token")) {
            return "{\"data\":[]}";
        }
        String data = person.pages().stream()
                .map(page -> "{\"id\":\"" + page.id() + "\",\"name\":\"" + page.name() + "\",\"category\":\""
                        + page.category() + "\",\"access_token\":\"" + pageToken(code, page.id()) + "\",\"tasks\":["
                        + page.tasks().stream().map(task -> "\"" + task + "\"").collect(Collectors.joining(","))
                        + "],\"picture\":{\"data\":{\"url\":\"https://scontent.example.test/" + page.id()
                        + ".jpg\"}}}")
                .collect(Collectors.joining(","));
        return "{\"data\":[" + data + "],\"paging\":{\"cursors\":{\"before\":\"a\",\"after\":\"b\"}}}";
    }

    private static String codeOfLongToken(String token) {
        return token != null && token.startsWith("EAAlong") ? token.substring("EAAlong".length()) : null;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            values.put(name, value);
        }
        return values;
    }

    private static void error(HttpExchange exchange, int status) throws IOException {
        send(exchange, status, "{\"error\":{\"message\":\"Simulated error\",\"type\":\"OAuthException\","
                + "\"code\":190}}");
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
