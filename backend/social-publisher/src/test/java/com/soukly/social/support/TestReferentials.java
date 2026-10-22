package com.soukly.social.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulated public referential endpoints of listing-service (CAT-01) on a local HTTP server: a few categories and
 * places with their real labels, enough for the post texts. A test can make it fail ({@link #down}) and count the
 * calls, to check the cache.
 */
public final class TestReferentials {

    private static final String CATEGORIES = """
            [{"code":"vehicles","label":{"fr":"Véhicules","ar":"المركبات","en":"Vehicles"},"subcategories":[
              {"code":"vehicles.cars","familyCode":"vehicles","label":{"fr":"Voitures","ar":"سيارات","en":"Cars"}},
              {"code":"vehicles.motorcycles","familyCode":"vehicles",
               "label":{"fr":"Motos et scooters","ar":"دراجات نارية وسكوترات","en":"Motorcycles and scooters"}}]},
             {"code":"services","label":{"fr":"Services","ar":"الخدمات","en":"Services"},"subcategories":[
              {"code":"services.tutoring","familyCode":"services",
               "label":{"fr":"Cours et soutien scolaire","ar":"دروس ودعم مدرسي","en":"Lessons and tutoring"}}]}]
            """;
    private static final String GOVERNORATES = """
            [{"code":"tunis","label":{"fr":"Tunis","ar":"تونس","en":"Tunis"}},
             {"code":"ben-arous","label":{"fr":"Ben Arous","ar":"بن عروس","en":"Ben Arous"}},
             {"code":"sfax","label":{"fr":"Sfax","ar":"صفاقس","en":"Sfax"}}]
            """;
    private static final String SFAX_DELEGATIONS = """
            [{"code":"sfax.sakiet-ezzit","label":{"fr":"Sakiet Ezzit","ar":"ساقية الزيت","en":"Sakiet Ezzit"}},
             {"code":"sfax.sfax-ville","label":{"fr":"Sfax Ville","ar":"صفاقس المدينة","en":"Sfax Ville"}}]
            """;

    private static final AtomicBoolean DOWN = new AtomicBoolean();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final HttpServer SERVER = start();

    private TestReferentials() {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /** While true, every endpoint answers {@code 500}. */
    public static void down(boolean down) {
        DOWN.set(down);
    }

    public static int calls() {
        return CALLS.get();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", TestReferentials::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        CALLS.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        if (DOWN.get()) {
            send(exchange, 500, "{}");
            return;
        }
        switch (path) {
            case "/categories" -> send(exchange, 200, CATEGORIES);
            case "/locations/governorates" -> send(exchange, 200, GOVERNORATES);
            case "/locations/governorates/sfax/delegations" -> send(exchange, 200, SFAX_DELEGATIONS);
            case "/locations/governorates/tunis/delegations", "/locations/governorates/ben-arous/delegations" ->
                send(exchange, 200, "[]");
            default -> send(exchange, 404, "{}");
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
