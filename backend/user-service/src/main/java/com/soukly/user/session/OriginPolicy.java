package com.soukly.user.session;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * CSRF protection of the endpoints that read the refresh cookie, on top of {@code SameSite=Strict}: the
 * {@code Origin} header must be one of the allowed origins. Browsers send it on every {@code POST}, so a missing
 * header is refused too.
 */
@Component
public class OriginPolicy {

    private final Set<String> allowedOrigins;

    OriginPolicy(SessionProperties properties) {
        List<String> origins = properties.allowedOrigins();
        if (origins == null || origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("ALLOWED_ORIGINS must list exact origins, never '*'");
        }
        this.allowedOrigins = origins.stream().map(OriginPolicy::normalize).collect(Collectors.toUnmodifiableSet());
    }

    /** @throws ForbiddenOriginException when the request does not come from an allowed origin */
    public void check(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(normalize(origin))) {
            throw new ForbiddenOriginException();
        }
    }

    private static String normalize(String origin) {
        return origin.strip().replaceAll("/+$", "");
    }
}
