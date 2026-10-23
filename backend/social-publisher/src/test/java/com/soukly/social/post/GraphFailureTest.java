package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.soukly.social.graph.GraphApiException;
import com.soukly.social.graph.GraphError;
import com.soukly.social.post.GraphFailure.Kind;

class GraphFailureTest {

    @ParameterizedTest
    @CsvSource({
        // status, code, subcode, transient, kind, last_error_code
        "500, 1, , false, TEMPORARY, meta-1",
        "500, 2, , false, TEMPORARY, meta-2",
        "400, 4, , false, TEMPORARY, meta-4",
        "400, 17, , false, TEMPORARY, meta-17",
        "400, 32, , false, TEMPORARY, meta-32",
        "400, 341, , false, TEMPORARY, meta-341",
        "400, 613, , false, TEMPORARY, meta-613",
        "400, 80001, , false, TEMPORARY, meta-80001",
        "400, 9007, , true, TEMPORARY, meta-9007",
        "503, 9999, , false, TEMPORARY, meta-9999",
        "400, 190, , false, TOKEN_INVALID, needs-reconnect",
        "400, 190, 463, false, TOKEN_INVALID, needs-reconnect",
        "400, 190, 460, false, TOKEN_INVALID, needs-reconnect",
        "400, 102, , false, TOKEN_INVALID, needs-reconnect",
        "403, 10, , false, PERMISSION_MISSING, permission-missing",
        "403, 200, , false, PERMISSION_MISSING, permission-missing",
        "403, 299, , false, PERMISSION_MISSING, permission-missing",
        "400, 100, 33, false, NOT_FOUND, meta-100/33",
        "400, 100, , false, REJECTED, meta-100",
        "400, 324, , false, REJECTED, meta-324",
        "400, 368, , false, REJECTED, meta-368",
        "400, 506, , false, REJECTED, meta-506"
    })
    void classifiesTheErrorsOfMeta(int status, int code, Integer subcode, boolean isTransient, Kind kind,
            String errorCode) {
        GraphFailure failure = GraphFailure.of(new GraphApiException("Graph API post creation answered " + status,
                status, new GraphError(code, subcode, isTransient, "OAuthException", "message")));

        assertThat(failure.kind()).isEqualTo(kind);
        assertThat(failure.errorCode()).isEqualTo(errorCode);
    }

    @ParameterizedTest
    @CsvSource({ "0, TEMPORARY, facebook-unavailable", "502, TEMPORARY, facebook-unavailable",
        "429, TEMPORARY, facebook-unavailable", "404, REJECTED, http-404" })
    void classifiesTheAnswersWithoutAnErrorObject(int status, Kind kind, String errorCode) {
        GraphFailure failure = GraphFailure.of(new GraphApiException("Graph API photo upload failed", status));

        assertThat(failure.kind()).isEqualTo(kind);
        assertThat(failure.errorCode()).isEqualTo(errorCode);
    }

    @Test
    void anErrorWithoutCodeIsClassifiedByItsStatus() {
        GraphFailure failure = GraphFailure.of(new GraphApiException("Graph API post creation answered 500", 500,
                new GraphError(null, null, false, null, "Unknown")));

        assertThat(failure.kind()).isEqualTo(Kind.TEMPORARY);
    }
}
