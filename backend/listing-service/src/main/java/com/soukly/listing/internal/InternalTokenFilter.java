package com.soukly.listing.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates the service-to-service calls of {@code /internal/**} with the shared token of
 * {@code X-Internal-Token}, the same mechanism as user-service (CATA-03). The comparison takes the same time whatever
 * the token sent. A missing or wrong token leaves the request anonymous, so it is answered {@code 401}.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    public InternalTokenFilter(InternalApiProperties properties) {
        this.expected = properties.configured() ? properties.token().getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sent = request.getHeader(HEADER);
        if (expected != null && sent != null
                && MessageDigest.isEqual(expected, sent.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        }
        chain.doFilter(request, response);
    }
}
