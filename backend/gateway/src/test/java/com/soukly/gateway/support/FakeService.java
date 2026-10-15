package com.soukly.gateway.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * A downstream service played by a local Reactor Netty server. It records every request it receives and answers
 * {@code 200} with a small JSON body. When a JWKS is attached it also serves {@code /.well-known/jwks.json},
 * like user-service will (AUTH-02).
 */
public final class FakeService {

    public record Received(String method, String uri, HttpHeaders headers) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final DisposableServer server;

    private FakeService(String name, Supplier<String> jwks) {
        this.server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    if (jwks != null && request.uri().equals("/.well-known/jwks.json")) {
                        return response.header("Content-Type", "application/json")
                                .sendString(Mono.fromSupplier(jwks));
                    }
                    HttpHeaders headers = new HttpHeaders();
                    request.requestHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
                    received.add(new Received(request.method().name(), request.uri(), headers));
                    return response.header("Content-Type", "application/json")
                            .sendString(Mono.just("{\"service\":\"" + name + "\"}"));
                })
                .bindNow();
    }

    public static FakeService start(String name) {
        return new FakeService(name, null);
    }

    public static FakeService startWithJwks(String name, Supplier<String> jwks) {
        return new FakeService(name, jwks);
    }

    public String url() {
        return "http://127.0.0.1:" + server.port();
    }

    public List<Received> received() {
        return received;
    }

    public void reset() {
        received.clear();
    }
}
