package com.soukly.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailsTest {

    @Test
    void buildsGenericProblemFromStatus() {
        ProblemDetail problem = ProblemDetails.forStatus(HttpStatus.SERVICE_UNAVAILABLE, "The service is unavailable.",
                URI.create("/api/social/accounts"), "req-1");

        assertThat(problem.getType()).hasToString("urn:soukly:problem:service-unavailable");
        assertThat(problem.getTitle()).isEqualTo("Service Unavailable");
        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getDetail()).isEqualTo("The service is unavailable.");
        assertThat(problem.getInstance()).hasToString("/api/social/accounts");
        assertThat(problem.getProperties()).containsEntry(ProblemDetails.REQUEST_ID, "req-1");
    }

    @Test
    void slugifiesMultiWordReasonPhrases() {
        assertThat(ProblemDetails.typeOf(HttpStatus.TOO_MANY_REQUESTS))
                .hasToString("urn:soukly:problem:too-many-requests");
    }

    @Test
    void omitsRequestIdWhenUnknown() {
        ProblemDetail problem = ProblemDetails.create(ProblemDetails.type("email-taken"), HttpStatus.CONFLICT,
                "Email already used", "This email already has an account.", URI.create("/api/users/register"), null);

        assertThat(problem.getType()).hasToString("urn:soukly:problem:email-taken");
        assertThat(problem.getProperties()).isNullOrEmpty();
    }
}
