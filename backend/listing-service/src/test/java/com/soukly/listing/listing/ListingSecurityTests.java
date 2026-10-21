package com.soukly.listing.listing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestJwks;

/** listing-service validates the access tokens again, with the rules of the gateway. */
class ListingSecurityTests extends IntegrationTest {

    private static final String BODY = "{ \"categoryCode\": \"home.furniture\" }";

    @Test
    void creatingWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/listings").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:unauthorized"));
        mockMvc.perform(patch("/listings/" + UUID.randomUUID()).header(HttpHeaders.IF_MATCH, "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokensAreUnauthorized() throws Exception {
        UUID account = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(3600);
        String[] tokens = {
                "not-a-jwt",
                // expired beyond the clock skew
                TestJwks.sign(TestJwks.claims(account).issueTime(Date.from(past))
                        .expirationTime(Date.from(past.plusSeconds(900))).build()),
                // another issuer
                TestJwks.sign(TestJwks.claims(account).issuer("someone-else").build()),
                // a key the JWKS does not publish
                TestJwks.signWithUnknownKey(TestJwks.claims(account).build()),
                // a subject that is not an account id
                TestJwks.sign(TestJwks.claims(account).subject("admin").build()) };
        for (String token : tokens) {
            mockMvc.perform(post("/listings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
        }
    }

    @Test
    void readingWithAnInvalidTokenIsUnauthorizedSoThatTheFrontEndRefreshesIt() throws Exception {
        mockMvc.perform(get("/listings/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void referentialsStayPublic() throws Exception {
        mockMvc.perform(get("/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/locations/governorates")).andExpect(status().isOk());
    }
}
