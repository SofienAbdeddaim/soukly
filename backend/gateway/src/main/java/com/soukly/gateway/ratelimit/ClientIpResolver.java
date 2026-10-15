package com.soukly.gateway.ratelimit;

import java.net.InetSocketAddress;
import java.util.List;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Finds the client IP of a request. {@code X-Forwarded-For} is only read when the connection comes from a
 * trusted reverse proxy (Caddy, configured by DEP): its last entry is the address the proxy saw.
 * Otherwise the header is ignored, so a client cannot choose its own IP.
 */
class ClientIpResolver {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpRange> trustedProxies;

    ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream()
                .map(String::trim)
                .filter(proxy -> !proxy.isEmpty())
                .map(IpRange::parse)
                .toList();
    }

    String resolve(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        String remoteIp = remote.getAddress().getHostAddress();
        if (isTrusted(remoteIp)) {
            String forwarded = lastForwardedFor(request);
            if (forwarded != null) {
                return forwarded;
            }
        }
        return remoteIp;
    }

    private boolean isTrusted(String ip) {
        return trustedProxies.stream().anyMatch(range -> range.contains(ip));
    }

    private static String lastForwardedFor(ServerHttpRequest request) {
        List<String> values = request.getHeaders().get(X_FORWARDED_FOR);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String last = values.getLast();
        String ip = last.substring(last.lastIndexOf(',') + 1).trim();
        return ip.isEmpty() ? null : ip;
    }
}
