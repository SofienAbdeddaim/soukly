package com.soukly.social.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphErrorTest {

    @Test
    void theMessageOfMetaIsCleanedOfTokensAndPersonalData() {
        GraphError error = new GraphError(190, 463, false, "OAuthException",
                "Error validating access token EAAJZCabcdefghijklmnopqrstuvwxyz0123 for "
                        + "https://graph.facebook.com/v26.0/me?access_token=secret123&fields=id\n"
                        + "contact seller@example.tn or +216 20 123 456");

        assertThat(error.message()).doesNotContain("EAAJZC", "secret123", "seller@example.tn", "20 123 456", "\n")
                .contains("access_token=[…]", "Error validating access token");
        assertThat(error.codes()).isEqualTo("190/463");
    }

    @Test
    void aLongMessageIsCut() {
        GraphError error = new GraphError(100, null, false, null, "x".repeat(500));

        assertThat(error.message()).hasSize(GraphError.MESSAGE_MAX + 1);
        assertThat(error.codes()).isEqualTo("100");
    }
}
