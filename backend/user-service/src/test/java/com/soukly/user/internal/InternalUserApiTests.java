package com.soukly.user.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.soukly.user.support.IntegrationTest;
import com.soukly.user.support.SessionCalls;

/** Service-to-service endpoints of CATA-03: seller profile and phone number, behind {@code X-Internal-Token}. */
class InternalUserApiTests extends IntegrationTest {

    private static final String EMAIL = "amira@example.tn";
    private static final String PASSWORD = "Jasmin-de-Carthage-2026";

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = createAccount(EMAIL, PASSWORD, "ACTIVE");
        // Registered on 2026-03-31 at 23:30 UTC: already April in Tunis (UTC+1)
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-03-31T23:30:00Z")), userId);
    }

    @Test
    void theCorrectTokenGivesThePublicProfileWithoutContactData() throws Exception {
        mockMvc.perform(internal("/internal/users/" + userId + "/public-profile", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Amira Ben Salah"))
                .andExpect(jsonPath("$.memberSince").value("2026-04"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void theCorrectTokenGivesThePhoneNumberNeverCached() throws Exception {
        mockMvc.perform(internal("/internal/users/" + userId + "/contact", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.phone").value("+21620123456"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = { "public-profile", "contact" })
    void aMissingOrWrongTokenIsRefused(String endpoint) throws Exception {
        String path = "/internal/users/" + userId + "/" + endpoint;
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.phone").doesNotExist());
        mockMvc.perform(internal(path, "wrong-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(internal(path, INTERNAL_TOKEN + "x")).andExpect(status().isUnauthorized());
        mockMvc.perform(internal(path, "")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAccessTokenOfAUserDoesNotOpenTheInternalEndpoints() throws Exception {
        String accessToken = JsonPath.read(new SessionCalls(mockMvc).login(EMAIL, PASSWORD)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/internal/users/" + userId + "/contact")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAccountThatIsNotActiveOrUnknownIsNotFound() throws Exception {
        UUID pending = createAccount("pending@example.tn", PASSWORD, "PENDING_VERIFICATION");

        for (String id : new String[] { pending.toString(), UUID.randomUUID().toString(), "42" }) {
            mockMvc.perform(internal("/internal/users/" + id + "/public-profile", INTERNAL_TOKEN))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("urn:soukly:problem:user-not-found"));
            mockMvc.perform(internal("/internal/users/" + id + "/contact", INTERNAL_TOKEN))
                    .andExpect(status().isNotFound());
        }
    }

    private static MockHttpServletRequestBuilder internal(String path, String token) {
        return get(path).header(InternalTokenFilter.HEADER, token);
    }
}
