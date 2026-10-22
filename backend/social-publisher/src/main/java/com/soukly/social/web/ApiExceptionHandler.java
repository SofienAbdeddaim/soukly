package com.soukly.social.web;

import java.net.URI;
import java.util.Locale;

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
import com.soukly.social.facebook.PageSelectionNotFoundException;
import com.soukly.social.oauth.NetworkNotConfiguredException;
import com.soukly.social.oauth.OAuthFailedException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns every error into the Soukly {@code application/problem+json} format (SETUP-06). Request bodies are never
 * logged: they carry OAuth codes.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Member of an OAuth problem: the language of the page that started the connection, when the state was valid. */
    static final String LANG = "lang";

    @ExceptionHandler
    ResponseEntity<Object> oauthFailed(OAuthFailedException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type(e.failure().slug()), e.failure().status(),
                e.failure().title(), e.detail(), instance(request), requestId(request));
        if (e.lang() != null) {
            problem.setProperty(LANG, e.lang());
        }
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> pageSelectionNotFound(PageSelectionNotFoundException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("page-selection-not-found"), HttpStatus.NOT_FOUND,
                "Page choice not found", "No choice of Page is in progress, or it expired. Connect again.",
                instance(request), requestId(request)));
    }

    /** {@code facebook-not-configured} or {@code instagram-not-configured}. */
    @ExceptionHandler
    ResponseEntity<Object> networkNotConfigured(NetworkNotConfiguredException e, NativeWebRequest request) {
        String name = e.network().displayName();
        return problem(ProblemDetails.create(
                ProblemDetails.type(e.network().name().toLowerCase(Locale.ROOT) + "-not-configured"),
                HttpStatus.SERVICE_UNAVAILABLE, name + " not configured",
                name + " cannot be connected in this environment yet.", instance(request), requestId(request)));
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
