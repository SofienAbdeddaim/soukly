package com.soukly.social.post;

import java.util.Set;

import com.soukly.social.graph.GraphApiException;
import com.soukly.social.graph.GraphError;

/**
 * What a failed Graph API call means for a post (SOC-04), from the HTTP status and the {@code error} object of Meta
 * (codes of the Graph API "Handling Errors" guide and "Rate Limits" page):
 *
 * <ul>
 * <li>{@link Kind#TEMPORARY}: no answer, 5xx, the throttling codes ({@code 4}, {@code 17}, {@code 32}, {@code 341},
 * {@code 613}, {@code 80001}), the service errors {@code 1} and {@code 2}, HTTP 429, or {@code is_transient};</li>
 * <li>{@link Kind#TOKEN_INVALID}: {@code 190} (invalid or expired token, whatever the subcode) and {@code 102}
 * (session);</li>
 * <li>{@link Kind#PERMISSION_MISSING}: {@code 10} and {@code 200} to {@code 299};</li>
 * <li>{@link Kind#NOT_FOUND}: {@code 100} with subcode {@code 33}, the object does not exist (a post already
 * deleted);</li>
 * <li>{@link Kind#REJECTED}: anything else (content, invalid photo...), kept as {@code meta-{code}}.</li>
 * </ul>
 *
 * @param kind      what to do
 * @param errorCode the {@code last_error_code} of the post
 */
record GraphFailure(Kind kind, String errorCode) {

    enum Kind {
        TEMPORARY, TOKEN_INVALID, PERMISSION_MISSING, NOT_FOUND, REJECTED
    }

    static final String NEEDS_RECONNECT = "needs-reconnect";
    static final String PERMISSION = "permission-missing";
    static final String UNAVAILABLE = "facebook-unavailable";

    private static final Set<Integer> THROTTLING = Set.of(1, 2, 4, 17, 32, 341, 613, 80001);
    private static final Set<Integer> TOKEN = Set.of(102, 190);
    private static final int PERMISSION_DENIED = 10;
    private static final int INVALID_PARAMETER = 100;
    private static final int OBJECT_DOES_NOT_EXIST = 33;

    static GraphFailure of(GraphApiException exception) {
        int status = exception.status();
        GraphError error = exception.error();
        if (error != null && error.code() != null) {
            int code = error.code();
            if (TOKEN.contains(code)) {
                return new GraphFailure(Kind.TOKEN_INVALID, NEEDS_RECONNECT);
            }
            if (code == PERMISSION_DENIED || (code >= 200 && code <= 299)) {
                return new GraphFailure(Kind.PERMISSION_MISSING, PERMISSION);
            }
            if (THROTTLING.contains(code) || error.isTransient() || status >= 500 || status == 429) {
                return new GraphFailure(Kind.TEMPORARY, "meta-" + error.codes());
            }
            if (code == INVALID_PARAMETER && Integer.valueOf(OBJECT_DOES_NOT_EXIST).equals(error.subcode())) {
                return new GraphFailure(Kind.NOT_FOUND, "meta-" + error.codes());
            }
            return new GraphFailure(Kind.REJECTED, "meta-" + error.codes());
        }
        if (status == 0 || status >= 500 || status == 429) {
            return new GraphFailure(Kind.TEMPORARY, UNAVAILABLE);
        }
        return new GraphFailure(Kind.REJECTED, "http-" + status);
    }
}
