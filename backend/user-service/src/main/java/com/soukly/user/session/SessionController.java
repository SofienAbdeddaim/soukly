package com.soukly.user.session;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Public session endpoints, exposed by the gateway under {@code /api/users/**}. The access token goes in the body,
 * the refresh token in the {@code soukly_rt} cookie only.
 */
@RestController
@RequestMapping("/users")
class SessionController {

    private final SessionService sessions;
    private final RefreshCookies cookies;
    private final OriginPolicy origins;

    SessionController(SessionService sessions, RefreshCookies cookies, OriginPolicy origins) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.origins = origins;
    }

    @PostMapping("/login")
    ResponseEntity<SessionResponse> login(@RequestBody LoginRequest request,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return opened(sessions.login(request, userAgent));
    }

    @PostMapping("/refresh")
    ResponseEntity<SessionResponse> refresh(HttpServletRequest request,
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        origins.check(request);
        return opened(sessions.refresh(refreshToken, userAgent));
    }

    /** {@code 204} and the cookie deleted, even without a cookie. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request,
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        origins.check(request);
        sessions.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    private ResponseEntity<SessionResponse> opened(Session session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString())
                .body(SessionResponse.of(session));
    }
}
