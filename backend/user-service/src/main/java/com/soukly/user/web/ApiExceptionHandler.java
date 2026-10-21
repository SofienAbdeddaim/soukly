package com.soukly.user.web;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.soukly.common.error.ProblemDetails;
import com.soukly.common.web.SouklyHeaders;
import com.soukly.user.registration.InvalidRequestException;
import com.soukly.user.registration.InvalidVerificationTokenException;
import com.soukly.user.session.ForbiddenOriginException;
import com.soukly.user.session.RefreshCookies;
import com.soukly.user.session.SessionException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns every error into the Soukly {@code application/problem+json} format (SETUP-06). Request bodies are never
 * logged.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Member of a validation problem that lists the invalid fields: {@code [{"field", "code"}]}. */
    static final String ERRORS = "errors";

    private final RefreshCookies refreshCookies;

    ApiExceptionHandler(RefreshCookies refreshCookies) {
        this.refreshCookies = refreshCookies;
    }

    @ExceptionHandler
    ResponseEntity<Object> invalidRequest(InvalidRequestException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("validation-failed"),
                HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.", instance(request),
                requestId(request));
        problem.setProperty(ERRORS, e.errors());
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> invalidToken(InvalidVerificationTokenException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("verification-token-invalid"),
                HttpStatus.BAD_REQUEST, "Invalid verification token",
                "The verification token is unknown, expired or already used.", instance(request),
                requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> session(SessionException e, NativeWebRequest request) {
        URI instance = instance(request);
        String requestId = requestId(request);
        return switch (e) {
            case SessionException.InvalidCredentials c -> problem(ProblemDetails.create(
                    ProblemDetails.type("invalid-credentials"), HttpStatus.UNAUTHORIZED, "Invalid credentials",
                    "The email or the password is incorrect.", instance, requestId));
            case SessionException.EmailNotVerified n -> problem(ProblemDetails.create(
                    ProblemDetails.type("email-not-verified"), HttpStatus.FORBIDDEN, "Email not verified",
                    "The email of this account is not verified yet.", instance, requestId));
            case SessionException.LoginThrottled t -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds(t)))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(ProblemDetails.forStatus(HttpStatus.TOO_MANY_REQUESTS,
                            "Too many failed attempts. Please try again later.", instance, requestId));
            // The cookie is deleted: the browser must not present it again.
            case SessionException.SessionExpired x -> sessionEnded(ProblemDetails.create(
                    ProblemDetails.type("session-expired"), HttpStatus.UNAUTHORIZED, "Session expired",
                    "The session has expired. Please sign in again.", instance, requestId));
            case SessionException.SessionRevoked r -> sessionEnded(ProblemDetails.create(
                    ProblemDetails.type("session-revoked"), HttpStatus.UNAUTHORIZED, "Session revoked",
                    "The session was closed. Please sign in again.", instance, requestId));
        };
    }

    @ExceptionHandler
    ResponseEntity<Object> forbiddenOrigin(ForbiddenOriginException e, NativeWebRequest request) {
        return problem(ProblemDetails.forStatus(HttpStatus.FORBIDDEN, "The origin of the request is not allowed.",
                instance(request), requestId(request)));
    }

    /** Missing or invalid access token (resource server); the reason is never echoed, it may describe the token. */
    @ExceptionHandler
    ResponseEntity<Object> unauthenticated(AuthenticationException e, NativeWebRequest request) {
        String challenge = e instanceof InvalidBearerTokenException ? "Bearer error=\"invalid_token\"" : "Bearer";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetails.forStatus(HttpStatus.UNAUTHORIZED, "A valid bearer token is required.",
                        instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> accessDenied(AccessDeniedException e, NativeWebRequest request) {
        return problem(ProblemDetails.forStatus(HttpStatus.FORBIDDEN, "Access to this resource is denied.",
                instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> unexpected(Exception e, NativeWebRequest request) {
        log.error("Unexpected error, requestId {}", requestId(request), e);
        return problem(ProblemDetails.forStatus(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error.",
                instance(request), requestId(request)));
    }

    /** Spring MVC errors (malformed JSON, unknown path, wrong method...) in the same format. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ProblemDetail problem = ProblemDetails.forStatus(status, status.getReasonPhrase() + ".", instance(request),
                requestId(request));
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<Object> sessionEnded(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).header(HttpHeaders.SET_COOKIE, refreshCookies.clear())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** Whole seconds, rounded up, at least 1. */
    private static long seconds(SessionException.LoginThrottled throttled) {
        return Math.max(1, (throttled.retryAfter().toMillis() + 999) / 1000);
    }

    private static ResponseEntity<Object> problem(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static URI instance(WebRequest request) {
        HttpServletRequest servletRequest = ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
        return servletRequest == null ? null : URI.create(servletRequest.getRequestURI());
    }

    private static String requestId(WebRequest request) {
        return request.getHeader(SouklyHeaders.REQUEST_ID);
    }
}
