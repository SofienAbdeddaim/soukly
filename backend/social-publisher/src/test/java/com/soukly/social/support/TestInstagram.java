package com.soukly.social.support;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulated Instagram API with Instagram Login on a local HTTP server, like {@link TestGraph}: the code exchange of
 * {@code api.instagram.com} and the long-lived token, refresh and profile of {@code graph.instagram.com} on one origin.
 * A test registers the account behind an OAuth code ({@link #account}); every token this fake hands out contains the
 * code, so a test can look for it in the database and the logs.
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
    public static final Set<String> ALL_PERMISSIONS = Set.of("instagram_business_basic",
            "instagram_business_content_publish");
    /** {@code expires_in} of a long-lived token: about 60 days, as documented. */
    public static final long EXPIRES_IN = 5_184_000;

    private static final Map<String, Account> ACCOUNTS = new ConcurrentHashMap<>();
    private static final Set<String> REVOKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> UNAVAILABLE = ConcurrentHashMap.newKeySet();
    private static final List<String> REFRESHED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger REFRESHES = new AtomicInteger();
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
     * What the person behind a code granted, and their account.
     *
     * @param accountType  {@code BUSINESS}, {@code MEDIA_CREATOR} or {@code PERSONAL}
     * @param flatExchange the code exchange answers at the top level instead of in a {@code data} array
     */
    public record Account(String username, String accountType, Set<String> permissions, boolean flatExchange,
            boolean failExchange) {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** Registers an account and returns a new OAuth code for it. */
    public static String account(String username, String accountType, Set<String> permissions,
            boolean flatExchange) {
        String code = "code-" + UUID.randomUUID();
        ACCOUNTS.put(code, new Account(username, accountType, permissions, flatExchange, false));
        return code;
    }

    public static String business(String username) {
        return account(username, "BUSINESS", ALL_PERMISSIONS, false);
    }

    /** A code Instagram refuses. */
    public static String refusedCode() {
        String code = "code-" + UUID.randomUUID();
        ACCOUNTS.put(code, new Account("refused", "BUSINESS", ALL_PERMISSIONS, false, true));
        return code;
    }

    /** The person removed Soukly from their apps: every token of this code is refused from now on. */
    public static void revoke(String code) {
        REVOKED.add(code);
    }

    /** Instagram answers 500 to the refresh of the tokens of this code. */
    public static void unavailable(String code) {
        UNAVAILABLE.add(code);
    }

    public static String shortToken(String code) {
        return "IGshort" + code;
    }

    public static String longToken(String code) {
        return "IGlong" + code;
    }

    /** Professional account id of the account behind {@code code} ({@code user_id}). */
    public static String accountId(String code) {
        return "1784" + Math.abs(code.hashCode());
    }

    /** App-scoped id of the account behind {@code code} ({@code id}). */
    public static String scopedId(String code) {
        return "igsid-" + code.substring("code-".length());
    }

    /** Codes whose token was refreshed, in order. */
    public static List<String> refreshed() {
        return List.copyOf(REFRESHED);
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
            if (method.equals("POST") && path.equals("/oauth/access_token")) {
                exchangeCode(exchange, query(read(exchange.getRequestBody())));
            } else if (method.equals("GET") && path.equals("/access_token")) {
                longLived(exchange, query);
            } else if (method.equals("GET") && path.equals("/refresh_access_token")) {
                refresh(exchange, query);
            } else if (method.equals("GET") && path.equals("/" + VERSION + "/me")) {
                profile(exchange, query);
            } else if (path.startsWith("/" + VERSION + "/")) {
                publishing(exchange, method, path.substring(VERSION.length() + 2).split("/"),
                        method.equals("POST") ? query(read(exchange.getRequestBody())) : query);
            } else {
                error(exchange, 404);
            }
        }
    }

    private static void exchangeCode(HttpExchange exchange, Map<String, String> form) throws IOException {
        String code = form.get("code");
        Account account = code == null ? null : ACCOUNTS.get(code);
        if (!APP_ID.equals(form.get("client_id")) || !APP_SECRET.equals(form.get("client_secret"))
                || !"authorization_code".equals(form.get("grant_type")) || form.get("redirect_uri") == null
                || account == null || account.failExchange()) {
            error(exchange, 400);
            return;
        }
        String token = "\"access_token\":\"" + shortToken(code) + "\",\"user_id\":" + accountId(code);
        if (account.flatExchange()) {
            send(exchange, 200, "{" + token + ",\"permissions\":["
                    + String.join(",", account.permissions().stream().map(p -> "\"" + p + "\"").toList()) + "]}");
        } else {
            send(exchange, 200, "{\"data\":[{" + token + ",\"permissions\":\""
                    + String.join(",", account.permissions()) + "\"}]}");
        }
    }

    private static void longLived(HttpExchange exchange, Map<String, String> query) throws IOException {
        String code = codeOf(query.get("access_token"), "IGshort");
        if (!"ig_exchange_token".equals(query.get("grant_type")) || !APP_SECRET.equals(query.get("client_secret"))
                || code == null) {
            error(exchange, 400);
            return;
        }
        sendToken(exchange, longToken(code));
    }

    private static void refresh(HttpExchange exchange, Map<String, String> query) throws IOException {
        String code = codeOf(query.get("access_token"), "IG");
        if (!"ig_refresh_token".equals(query.get("grant_type")) || code == null || REVOKED.contains(code)) {
            error(exchange, 400);
            return;
        }
        if (UNAVAILABLE.contains(code)) {
            error(exchange, 500);
            return;
        }
        REFRESHED.add(code);
        sendToken(exchange, "IGrefreshed" + REFRESHES.incrementAndGet() + "-" + code);
    }

    private static void profile(HttpExchange exchange, Map<String, String> query) throws IOException {
        String code = codeOf(query.get("access_token"), "IG");
        if (code == null || REVOKED.contains(code)) {
            error(exchange, 401);
            return;
        }
        Account account = ACCOUNTS.get(code);
        send(exchange, 200, "{\"id\":\"" + scopedId(code) + "\",\"user_id\":\"" + accountId(code)
                + "\",\"username\":\"" + account.username() + "\",\"account_type\":\"" + account.accountType()
                + "\",\"profile_picture_url\":\"https://scontent.example.test/ig/" + account.username() + ".jpg\"}");
    }

    private static void publishing(HttpExchange exchange, String method, String[] path, Map<String, String> params)
            throws IOException {
        String code = codeOf(params.get("access_token"), "IG");
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

    private static void sendToken(HttpExchange exchange, String token) throws IOException {
        send(exchange, 200, "{\"access_token\":\"" + token + "\",\"token_type\":\"bearer\",\"expires_in\":"
                + EXPIRES_IN + "}");
    }

    /** The code a token of this fake was made from, when it starts with {@code prefix} and the code is known. */
    private static String codeOf(String token, String prefix) {
        if (token == null || !token.startsWith(prefix) || !token.contains("code-")) {
            return null;
        }
        String code = token.substring(token.indexOf("code-"));
        return ACCOUNTS.containsKey(code) ? code : null;
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
