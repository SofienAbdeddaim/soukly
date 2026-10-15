package com.soukly.common.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the single error format of Soukly: {@code application/problem+json} (RFC 9457).
 *
 * <p>Every error body has the members {@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance} and the extension member {@code requestId} (the {@code X-Request-Id} of the request).
 * The {@code type} is a URN ({@code urn:soukly:problem:<slug>}), so no domain is hard-coded.
 * Clients branch on {@code type} or {@code status}, never on {@code title} or {@code detail}.
 */
public final class ProblemDetails {

    /** Prefix of every Soukly problem type. */
    public static final String TYPE_PREFIX = "urn:soukly:problem:";

    /** Name of the extension member that carries the request identifier. */
    public static final String REQUEST_ID = "requestId";

    private ProblemDetails() {
    }

    /**
     * Returns the problem type of a slug, for example {@code urn:soukly:problem:email-taken}.
     */
    public static URI type(String slug) {
        return URI.create(TYPE_PREFIX + slug);
    }

    /**
     * Returns the generic problem type of an HTTP status, for example
     * {@code urn:soukly:problem:service-unavailable} for 503.
     */
    public static URI typeOf(HttpStatus status) {
        return type(status.getReasonPhrase().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
    }

    /**
     * Builds a problem with the generic type and title of {@code status}.
     *
     * @param detail    explanation for developers, never personal data or internal details
     * @param instance  path of the request, for example {@code /api/listings/42}
     * @param requestId identifier of the request, or {@code null} when unknown
     */
    public static ProblemDetail forStatus(HttpStatus status, String detail, URI instance, String requestId) {
        return create(typeOf(status), status, status.getReasonPhrase(), detail, instance, requestId);
    }

    /**
     * Builds a problem with a specific type and title.
     */
    public static ProblemDetail create(URI type, HttpStatus status, String title, String detail, URI instance,
            String requestId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(instance);
        if (requestId != null) {
            problem.setProperty(REQUEST_ID, requestId);
        }
        return problem;
    }
}
