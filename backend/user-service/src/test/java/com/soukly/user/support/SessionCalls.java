package com.soukly.user.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

/** Login, refresh and logout calls as the browser makes them through the gateway. */
public final class SessionCalls {

    public static final String COOKIE = "soukly_rt";
    private static final Pattern COOKIE_VALUE = Pattern.compile("^" + COOKIE + "=([^;]*)");

    private final MockMvc mockMvc;

    public SessionCalls(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Linux; Android 14) Soukly tests")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    public ResultActions refresh(String refreshToken, String origin) throws Exception {
        var request = post("/users/refresh");
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        if (refreshToken != null) {
            request.cookie(new Cookie(COOKIE, refreshToken));
        }
        return mockMvc.perform(request);
    }

    public ResultActions logout(String refreshToken, String origin) throws Exception {
        var request = post("/users/logout");
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        if (refreshToken != null) {
            request.cookie(new Cookie(COOKIE, refreshToken));
        }
        return mockMvc.perform(request);
    }

    /** The raw {@code Set-Cookie} header of the refresh cookie, or {@code null}. */
    public static String setCookie(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE + "=")).findFirst().orElse(null);
    }

    /** Value of the refresh cookie set by the response. */
    public static String refreshToken(MvcResult result) {
        String header = setCookie(result);
        if (header == null) {
            throw new AssertionError("No " + COOKIE + " cookie in the response");
        }
        Matcher matcher = COOKIE_VALUE.matcher(header);
        if (!matcher.find()) {
            throw new AssertionError("Malformed Set-Cookie: " + header);
        }
        return matcher.group(1);
    }
}
