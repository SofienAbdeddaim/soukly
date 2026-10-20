package com.soukly.listing.web;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.soukly.common.error.ProblemDetails;
import com.soukly.common.web.SouklyHeaders;
import com.soukly.listing.referential.CategoryNotFoundException;
import com.soukly.listing.referential.GovernorateNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/** Turns every error into the Soukly {@code application/problem+json} format (SETUP-06). */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler
    ResponseEntity<Object> categoryNotFound(CategoryNotFoundException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("category-not-found"), HttpStatus.NOT_FOUND,
                "Category not found", "No active subcategory has this code.", instance(request),
                requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> governorateNotFound(GovernorateNotFoundException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("governorate-not-found"), HttpStatus.NOT_FOUND,
                "Governorate not found", "No governorate has this code.", instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> unexpected(Exception e, NativeWebRequest request) {
        log.error("Unexpected error, requestId {}", requestId(request), e);
        return problem(ProblemDetails.forStatus(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error.",
                instance(request), requestId(request)));
    }

    /** Spring MVC errors (unknown path, wrong method...) in the same format. */
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
