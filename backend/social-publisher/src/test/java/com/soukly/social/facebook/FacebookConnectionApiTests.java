package com.soukly.social.facebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGraph;
import com.soukly.social.support.TestGraph.Page;
import com.soukly.social.support.TestJwks;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Connection of a Facebook Page (SOC-01) against the simulated Graph API: one Page, several Pages, no Page, a missing
 * permission, a cancellation, invalid states, the disconnection, and no token ever in an answer or a log.
 */
@ExtendWith(OutputCaptureExtension.class)
class FacebookConnectionApiTests extends IntegrationTest {

    private static final Page SFAX_AUTO = new Page("1001", "Sfax Auto Occasion", "Car dealership");
    private static final Page AMIRA_DECO = new Page("1002", "Amira Déco", "Shop");
    private static final Page COURS = new Page("1003", "Cours Ben Salah", "Education");

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID member;
    private CapturedOutput output;
    private final List<String> secrets = new java.util.ArrayList<>();

    @BeforeEach
    void member(CapturedOutput output) {
        this.member = UUID.randomUUID();
        this.output = output;
        secrets.clear();
        secrets.add(TestGraph.APP_SECRET);
    }

    /** "No token appears in the logs of the tests": checked after every test of this class. */
    @AfterEach
    void noSecretInTheLogs() {
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    @Test
    void aSinglePageIsConnectedAtOnceAndItsTokenIsEncrypted() throws Exception {
        String state = connect(member, "fr");
        String code = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO));

        String body = callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"))
                .andExpect(jsonPath("$.connection.network").value("FACEBOOK"))
                .andExpect(jsonPath("$.connection.status").value("CONNECTED"))
                .andExpect(jsonPath("$.connection.displayName").value("Sfax Auto Occasion"))
                .andExpect(jsonPath("$.connection.pictureUrl").value("https://scontent.example.test/1001.jpg"))
                .andExpect(jsonPath("$.connection.connectedAt").isNotEmpty())
                .andExpect(jsonPath("$.pendingSelection").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("EAA");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT external_id, external_user_id, token_ciphertext, key_version, status FROM social_connections "
                        + "WHERE user_id = ?", member);
        assertThat(row.get("external_id")).isEqualTo("1001");
        assertThat(row.get("external_user_id")).isEqualTo(TestGraph.userId(code));
        assertThat(((Number) row.get("key_version")).intValue()).isEqualTo(1);
        assertThat(row.get("status")).isEqualTo("CONNECTED");
        byte[] ciphertext = (byte[]) row.get("token_ciphertext");
        assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1))
                .doesNotContain(TestGraph.pageToken(code, "1001")).doesNotContain("EAA");

        connections(member)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].network").value("FACEBOOK"))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$[0].displayName").value("Sfax Auto Occasion"));
    }

    @Test
    void theAuthorizeUrlNamesTheAppTheRedirectUriTheStateAndThePermissions() throws Exception {
        String url = authorizeUrl(member, "ar");
        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();

        assertThat(uri.getScheme() + "://" + uri.getHost() + uri.getPath())
                .isEqualTo("https://www.facebook.example.test/v26.0/dialog/oauth");
        assertThat(uri.getQueryParams().getFirst("client_id")).isEqualTo(TestGraph.APP_ID);
        assertThat(decode(uri.getQueryParams().getFirst("redirect_uri")))
                .isEqualTo(FRONT_BASE_URL + "/oauth/facebook/callback");
        assertThat(uri.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(decode(uri.getQueryParams().getFirst("scope")))
                .isEqualTo("pages_show_list,pages_manage_posts,pages_read_engagement");
        String state = uri.getQueryParams().getFirst("state");
        // 32 random bytes in base64url
        assertThat(state).hasSize(43);
        assertThat(url).doesNotContain(TestGraph.APP_SECRET);

        // Stored hashed only, with the language
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth_states WHERE user_id = ?", Integer.class, member))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT lang FROM oauth_states WHERE user_id = ?", String.class, member))
                .isEqualTo("ar");
        byte[] stored = jdbc.queryForObject("SELECT state_hash FROM oauth_states WHERE user_id = ?", byte[].class,
                member);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(state);
    }

    @Test
    void anUnknownLanguageFallsBackToFrench() throws Exception {
        String state = connect(member, "de");
        String code = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO));

        callback(member, Map.of("code", code, "state", state)).andExpect(jsonPath("$.lang").value("fr"));
    }

    @Test
    void severalPagesAreOfferedWithoutTokenThenTheChosenOneIsConnected() throws Exception {
        String state = connect(member, "fr");
        String code = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO, AMIRA_DECO, COURS));

        String body = callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"))
                .andExpect(jsonPath("$.connection").doesNotExist())
                .andExpect(jsonPath("$.pendingSelection.length()").value(3))
                .andExpect(jsonPath("$.pendingSelection[0].pageId").value("1001"))
                .andExpect(jsonPath("$.pendingSelection[0].name").value("Sfax Auto Occasion"))
                .andExpect(jsonPath("$.pendingSelection[0].category").value("Car dealership"))
                .andExpect(jsonPath("$.pendingSelection[0].pictureUrl").isNotEmpty())
                .andExpect(jsonPath("$.pendingSelection[1].name").value("Amira Déco"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("EAA").doesNotContain("access_token").doesNotContain("Token");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                member)).isZero();
        byte[] pending = jdbc.queryForObject("SELECT ciphertext FROM pending_page_selections WHERE user_id = ?",
                byte[].class, member);
        assertThat(new String(pending, StandardCharsets.ISO_8859_1)).doesNotContain("EAA").doesNotContain("Sfax");

        // A page reloaded during the choice reads it again
        mockMvc.perform(get("/social/facebook/page-selection").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingSelection.length()").value(3));
        // Another member has no choice in progress
        mockMvc.perform(get("/social/facebook/page-selection")
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        choose(member, "1001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.network").value("FACEBOOK"))
                .andExpect(jsonPath("$.displayName").value("Sfax Auto Occasion"));
        assertThat(jdbc.queryForObject("SELECT external_id FROM social_connections WHERE user_id = ?", String.class,
                member)).isEqualTo("1001");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_page_selections WHERE user_id = ?",
                Integer.class, member)).isZero();
        // The choice was used
        choose(member, "1002").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:page-selection-not-found"));
    }

    @Test
    void aPageOutsideTheChoiceOrAnExpiredChoiceIsNotFound() throws Exception {
        String state = connect(member, "fr");
        callback(member, Map.of("code", person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO, AMIRA_DECO)),
                "state", state)).andExpect(status().isOk());

        choose(member, "9999").andExpect(status().isNotFound());
        clock.advance(Duration.ofMinutes(11));
        choose(member, "1001").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                member)).isZero();
    }

    @Test
    void connectingAgainReplacesThePageAndClearsNeedsReconnect() throws Exception {
        String first = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO));
        callback(member, Map.of("code", first, "state", connect(member, "fr"))).andExpect(status().isOk());
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ?", member);
        connections(member).andExpect(jsonPath("$[0].status").value("NEEDS_RECONNECT"));

        clock.advance(Duration.ofDays(3));
        String second = person(TestGraph.ALL_PERMISSIONS, List.of(COURS));
        callback(member, Map.of("code", second, "state", connect(member, "fr"))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                member)).isEqualTo(1);
        connections(member)
                .andExpect(jsonPath("$[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$[0].displayName").value("Cours Ben Salah"))
                .andExpect(jsonPath("$[0].connectedAt").value(clock.instant().toString()));
    }

    @Test
    void aStateUsedAlreadyOrStartedByAnotherMemberIsInvalidAndNothingIsSaved() throws Exception {
        String state = connect(member, "fr");
        String code = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO));
        UUID other = UUID.randomUUID();

        // Another member cannot use it, and does not consume it
        callback(other, Map.of("code", code, "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"))
                .andExpect(jsonPath("$.lang").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                other)).isZero();

        callback(member, Map.of("code", code, "state", state)).andExpect(status().isOk());
        // Single use
        callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"));
        // Unknown, or missing
        callback(member, Map.of("code", code, "state", "unknown-state")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"));
        callback(member, Map.of("code", code)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"));
    }

    @Test
    void anExpiredStateIsInvalid() throws Exception {
        String state = connect(member, "fr");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        callback(member, Map.of("code", person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO)), "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_connections WHERE user_id = ?", Integer.class,
                member)).isZero();
    }

    @Test
    void aCancellationOnFacebookIsReportedWithTheLanguage() throws Exception {
        String state = connect(member, "ar");

        callback(member, Map.of("error", "access_denied", "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-cancelled"))
                .andExpect(jsonPath("$.lang").value("ar"));
        connections(member).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aMissingPublishingPermissionIsReported() throws Exception {
        String state = connect(member, "fr");
        String code = person(Set.of("pages_show_list", "pages_read_engagement"), List.of(SFAX_AUTO));

        callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-permission-missing"))
                .andExpect(jsonPath("$.lang").value("fr"));
        connections(member).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aPersonWithoutPageIsReported() throws Exception {
        String state = connect(member, "fr");
        // A Page without a task that allows posting does not count
        Page readOnly = new Page("1004", "Analytics only", "Shop", List.of("ANALYZE"));

        callback(member, Map.of("code", person(TestGraph.ALL_PERMISSIONS, List.of(readOnly)), "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:facebook-no-page"))
                .andExpect(jsonPath("$.lang").value("fr"));
        connections(member).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aCodeRefusedByFacebookIsABadGateway() throws Exception {
        String state = connect(member, "en");

        callback(member, Map.of("code", TestGraph.refusedCode(), "state", state))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:facebook-unavailable"))
                .andExpect(jsonPath("$.lang").value("en"));
    }

    @Test
    void disconnectingDeletesTheConnectionAndRevokesThePermissions() throws Exception {
        String code = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO));
        callback(member, Map.of("code", code, "state", connect(member, "fr"))).andExpect(status().isOk());

        mockMvc.perform(delete("/social/facebook").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isNoContent());

        connections(member).andExpect(jsonPath("$.length()").value(0));
        assertThat(TestGraph.revoked()).contains(TestGraph.userId(code));
        // Nothing to disconnect is not an error
        mockMvc.perform(delete("/social/facebook").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isNoContent());
    }

    @Test
    void permissionsStayWhenAnotherAccountUsesThem() throws Exception {
        UUID secondAccount = UUID.randomUUID();
        String code = person(TestGraph.ALL_PERMISSIONS, List.of(SFAX_AUTO));
        callback(member, Map.of("code", code, "state", connect(member, "fr"))).andExpect(status().isOk());
        // The same person connects the Page to a second Soukly account: same app-scoped id
        callback(secondAccount, Map.of("code", code, "state", connect(secondAccount, "fr")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/social/facebook").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isNoContent());

        assertThat(TestGraph.revoked()).doesNotContain(TestGraph.userId(code));
    }

    @Test
    void everyEndpointNeedsAnAccessToken() throws Exception {
        mockMvc.perform(get("/social/connections")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:unauthorized"));
        mockMvc.perform(post("/social/facebook/connect")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/social/facebook/callback").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/social/facebook/page").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/social/facebook")).andExpect(status().isUnauthorized());
    }

    private String person(Set<String> permissions, List<Page> pages) {
        String code = TestGraph.person(permissions, pages);
        secrets.add(TestGraph.shortToken(code));
        secrets.add(TestGraph.longToken(code));
        pages.forEach(page -> secrets.add(TestGraph.pageToken(code, page.id())));
        secrets.add(code);
        return code;
    }

    private String authorizeUrl(UUID userId, String lang) throws Exception {
        String body = mockMvc.perform(post("/social/facebook/connect")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lang\":\"" + lang + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("authorizeUrl").asString();
    }

    /** Starts a connection and returns its state, as Facebook would send it back. */
    private String connect(UUID userId, String lang) throws Exception {
        String state = UriComponentsBuilder.fromUriString(authorizeUrl(userId, lang)).build().getQueryParams()
                .getFirst("state");
        secrets.add(state);
        return state;
    }

    private ResultActions callback(UUID userId, Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/social/facebook/callback")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions choose(UUID userId, String pageId) throws Exception {
        return mockMvc.perform(post("/social/facebook/page")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageId\":\"" + pageId + "\"}"));
    }

    private ResultActions connections(UUID userId) throws Exception {
        ResultActions result = mockMvc.perform(get("/social/connections")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isOk());
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        assertThat(body.toString()).doesNotContain("EAA").doesNotContain("token");
        return result;
    }

    private static String bearer(UUID userId) {
        return "Bearer " + TestJwks.token(userId);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
