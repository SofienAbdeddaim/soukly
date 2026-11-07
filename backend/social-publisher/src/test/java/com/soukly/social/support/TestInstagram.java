package com.soukly.social.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulated Instagram API with Facebook Login for Business (Meta Unified login) on a local HTTP server, like
 * {@link TestGraph}: the code exchange for a system-user token ({@code /oauth/access_token}) and the accounts it was
 * granted ({@code /me/assigned_instagram_accounts}). A test registers the accounts behind an OAuth code
 * ({@link #accounts}); the token this fake hands out contains the code, so a test can look for it in the database and
 * the logs.
 *
 * <p>The content publishing (SOC-05) is simulated too: containers ({@code /{account}/media}), their status, the
 * publication ({@code /{account}/media_publish}), the permalink and the publishing limit of an account. The calls are
 * recorded per account ({@link #calls}); a test can keep containers in progress ({@link #processing}), use the limit
 * up ({@link #quotaUsage}) or make the next calls fail ({@link #failNext}).
 */
public final class TestInstagram {

    public static final String VERSION = "v26.0";
    public static final String APP_ID = "9876543210";
    public static final String APP_SECRET = "test-instagram-secret-" + UUID.randomUUID();
    /** Facebook Login for Business configuration of the Instagram connection. */
    public static final String LOGIN_CONFIG_ID = "1100000000000002";

    private static final Map<String, Grant> GRANTS = new ConcurrentHashMap<>();
    private static final Set<String> REVOKED = ConcurrentHashMap.newKeySet();
    /** {@code config.quota_total} of {@code content_publishing_limit}. */
    public static final int QUOTA_TOTAL = 100;

    private static final Map<String, List<MediaCall>> MEDIA_CALLS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PROCESSING = new ConcurrentHashMap<>();
    private static final Map<String, Integer> QUOTA_USAGE = new ConcurrentHashMap<>();
    private static final Map<String, List<Failure>> FAILURES = new ConcurrentHashMap<>();
    private static final Map<String, String> CONTAINER_ACCOUNTS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> STATUS_READS = new ConcurrentHashMap<>();
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final HttpServer SERVER = start();

    private TestInstagram() {
    }

    /**
     * The Instagram accounts the person behind a code granted in the dialog, by username: the first one is the
     * account of the token for the publishing calls.
     */
    public record Grant(List<String> usernames, boolean failExchange) {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** Registers the accounts granted in the dialog and returns a new OAuth code for them (none: an empty list). */
    public static String accounts(String... usernames) {
        String code = "code-" + UUID.randomUUID();
        GRANTS.put(code, new Grant(List.of(usernames), false));
        return code;
    }

    /** A professional account granted alone, the usual case. */
    public static String business(String username) {
        return accounts(username);
    }

    /** A code Meta refuses. */
    public static String refusedCode() {
        String code = "code-" + UUID.randomUUID();
        GRANTS.put(code, new Grant(List.of("refused"), true));
        return code;
    }

    /** The access was removed: every token of this code is refused from now on. */
    public static void revoke(String code) {
        REVOKED.add(code);
    }

    /** The system-user token handed out for {@code code}. */
    public static String token(String code) {
        return "IGsuat" + code;
    }

    /** Instagram account id of the first account behind {@code code}. */
    public static String accountId(String code) {
        return accountId(code, 0);
    }

    /** Instagram account id of the account at {@code index} behind {@code code}. */
    public static String accountId(String code, int index) {
        return "1784" + Math.abs(code.hashCode()) + (index == 0 ? "" : "-" + index);
    }

    /**
     * A publishing call of an account.
     *
     * @param kind   {@code image}, {@code item}, {@code carousel}, {@code status}, {@code publish}, {@code limit}
     * @param id     the container or media id answered, or read
     * @param params form or query parameters, the token included
     */
    public record MediaCall(String kind, String id, Map<String, String> params) {
    }

    /** An error answered instead of the next publishing call of an account ({@code kind} {@code null}: any). */
    public record Failure(String kind, int status, int code, Integer subcode) {
    }

    public static List<MediaCall> calls(String accountId) {
        return List.copyOf(MEDIA_CALLS.getOrDefault(accountId, List.of()));
    }

    public static List<MediaCall> calls(String accountId, String kind) {
        return calls(accountId).stream().filter(call -> call.kind().equals(kind)).toList();
    }

    /** The containers of the account answer {@code IN_PROGRESS} to their first {@code readings} status readings. */
    public static void processing(String accountId, int readings) {
        PROCESSING.put(accountId, readings);
    }

    /** Posts the account published through the API within the last 24 hours. */
    public static void quotaUsage(String accountId, int usage) {
        QUOTA_USAGE.put(accountId, usage);
    }

    public static void failNext(String accountId, Failure... failures) {
        FAILURES.computeIfAbsent(accountId, key -> new CopyOnWriteArrayList<>()).addAll(List.of(failures));
    }

    public static String permalinkOf(String mediaId) {
        return "https://www.instagram.com/p/" + mediaId + "/";
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", TestInstagram::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            if (method.equals("GET") && path.equals("/" + VERSION + "/oauth/access_token")) {
                exchangeCode(exchange, query);
            } else if (method.equals("GET") && path.equals("/" + VERSION + "/me/assigned_instagram_accounts")) {
                assignedAccounts(exchange, query);
            } else if (path.startsWith("/" + VERSION + "/")) {
                publishing(exchange, method, path.substring(VERSION.length() + 2).split("/"),
                        method.equals("POST") ? query(read(exchange.getRequestBody())) : query);
            } else {
                error(exchange, 404);
            }
        }
    }

    private static void exchangeCode(HttpExchange exchange, Map<String, String> query) throws IOException {
        String code = query.get("code");
        Grant grant = code == null ? null : GRANTS.get(code);
        if (!APP_ID.equals(query.get("client_id")) || !APP_SECRET.equals(query.get("client_secret"))
                || query.get("redirect_uri") == null || grant == null || grant.failExchange()) {
            error(exchange, 400);
            return;
        }
        // A system-user token: no expires_in
        send(exchange, 200, "{\"access_token\":\"" + token(code) + "\",\"token_type\":\"bearer\"}");
    }

    private static void assignedAccounts(HttpExchange exchange, Map<String, String> query) throws IOException {
        String code = codeOf(query.get("access_token"), "IGsuat");
        if (code == null || REVOKED.contains(code)) {
            error(exchange, 401);
            return;
        }
        if (!"id,username,profile_picture_url".equals(query.get("fields"))) {
            error(exchange, 400);
            return;
        }
        List<String> usernames = GRANTS.get(code).usernames();
        List<String> accounts = new ArrayList<>();
        for (int i = 0; i < usernames.size(); i++) {
            accounts.add("{\"id\":\"" + accountId(code, i) + "\",\"username\":\"" + usernames.get(i)
                    + "\",\"profile_picture_url\":\"https://scontent.example.test/ig/" + usernames.get(i)
                    + ".jpg\"}");
        }
        send(exchange, 200, "{\"data\":[" + String.join(",", accounts) + "]}");
    }

    private static void publishing(HttpExchange exchange, String method, String[] path, Map<String, String> params)
            throws IOException {
        String code = codeOf(params.get("access_token"), "IGsuat");
        if (code == null || REVOKED.contains(code)) {
            error(exchange, 401);
            return;
        }
        String account = accountId(code);
        String kind = kind(method, path, params);
        if (kind == null) {
            error(exchange, 404);
            return;
        }
        if (path.length == 1 && !account.equals(CONTAINER_ACCOUNTS.get(path[0]))) {
            error(exchange, 404);
            return;
        }
        if (path.length == 2 && !account.equals(path[0])) {
            // Only the account of the token publishes on itself
            failure(exchange, new Failure(kind, 400, 100, 33));
            return;
        }
        List<Failure> failures = FAILURES.getOrDefault(account, List.of());
        Failure failure = failures.stream().filter(f -> f.kind() == null || f.kind().equals(kind)).findFirst()
                .orElse(null);
        if (failure != null) {
            failures.remove(failure);
            record(account, kind, null, params);
            failure(exchange, failure);
            return;
        }
        switch (kind) {
            case "image", "item", "carousel" -> {
                String id = "container-" + IDS.incrementAndGet();
                CONTAINER_ACCOUNTS.put(id, account);
                record(account, kind, id, params);
                send(exchange, 200, "{\"id\":\"" + id + "\"}");
            }
            case "status" -> {
                int reads = STATUS_READS.merge(path[0], 1, Integer::sum);
                record(account, kind, path[0], params);
                String status = reads <= PROCESSING.getOrDefault(account, 0) ? "IN_PROGRESS" : "FINISHED";
                send(exchange, 200, "{\"status_code\":\"" + status + "\",\"id\":\"" + path[0] + "\"}");
            }
            case "publish" -> {
                int usage = QUOTA_USAGE.getOrDefault(account, 0);
                if (usage >= QUOTA_TOTAL) {
                    record(account, kind, null, params);
                    failure(exchange, new Failure(kind, 400, 9, 2207042));
                    return;
                }
                QUOTA_USAGE.put(account, usage + 1);
                String id = "1790" + IDS.incrementAndGet();
                CONTAINER_ACCOUNTS.put(id, account);
                record(account, kind, id, params);
                send(exchange, 200, "{\"id\":\"" + id + "\"}");
            }
            case "permalink" -> {
                record(account, kind, path[0], params);
                send(exchange, 200, "{\"permalink\":\"" + permalinkOf(path[0]) + "\",\"id\":\"" + path[0]
                        + "\"}");
            }
            default -> {
                record(account, kind, null, params);
                send(exchange, 200, "{\"data\":[{\"quota_usage\":" + QUOTA_USAGE.getOrDefault(account, 0)
                        + ",\"config\":{\"quota_total\":" + QUOTA_TOTAL + ",\"quota_duration\":86400}}]}");
            }
        }
    }

    private static String kind(String method, String[] path, Map<String, String> params) {
        if (method.equals("POST") && path.length == 2 && path[1].equals("media")) {
            if ("CAROUSEL".equals(params.get("media_type"))) {
                return params.get("children") == null ? null : "carousel";
            }
            if (params.get("image_url") == null) {
                return null;
            }
            return "true".equals(params.get("is_carousel_item")) ? "item" : "image";
        }
        if (method.equals("POST") && path.length == 2 && path[1].equals("media_publish")) {
            return params.get("creation_id") == null ? null : "publish";
        }
        if (method.equals("GET") && path.length == 2 && path[1].equals("content_publishing_limit")) {
            return "limit";
        }
        if (method.equals("GET") && path.length == 1 && "status_code".equals(params.get("fields"))) {
            return "status";
        }
        if (method.equals("GET") && path.length == 1 && "permalink".equals(params.get("fields"))) {
            return "permalink";
        }
        return null;
    }

    private static void record(String account, String kind, String id, Map<String, String> params) {
        MEDIA_CALLS.computeIfAbsent(account, key -> new CopyOnWriteArrayList<>())
                .add(new MediaCall(kind, id, Map.copyOf(params)));
    }

    private static void failure(HttpExchange exchange, Failure failure) throws IOException {
        send(exchange, failure.status(), "{\"error\":{\"message\":\"Simulated error\",\"type\":\"OAuthException\","
                + "\"code\":" + failure.code() + (failure.subcode() == null ? "" : ",\"error_subcode\":"
                + failure.subcode()) + ",\"fbtrace_id\":\"trace\"}}");
    }

    /** The code a token of this fake was made from, when it starts with {@code prefix} and the code is known. */
    private static String codeOf(String token, String prefix) {
        if (token == null || !token.startsWith(prefix) || !token.contains("code-")) {
            return null;
        }
        String code = token.substring(token.indexOf("code-"));
        return GRANTS.containsKey(code) ? code : null;
    }

    private static String read(InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
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
