package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.soukly.social.connection.Network;
import com.soukly.social.connection.NewConnection;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestJwks;
import com.soukly.social.support.TestReferentials;

/**
 * The posts on "My listings" (SOC-06): {@code GET /social/posts}, "Try again", and the relaunch of the posts blocked
 * while a network was to reconnect. Rows are written directly: the publication itself is tested by SOC-04 and SOC-05.
 */
class PostFollowUpApiTests extends IntegrationTest {

    @Autowired
    private SocialConnectionRepository connections;

    private UUID member;

    @BeforeEach
    void member() {
        member = UUID.randomUUID();
    }

    @Test
    void theBadgesOfAListingPublishedOnFacebookAndPendingOnInstagram() throws Exception {
        connect(Network.FACEBOOK);
        connect(Network.INSTAGRAM);
        UUID listing = UUID.randomUUID();
        seed(listing, Network.FACEBOOK, "PUBLISHED", null, 0, 0);
        jdbc.update("UPDATE social_posts SET permalink = 'https://www.facebook.com/1/posts/2' WHERE listing_id = ?",
                listing);
        seed(listing, Network.INSTAGRAM, "PENDING", null, 0, 0);

        mockMvc.perform(as(get("/social/posts").param("listingIds", listing.toString())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.listings.length()").value(1))
                .andExpect(jsonPath("$.listings[0].listingId").value(listing.toString()))
                .andExpect(jsonPath("$.listings[0].posts[0].network").value("FACEBOOK"))
                .andExpect(jsonPath("$.listings[0].posts[0].badge").value("PUBLISHED"))
                .andExpect(jsonPath("$.listings[0].posts[0].permalink").value("https://www.facebook.com/1/posts/2"))
                .andExpect(jsonPath("$.listings[0].posts[1].network").value("INSTAGRAM"))
                .andExpect(jsonPath("$.listings[0].posts[1].badge").value("PENDING"))
                .andExpect(jsonPath("$.listings[0].posts[1].permalink").doesNotExist());
    }

    @Test
    void theListingsOfAnotherMemberAndThoseWithoutPostAreLeftOut() throws Exception {
        UUID other = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        insert(theirs, other, Network.FACEBOOK, "PUBLISHED", null, 0, 0);
        UUID mine = UUID.randomUUID();
        UUID notShared = UUID.randomUUID();
        seed(mine, Network.FACEBOOK, "SKIPPED", "not-connected", 0, 0);

        mockMvc.perform(as(get("/social/posts").param("listingIds", theirs + "," + mine + "," + notShared)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings.length()").value(0));
    }

    @Test
    void fiftyListingsAtMostAndValidIds() throws Exception {
        String ids = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));

        mockMvc.perform(as(get("/social/posts").param("listingIds", ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("listing-ids.too-many"));
        mockMvc.perform(as(get("/social/posts").param("listingIds", "nope")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/social/posts").param("listingIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFailureAfterEveryAttemptCanBeTriedAgain() throws Exception {
        connect(Network.INSTAGRAM);
        UUID listing = online();
        seed(listing, Network.INSTAGRAM, "FAILED", "instagram-unavailable", 6, 0);

        mockMvc.perform(as(get("/social/posts").param("listingIds", listing.toString())))
                .andExpect(jsonPath("$.listings[0].posts[0].badge").value("FAILED"))
                .andExpect(jsonPath("$.listings[0].posts[0].reason").value("RETRIES_EXHAUSTED"))
                .andExpect(jsonPath("$.listings[0].posts[0].retryable").value(true))
                .andExpect(jsonPath("$.listings[0].posts[0].manualRetriesLeft").value(3));

        mockMvc.perform(as(post("/social/posts/" + listing + "/INSTAGRAM/retry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badge").value("PENDING"))
                .andExpect(jsonPath("$.manualRetriesLeft").value(2));

        Map<String, Object> row = row(listing, Network.INSTAGRAM);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 0)
                .containsEntry("manual_retries", 1);
        assertThat(((Timestamp) row.get("next_attempt_at")).toInstant()).isEqualTo(clock.instant());
    }

    @Test
    void threeManualAttemptsAtMost() throws Exception {
        connect(Network.INSTAGRAM);
        UUID listing = online();
        seed(listing, Network.INSTAGRAM, "FAILED", "meta-1", 6, 3);

        mockMvc.perform(as(post("/social/posts/" + listing + "/instagram/retry")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:retry-limit-reached"));
        assertThat(row(listing, Network.INSTAGRAM)).containsEntry("status", "FAILED");
    }

    @Test
    void aListingNoLongerOnlineIsNeverQueuedAgain() throws Exception {
        connect(Network.FACEBOOK);
        UUID expired = UUID.randomUUID();
        TestReferentials.listing(expired, false);
        seed(expired, Network.FACEBOOK, "FAILED", "meta-324", 1, 0);

        mockMvc.perform(as(post("/social/posts/" + expired + "/FACEBOOK/retry")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-online"));
        assertThat(row(expired, Network.FACEBOOK)).containsEntry("status", "FAILED").containsEntry("manual_retries",
                0);
    }

    @Test
    void aPostToReconnectOrPublishedIsNotRetriedAndAnotherMembersPostIsNotFound() throws Exception {
        connect(Network.FACEBOOK);
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ?", member);
        UUID blocked = online();
        seed(blocked, Network.FACEBOOK, "SKIPPED", "needs-reconnect", 0, 0);
        UUID published = online();
        seed(published, Network.FACEBOOK, "PUBLISHED", null, 0, 0);
        UUID theirs = online();
        insert(theirs, UUID.randomUUID(), Network.FACEBOOK, "FAILED", "meta-1", 6, 0);

        mockMvc.perform(as(get("/social/posts").param("listingIds", blocked.toString())))
                .andExpect(jsonPath("$.listings[0].posts[0].badge").value("TO_RECONNECT"))
                .andExpect(jsonPath("$.listings[0].posts[0].retryable").value(false));
        mockMvc.perform(as(post("/social/posts/" + blocked + "/FACEBOOK/retry")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:not-retryable"));
        mockMvc.perform(as(post("/social/posts/" + published + "/FACEBOOK/retry")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:not-retryable"));
        mockMvc.perform(as(post("/social/posts/" + theirs + "/FACEBOOK/retry")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:post-not-found"));
    }

    @Test
    void afterAReconnectionTheBlockedListingsStillOnlineAreRelaunched() throws Exception {
        connect(Network.FACEBOOK);
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ?", member);
        UUID first = online();
        UUID second = online();
        UUID third = online();
        seed(first, Network.FACEBOOK, "SKIPPED", "needs-reconnect", 0, 0);
        seed(second, Network.FACEBOOK, "SKIPPED", "needs-reconnect", 0, 0);
        seed(third, Network.FACEBOOK, "FAILED", "needs-reconnect", 1, 0);
        UUID expired = UUID.randomUUID();
        TestReferentials.listing(expired, false);
        seed(expired, Network.FACEBOOK, "SKIPPED", "needs-reconnect", 0, 0);
        UUID tooOld = online();
        seed(tooOld, Network.FACEBOOK, "SKIPPED", "needs-reconnect", 0, 0);
        jdbc.update("UPDATE social_posts SET created_at = ? WHERE listing_id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(31))), tooOld);
        UUID failedOtherwise = online();
        seed(failedOtherwise, Network.FACEBOOK, "FAILED", "meta-324", 1, 0);

        relaunch(Network.FACEBOOK).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:network-not-connected"));

        clock.advance(Duration.ofMinutes(5));
        connect(Network.FACEBOOK);

        mockMvc.perform(as(get("/social/posts/blocked").param("network", "FACEBOOK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.network").value("FACEBOOK"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.listings[0].title").value("Peugeot 208 Allure"));
        relaunch(Network.FACEBOOK).andExpect(status().isOk()).andExpect(jsonPath("$.relaunched").value(3));

        for (UUID relaunched : new UUID[] { first, second, third }) {
            assertThat(row(relaunched, Network.FACEBOOK)).containsEntry("status", "PENDING")
                    .containsEntry("attempts", 0).containsEntry("manual_retries", 0);
        }
        assertThat(row(expired, Network.FACEBOOK)).containsEntry("status", "SKIPPED");
        assertThat(row(tooOld, Network.FACEBOOK)).containsEntry("status", "SKIPPED");
        assertThat(row(failedOtherwise, Network.FACEBOOK)).containsEntry("status", "FAILED");
        mockMvc.perform(as(get("/social/posts/blocked").param("network", "facebook")))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void declinedTheBlockedPostsBecomeFailuresToTryOneByOne() throws Exception {
        connect(Network.INSTAGRAM);
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ?", member);
        UUID listing = online();
        seed(listing, Network.INSTAGRAM, "SKIPPED", "needs-reconnect", 0, 0);
        clock.advance(Duration.ofMinutes(5));
        connect(Network.INSTAGRAM);

        mockMvc.perform(as(get("/social/posts").param("listingIds", listing.toString())))
                .andExpect(jsonPath("$.listings[0].posts[0].badge").value("FAILED"))
                .andExpect(jsonPath("$.listings[0].posts[0].reason").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.listings[0].posts[0].retryable").value(true));
        mockMvc.perform(as(post("/social/posts/" + listing + "/INSTAGRAM/retry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badge").value("PENDING"));
    }

    @Test
    void aListingServiceThatDoesNotAnswerChangesNothing() throws Exception {
        connect(Network.FACEBOOK);
        UUID listing = online();
        seed(listing, Network.FACEBOOK, "FAILED", "meta-1", 6, 0);
        TestReferentials.down(true);
        try {
            mockMvc.perform(as(post("/social/posts/" + listing + "/FACEBOOK/retry")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-service-unavailable"));
        } finally {
            TestReferentials.down(false);
        }
        assertThat(row(listing, Network.FACEBOOK)).containsEntry("status", "FAILED");
    }

    private void connect(Network network) {
        connections.save(member, new NewConnection(network, "account-" + member, "person-" + member,
                network == Network.FACEBOOK ? "Sfax Auto" : "@sfax.auto", null, "token-" + member, null));
    }

    private UUID online() {
        UUID listing = UUID.randomUUID();
        TestReferentials.listing(listing, true);
        return listing;
    }

    private void seed(UUID listing, Network network, String status, String code, int attempts, int manualRetries) {
        insert(listing, member, network, status, code, attempts, manualRetries);
    }

    private void insert(UUID listing, UUID user, Network network, String status, String code, int attempts,
            int manualRetries) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO social_posts (id, listing_id, user_id, network, status, content, attempts, last_error_code,
                    manual_retries, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, '{"title":"Peugeot 208 Allure"}'::jsonb, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), listing, user, network.name(), status, attempts, code, manualRetries, now,
                now);
    }

    private Map<String, Object> row(UUID listing, Network network) {
        return jdbc.queryForMap("SELECT * FROM social_posts WHERE listing_id = ? AND network = ?", listing,
                network.name());
    }

    private ResultActions relaunch(Network network) throws Exception {
        return mockMvc.perform(as(post("/social/posts/relaunch")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"network\":\"" + network + "\"}"));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(member));
    }
}
