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
import com.soukly.listing.contact.ContactLimitReachedException;
import com.soukly.listing.listing.AlreadyPublishedException;
import com.soukly.listing.listing.DraftLimitReachedException;
import com.soukly.listing.listing.IfMatchRequiredException;
import com.soukly.listing.listing.InvalidListingException;
import com.soukly.listing.listing.ListingGoneException;
import com.soukly.listing.listing.ListingIncompleteException;
import com.soukly.listing.listing.ListingNotFoundException;
import com.soukly.listing.listing.OwnListingException;
import com.soukly.listing.listing.RenewNotAllowedException;
import com.soukly.listing.listing.VersionConflictException;
import com.soukly.listing.photo.PhotoLimitReachedException;
import com.soukly.listing.photo.PhotoNotFoundException;
import com.soukly.listing.photo.PhotoRejectedException;
import com.soukly.listing.referential.CategoryNotFoundException;
import com.soukly.listing.referential.GovernorateNotFoundException;
import com.soukly.listing.search.InvalidSearchException;
import com.soukly.listing.seller.SellerUnavailableException;

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

    /** Member of a {@code listing-incomplete} problem: the same list as {@code missingFields} of the listing. */
    static final String MISSING_FIELDS = "missingFields";

    /**
     * Member of a {@code photo-rejected} problem, the stable reason ({@code photo.too-large}...), and of an
     * {@code invalid-search} problem ({@code filter.unknown-attribute}, {@code cursor.invalid}...).
     */
    static final String CODE = "code";

    /** Members of a {@code listing-gone} problem (CATA-03). */
    static final String CATEGORY_CODE = "categoryCode";
    static final String GOVERNORATE_CODE = "governorateCode";

    @ExceptionHandler
    ResponseEntity<Object> invalidListing(InvalidListingException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("validation-failed"),
                HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.", instance(request),
                requestId(request));
        problem.setProperty(ERRORS, e.errors());
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> invalidSearch(InvalidSearchException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("invalid-search"),
                HttpStatus.BAD_REQUEST, "Invalid search", "A search parameter cannot be applied.",
                instance(request), requestId(request));
        problem.setProperty(CODE, e.code());
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> listingNotFound(ListingNotFoundException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("listing-not-found"), HttpStatus.NOT_FOUND,
                "Listing not found", "No listing with this id is visible to you.", instance(request),
                requestId(request)));
    }

    /** Only the subcategory and the governorate, for similar listings (CATA-03); nothing else of the listing. */
    @ExceptionHandler
    ResponseEntity<Object> listingGone(ListingGoneException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("listing-gone"), HttpStatus.GONE,
                "Listing gone", "This listing is not online any more.", instance(request), requestId(request));
        problem.setProperty(CATEGORY_CODE, e.categoryCode());
        problem.setProperty(GOVERNORATE_CODE, e.governorateCode());
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> ownListing(OwnListingException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("own-listing"), HttpStatus.CONFLICT, "Own listing",
                "This listing is yours: its contact is your own.", instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> contactLimitReached(ContactLimitReachedException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("contact-limit-reached"),
                HttpStatus.TOO_MANY_REQUESTS, "Contact limit reached",
                "You were shown the numbers of many listings today. Try again later.", instance(request),
                requestId(request));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfter().toSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> sellerUnavailable(SellerUnavailableException e, NativeWebRequest request) {
        return problem(ProblemDetails.forStatus(HttpStatus.SERVICE_UNAVAILABLE,
                "The seller cannot be read right now. Try again.", instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> draftLimitReached(DraftLimitReachedException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("draft-limit-reached"), HttpStatus.CONFLICT,
                "Draft limit reached", "An account can keep at most " + e.limit() + " drafts.", instance(request),
                requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> listingIncomplete(ListingIncompleteException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("listing-incomplete"),
                HttpStatus.UNPROCESSABLE_CONTENT, "Listing incomplete",
                "Complete the missing fields before publishing.", instance(request), requestId(request));
        problem.setProperty(MISSING_FIELDS, e.missingFields());
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> alreadyPublished(AlreadyPublishedException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("already-published"), HttpStatus.CONFLICT,
                "Already published", "This listing was published already; an expired one can be renewed.",
                instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> renewNotAllowed(RenewNotAllowedException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("renew-not-allowed"), HttpStatus.CONFLICT,
                "Renewal not allowed",
                "Only an expired listing, or a published one that expires soon, can be put back online.",
                instance(request), requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> photoNotFound(PhotoNotFoundException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("photo-not-found"), HttpStatus.NOT_FOUND,
                "Photo not found", "This listing has no photo with this id.", instance(request),
                requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> photoLimitReached(PhotoLimitReachedException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("photo-limit-reached"), HttpStatus.CONFLICT,
                "Photo limit reached", "A listing can have at most " + e.limit() + " photos.", instance(request),
                requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> photoRejected(PhotoRejectedException e, NativeWebRequest request) {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("photo-rejected"),
                HttpStatus.BAD_REQUEST, "Photo rejected", "The uploaded file cannot be used as a photo.",
                instance(request), requestId(request));
        problem.setProperty(CODE, e.rejection().code());
        return problem(problem);
    }

    @ExceptionHandler
    ResponseEntity<Object> versionConflict(VersionConflictException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("version-conflict"),
                HttpStatus.PRECONDITION_FAILED, "Version conflict",
                "The listing was changed since this version was read. Read it again.", instance(request),
                requestId(request)));
    }

    @ExceptionHandler
    ResponseEntity<Object> ifMatchRequired(IfMatchRequiredException e, NativeWebRequest request) {
        return problem(ProblemDetails.create(ProblemDetails.type("if-match-required"),
                HttpStatus.PRECONDITION_REQUIRED, "If-Match required",
                "Send the ETag of the version you read in If-Match.", instance(request), requestId(request)));
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
