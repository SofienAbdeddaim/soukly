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
 * <p>Instagram ({@link #ofInstagram}, SOC-05) answers with the same {@code error} object and adds its own subcodes
 * (Instagram Platform "Error Codes" page), read first:
 *
 * <ul>
 * <li>{@link Kind#QUOTA}: {@code 9} / {@code 2207042}, the posts published through the API within 24 hours reached
 * the limit of the account: not a failure, the post waits;</li>
 * <li>{@link Kind#TEMPORARY}: {@code 9007} / {@code 2207027}, the container is not ready to publish yet,
 * {@code 2207008}, it expired, {@code 2207001} and {@code 2207003}, an error or a timeout of Instagram, and
 * {@code 2207052}, the photo could not be fetched;</li>
 * <li>{@link Kind#REJECTED} {@code media-rejected}: {@code 36000} to {@code 36004} and the subcodes {@code 2207004},
 * {@code 2207005}, {@code 2207009}, a photo of a size, format or proportions Instagram refuses.</li>
 * </ul>
 *
 * @param kind      what to do
 * @param errorCode the {@code last_error_code} of the post
 */
record GraphFailure(Kind kind, String errorCode) {

    enum Kind {
        TEMPORARY, TOKEN_INVALID, PERMISSION_MISSING, NOT_FOUND, REJECTED, QUOTA
    }

    static final String NEEDS_RECONNECT = "needs-reconnect";
    static final String PERMISSION = "permission-missing";
    static final String UNAVAILABLE = "facebook-unavailable";
    static final String INSTAGRAM_UNAVAILABLE = "instagram-unavailable";

    static final String MEDIA_REJECTED = "media-rejected";
    static final String QUOTA_REACHED = "quota-reached";

    private static final int INSTAGRAM_LIMIT_SUBCODE = 2207042;
    private static final Set<Integer> INSTAGRAM_TEMPORARY_CODES = Set.of(9007);
    private static final Set<Integer> INSTAGRAM_TEMPORARY_SUBCODES = Set.of(2207001, 2207003, 2207008, 2207027,
            2207052);
    private static final Set<Integer> INSTAGRAM_MEDIA_CODES = Set.of(36000, 36001, 36003, 36004);
    private static final Set<Integer> INSTAGRAM_MEDIA_SUBCODES = Set.of(2207004, 2207005, 2207009);

    private static final Set<Integer> THROTTLING = Set.of(1, 2, 4, 17, 32, 341, 613, 80001);
    private static final Set<Integer> TOKEN = Set.of(102, 190);
    private static final int PERMISSION_DENIED = 10;
    private static final int INVALID_PARAMETER = 100;
    private static final int OBJECT_DOES_NOT_EXIST = 33;

    /** A failure of the Instagram API: its own subcodes, then those of the Graph API. */
    static GraphFailure ofInstagram(GraphApiException exception) {
        GraphError error = exception.error();
        if (error != null && error.code() != null) {
            Integer subcode = error.subcode();
            if (Integer.valueOf(INSTAGRAM_LIMIT_SUBCODE).equals(subcode)) {
                return new GraphFailure(Kind.QUOTA, QUOTA_REACHED);
            }
            if (INSTAGRAM_TEMPORARY_CODES.contains(error.code())
                    || (subcode != null && INSTAGRAM_TEMPORARY_SUBCODES.contains(subcode))) {
                return new GraphFailure(Kind.TEMPORARY, "meta-" + error.codes());
            }
            if (INSTAGRAM_MEDIA_CODES.contains(error.code())
                    || (subcode != null && INSTAGRAM_MEDIA_SUBCODES.contains(subcode))) {
                return new GraphFailure(Kind.REJECTED, MEDIA_REJECTED);
            }
        }
        GraphFailure failure = of(exception);
        return failure.errorCode().equals(UNAVAILABLE) ? new GraphFailure(Kind.TEMPORARY, INSTAGRAM_UNAVAILABLE)
                : failure;
    }

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
            if (THROTTLING.contains(code) || Boolean.TRUE.equals(error.isTransient()) || status >= 500 || status == 429) {
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
