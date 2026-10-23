package com.soukly.social.graph;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.soukly.social.caption.PersonalData;

/**
 * The {@code error} object of a Graph API answer
 * ({@code {"error": {"message", "type", "code", "error_subcode", "is_transient", "fbtrace_id"}}}).
 *
 * @param code      Meta error code, for example {@code 190} for an invalid token; {@code null} when absent
 * @param subcode   {@code error_subcode}, {@code null} when absent
 * @param isTransient {@code is_transient}: Meta says the same call may succeed later; {@code null} when absent
 * @param message   message of Meta, cleaned by {@link #safeMessage}: safe to log
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphError(Integer code, @JsonProperty("error_subcode") Integer subcode,
        @JsonProperty("is_transient") Boolean isTransient, String type, String message) {

    static final int MESSAGE_MAX = 200;

    private static final Pattern TOKEN_PARAMETER = Pattern.compile("(?i)(access_token|client_secret|code)=[^&\\s\"]+");
    /** Meta access tokens start with {@code EAA} (Facebook) or {@code IG} (Instagram), followed by a long string. */
    private static final Pattern TOKEN = Pattern.compile("\\b(?:EAA|IG)[A-Za-z0-9_-]{16,}");

    public GraphError {
        message = safeMessage(message);
    }

    /**
     * A message of Meta that can be logged: without token, secret or code in a quoted URL, without email address or
     * phone number, on one line and at most {@value #MESSAGE_MAX} characters.
     */
    public static String safeMessage(String message) {
        if (message == null) {
            return null;
        }
        String safe = TOKEN_PARAMETER.matcher(message).replaceAll("$1=[…]");
        safe = TOKEN.matcher(safe).replaceAll("[…]");
        safe = PersonalData.mask(safe).replaceAll("\\s+", " ").strip();
        return safe.length() <= MESSAGE_MAX ? safe : safe.substring(0, MESSAGE_MAX) + "…";
    }

    /** The code and subcode for a log or {@code last_error_code}: {@code 100}, {@code 190/463}. */
    public String codes() {
        if (code == null) {
            return "none";
        }
        return subcode == null ? String.valueOf(code) : code + "/" + subcode;
    }
}
