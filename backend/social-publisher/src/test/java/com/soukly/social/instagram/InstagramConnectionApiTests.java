package com.soukly.social.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.social.connection.Network;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestGraph;
import com.soukly.social.support.TestInstagram;
import com.soukly.social.support.TestJwks;
import com.soukly.social.token.TokenCipher;

import tools.jackson.databind.json.JsonMapper;

/**
 * Connection of an Instagram professional account (SOC-02) against the simulated Instagram API: Business and Creator
 * accounts, a personal account, a cancellation, a missing permission, invalid states, the daily refresh of the tokens
 * (refreshed, refused, Instagram down), the disconnection next to a Facebook Page, and no token ever in an answer or a
 * log.
 */
@ExtendWith(OutputCaptureExtension.class)
class InstagramConnectionApiTests extends IntegrationTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> secrets = new ArrayList<>();

    @Autowired
    private InstagramTokenRefresher refresher;

    @Autowired
    private TokenCipher cipher;

    private UUID member;
    private CapturedOutput output;

    @BeforeEach
    void member(CapturedOutput output) {
        this.member = UUID.randomUUID();
        this.output = output;
        secrets.clear();
        secrets.add(TestInstagram.APP_SECRET);
    }

    /** "No token appears in the logs of the tests": checked after every test of this class. */
    @AfterEach
    void noSecretInTheLogs() {
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
        assertThat(output.getAll()).doesNotContain("IGlong").doesNotContain("IGshort").doesNotContain("IGrefreshed");
    }

    @Test
    void aBusinessAccountIsConnectedWithItsTokenEncryptedAndItsExpiry() throws Exception {
        String state = connect(member, "fr");
        String code = code(TestInstagram.business("sfax.auto"));

        String body = callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("fr"))
                .andExpect(jsonPath("$.connection.network").value("INSTAGRAM"))
                .andExpect(jsonPath("$.connection.status").value("CONNECTED"))
                .andExpect(jsonPath("$.connection.displayName").value("@sfax.auto"))
                .andExpect(jsonPath("$.connection.pictureUrl").value("https://scontent.example.test/ig/sfax.auto.jpg"))
                .andExpect(jsonPath("$.connection.connectedAt").value(clock.instant().toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("IG").doesNotContain("token");

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT external_id, external_user_id, display_name, token_ciphertext, token_expires_at, status
                FROM social_connections WHERE user_id = ? AND network = 'INSTAGRAM'
                """, member);
        assertThat(row.get("external_id")).isEqualTo(TestInstagram.accountId(code));
        assertThat(row.get("external_user_id")).isEqualTo(TestInstagram.scopedId(code));
        assertThat(row.get("display_name")).isEqualTo("@sfax.auto");
        assertThat(row.get("status")).isEqualTo("CONNECTED");
        assertThat(((Timestamp) row.get("token_expires_at")).toInstant())
                .isEqualTo(clock.instant().plusSeconds(TestInstagram.EXPIRES_IN));
        byte[] ciphertext = (byte[]) row.get("token_ciphertext");
        assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1)).doesNotContain("IGlong");
        assertThat(decrypt(member)).isEqualTo(TestInstagram.longToken(code));

        connections(member)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].network").value("INSTAGRAM"))
                .andExpect(jsonPath("$[0].displayName").value("@sfax.auto"));
    }

    @Test
    void theAuthorizeUrlNamesTheInstagramAppTheRedirectUriTheStateAndThePermissions() throws Exception {
        String url = authorizeUrl(member, "ar");
        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();

        assertThat(uri.getScheme() + "://" + uri.getHost() + uri.getPath())
                .isEqualTo("https://www.instagram.example.test/oauth/authorize");
        assertThat(uri.getQueryParams().getFirst("client_id")).isEqualTo(TestInstagram.APP_ID);
        assertThat(decode(uri.getQueryParams().getFirst("redirect_uri")))
                .isEqualTo(FRONT_BASE_URL + "/oauth/instagram/callback");
        assertThat(uri.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(decode(uri.getQueryParams().getFirst("scope")))
                .isEqualTo("instagram_business_basic,instagram_business_content_publish");
        assertThat(uri.getQueryParams().getFirst("state")).hasSize(43);
        assertThat(url).doesNotContain(TestInstagram.APP_SECRET);
        assertThat(jdbc.queryForObject("SELECT network || ':' || lang FROM oauth_states WHERE user_id = ?",
                String.class, member)).isEqualTo("INSTAGRAM:ar");
    }

    @Test
    void aCreatorAccountIsConnectedToo() throws Exception {
        String state = connect(member, "en");
        // An answer with the token at the top level and the permissions as an array, and the suffix of the redirect
        String code = code(TestInstagram.account("amira.deco", "MEDIA_CREATOR", TestInstagram.ALL_PERMISSIONS, true));

        callback(member, Map.of("code", code + "#_", "state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("en"))
                .andExpect(jsonPath("$.connection.displayName").value("@amira.deco"));
    }

    @Test
    void aPersonalAccountIsRefusedAndNothingIsSaved() throws Exception {
        String state = connect(member, "fr");
        String code = code(TestInstagram.account("perso.123", "PERSONAL", TestInstagram.ALL_PERMISSIONS, false));

        callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:instagram-not-professional"))
                .andExpect(jsonPath("$.lang").value("fr"));
        connections(member).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aCancellationOnInstagramIsReportedWithTheLanguage() throws Exception {
        String state = connect(member, "ar");

        callback(member, Map.of("error", "access_denied", "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-cancelled"))
                .andExpect(jsonPath("$.detail").value("The connection was cancelled on Instagram."))
                .andExpect(jsonPath("$.lang").value("ar"));
        connections(member).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aMissingPublishingPermissionIsReported() throws Exception {
        String state = connect(member, "fr");
        String code = code(TestInstagram.account("sfax.auto", "BUSINESS", Set.of("instagram_business_basic"),
                false));

        callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-permission-missing"))
                .andExpect(jsonPath("$.lang").value("fr"));
        connections(member).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aStateIsBoundToItsNetworkAndUsedOnce() throws Exception {
        String code = code(TestInstagram.business("sfax.auto"));
        // A Facebook state does not end an Instagram connection
        String facebookState = UriComponentsBuilder.fromUriString(json.readTree(mockMvc.perform(
                        post("/social/facebook/connect").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andReturn().getResponse().getContentAsString()).get("authorizeUrl").asString()).build()
                .getQueryParams().getFirst("state");
        secrets.add(facebookState);
        callback(member, Map.of("code", code, "state", facebookState))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"))
                .andExpect(jsonPath("$.lang").doesNotExist());

        String state = connect(member, "fr");
        callback(UUID.randomUUID(), Map.of("code", code, "state", state)).andExpect(status().isBadRequest());
        callback(member, Map.of("code", code, "state", state)).andExpect(status().isOk());
        callback(member, Map.of("code", code, "state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"));

        String expired = connect(member, "fr");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        callback(member, Map.of("code", code, "state", expired))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:oauth-state-invalid"));
    }

    @Test
    void aCodeRefusedByInstagramIsABadGateway() throws Exception {
        String state = connect(member, "en");

        callback(member, Map.of("code", code(TestInstagram.refusedCode()), "state", state))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:instagram-unavailable"))
                .andExpect(jsonPath("$.lang").value("en"));
    }

    @Test
    void instagramIsConnectedWithoutAFacebookPageAndDisconnectedAlone() throws Exception {
        callback(member, Map.of("code", code(TestInstagram.business("sfax.auto")), "state", connect(member, "fr")))
                .andExpect(status().isOk());
        connections(member)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].network").value("INSTAGRAM"));

        connectFacebookPage(member);
        connections(member)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].network").value("FACEBOOK"))
                .andExpect(jsonPath("$[1].network").value("INSTAGRAM"));

        mockMvc.perform(delete("/social/instagram").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isNoContent());
        connections(member)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].network").value("FACEBOOK"));
        // Nothing to disconnect is not an error
        mockMvc.perform(delete("/social/instagram").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aTokenThatExpiresSoonIsRefreshedAndOneThatDoesNotIsLeftAlone() throws Exception {
        String soon = connectAccount(member, "sfax.auto");
        UUID later = UUID.randomUUID();
        clock.advance(Duration.ofDays(20));
        String notYet = connectAccount(later, "amira.deco");
        Instant laterExpiry = expiry(later);

        // The first token now expires in 5 days, the second in 25
        clock.advance(Duration.ofDays(35));
        refresher.refreshExpiring();

        assertThat(TestInstagram.refreshed()).contains(soon).doesNotContain(notYet);
        assertThat(expiry(member)).isEqualTo(clock.instant().plusSeconds(TestInstagram.EXPIRES_IN));
        assertThat(decrypt(member)).startsWith("IGrefreshed").endsWith(soon);
        assertThat(connectionStatus(member)).isEqualTo("CONNECTED");
        assertThat(expiry(later)).isEqualTo(laterExpiry);

        // Refreshed already: the next run leaves it alone
        refresher.refreshExpiring();
        assertThat(TestInstagram.refreshed().stream().filter(soon::equals)).hasSize(1);
    }

    @Test
    void aTokenWhoseAccessWasRemovedNeedsAReconnection() throws Exception {
        String code = connectAccount(member, "sfax.auto");
        TestInstagram.revoke(code);
        clock.advance(Duration.ofDays(55));

        refresher.refreshExpiring();

        assertThat(connectionStatus(member)).isEqualTo("NEEDS_RECONNECT");
        connections(member).andExpect(jsonPath("$[0].status").value("NEEDS_RECONNECT"));

        // A new connection clears it
        callback(member, Map.of("code", code(TestInstagram.business("sfax.auto")), "state", connect(member, "fr")))
                .andExpect(status().isOk());
        connections(member).andExpect(jsonPath("$[0].status").value("CONNECTED"));
    }

    @Test
    void anExpiredTokenNeedsAReconnectionEvenWhenInstagramIsDown() throws Exception {
        UUID down = UUID.randomUUID();
        connectAccount(member, "sfax.auto");
        String downCode = connectAccount(down, "amira.deco");
        TestInstagram.unavailable(downCode);
        Instant downExpiry = expiry(down);

        clock.advance(Duration.ofDays(61));
        refresher.refreshExpiring();

        assertThat(connectionStatus(member)).isEqualTo("NEEDS_RECONNECT");
        // Expired too: not refreshable any more either
        assertThat(connectionStatus(down)).isEqualTo("NEEDS_RECONNECT");
        assertThat(expiry(down)).isEqualTo(downExpiry);
    }

    @Test
    void instagramDownLeavesTheTokenForTheNextRun() throws Exception {
        String code = connectAccount(member, "sfax.auto");
        TestInstagram.unavailable(code);
        Instant before = expiry(member);
        clock.advance(Duration.ofDays(55));

        refresher.refreshExpiring();

        assertThat(connectionStatus(member)).isEqualTo("CONNECTED");
        assertThat(expiry(member)).isEqualTo(before);
        assertThat(decrypt(member)).isEqualTo(TestInstagram.longToken(code));
    }

    @Test
    void facebookPagesHaveNoExpiryAndAreNeverRefreshed() throws Exception {
        connectFacebookPage(member);

        assertThat(jdbc.queryForObject("SELECT token_expires_at FROM social_connections WHERE user_id = ?",
                Timestamp.class, member)).isNull();
    }

    @Test
    void everyEndpointNeedsAnAccessToken() throws Exception {
        mockMvc.perform(post("/social/instagram/connect")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/social/instagram/callback").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/social/instagram")).andExpect(status().isUnauthorized());
    }

    /** Connects an Instagram Business account through the API; returns its OAuth code. */
    private String connectAccount(UUID userId, String username) throws Exception {
        String code = code(TestInstagram.business(username));
        callback(userId, Map.of("code", code, "state", connect(userId, "fr"))).andExpect(status().isOk());
        return code;
    }

    private void connectFacebookPage(UUID userId) throws Exception {
        String body = mockMvc.perform(post("/social/facebook/connect")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andReturn().getResponse().getContentAsString();
        String state = UriComponentsBuilder.fromUriString(json.readTree(body).get("authorizeUrl").asString()).build()
                .getQueryParams().getFirst("state");
        String code = TestGraph.person(TestGraph.ALL_PERMISSIONS,
                List.of(new TestGraph.Page("3001", "Sfax Auto Occasion", "Car dealership")));
        secrets.add(state);
        secrets.add(code);
        mockMvc.perform(post("/social/facebook/callback")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", code, "state", state))))
                .andExpect(status().isOk());
    }

    private String code(String code) {
        secrets.add(code);
        return code;
    }

    private Instant expiry(UUID userId) {
        return jdbc.queryForObject("SELECT token_expires_at FROM social_connections WHERE user_id = ? "
                + "AND network = 'INSTAGRAM'", Timestamp.class, userId).toInstant();
    }

    private String connectionStatus(UUID userId) {
        return jdbc.queryForObject("SELECT status FROM social_connections WHERE user_id = ? AND network = 'INSTAGRAM'",
                String.class, userId);
    }

    private String decrypt(UUID userId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT token_ciphertext, key_version FROM social_connections "
                + "WHERE user_id = ? AND network = 'INSTAGRAM'", userId);
        return cipher.decrypt((byte[]) row.get("token_ciphertext"), ((Number) row.get("key_version")).intValue(),
                SocialConnectionRepository.associatedData(userId, Network.INSTAGRAM));
    }

    private String authorizeUrl(UUID userId, String lang) throws Exception {
        String body = mockMvc.perform(post("/social/instagram/connect")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lang\":\"" + lang + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("authorizeUrl").asString();
    }

    /** Starts a connection and returns its state, as Instagram would send it back. */
    private String connect(UUID userId, String lang) throws Exception {
        String state = UriComponentsBuilder.fromUriString(authorizeUrl(userId, lang)).build().getQueryParams()
                .getFirst("state");
        secrets.add(state);
        return state;
    }

    private ResultActions callback(UUID userId, Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/social/instagram/callback")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions connections(UUID userId) throws Exception {
        ResultActions result = mockMvc.perform(get("/social/connections")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isOk());
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain("IG").doesNotContain("token");
        return result;
    }

    private static String bearer(UUID userId) {
        return "Bearer " + TestJwks.token(userId);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
