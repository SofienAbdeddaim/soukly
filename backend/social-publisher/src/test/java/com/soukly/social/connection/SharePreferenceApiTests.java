package com.soukly.social.connection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.soukly.social.support.IntegrationTest;
import com.soukly.social.support.TestJwks;

/**
 * The preference "tick by default for every new listing" of each connected network (SOC-03): on at the connection,
 * changed by its member only, kept by a new connection of the same network.
 */
class SharePreferenceApiTests extends IntegrationTest {

    @Autowired
    private SocialConnectionRepository connections;

    private UUID member;

    @BeforeEach
    void member() {
        member = UUID.randomUUID();
    }

    @Test
    void aNewConnectionIsTickedByDefault() throws Exception {
        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");
        connect(member, Network.INSTAGRAM, "@sfax.auto");

        list(member)
                .andExpect(jsonPath("$[0].network").value("FACEBOOK"))
                .andExpect(jsonPath("$[0].autoShare").value(true))
                .andExpect(jsonPath("$[1].network").value("INSTAGRAM"))
                .andExpect(jsonPath("$[1].autoShare").value(true));
    }

    @Test
    void theMemberTurnsThePreferenceOffAndOnForOneNetwork() throws Exception {
        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");
        connect(member, Network.INSTAGRAM, "@sfax.auto");

        update(member, "instagram", "{\"autoShare\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.network").value("INSTAGRAM"))
                .andExpect(jsonPath("$.displayName").value("@sfax.auto"))
                .andExpect(jsonPath("$.autoShare").value(false));
        list(member)
                .andExpect(jsonPath("$[0].autoShare").value(true))
                .andExpect(jsonPath("$[1].autoShare").value(false));

        update(member, "INSTAGRAM", "{\"autoShare\":true}").andExpect(jsonPath("$.autoShare").value(true));
    }

    @Test
    void aNewConnectionOfTheSameNetworkKeepsThePreference() throws Exception {
        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");
        update(member, "facebook", "{\"autoShare\":false}").andExpect(status().isOk());
        jdbc.update("UPDATE social_connections SET status = 'NEEDS_RECONNECT' WHERE user_id = ?", member);

        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");

        list(member)
                .andExpect(jsonPath("$[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$[0].autoShare").value(false));
    }

    @Test
    void aConnectionMadeAgainAfterADisconnectionIsTickedAgain() throws Exception {
        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");
        update(member, "facebook", "{\"autoShare\":false}").andExpect(status().isOk());
        connections.delete(member, Network.FACEBOOK);

        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");

        list(member).andExpect(jsonPath("$[0].autoShare").value(true));
    }

    @Test
    void onlyTheMembersOwnConnectionCanBeChanged() throws Exception {
        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");
        UUID other = UUID.randomUUID();

        update(other, "facebook", "{\"autoShare\":false}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:connection-not-found"));
        list(member).andExpect(jsonPath("$[0].autoShare").value(true));
    }

    @Test
    void anUnknownNetworkOrAMissingValueIsRefused() throws Exception {
        connect(member, Network.FACEBOOK, "Sfax Auto Occasion");

        update(member, "tiktok", "{\"autoShare\":false}").andExpect(status().isNotFound());
        update(member, "facebook", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("autoShare"))
                .andExpect(jsonPath("$.errors[0].code").value("auto-share.required"));
        mockMvc.perform(patch("/social/connections/facebook")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"autoShare\":false}"))
                .andExpect(status().isUnauthorized());
    }

    private void connect(UUID userId, Network network, String name) {
        connections.save(userId, new NewConnection(network, "ext-" + UUID.randomUUID(), null, name, null,
                "token-" + UUID.randomUUID(), null));
    }

    private ResultActions update(UUID userId, String network, String body) throws Exception {
        return mockMvc.perform(patch("/social/connections/{network}", network)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions list(UUID userId) throws Exception {
        return mockMvc.perform(get("/social/connections")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwks.token(userId)))
                .andExpect(status().isOk());
    }
}
