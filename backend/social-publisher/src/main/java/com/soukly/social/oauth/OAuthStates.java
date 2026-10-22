package com.soukly.social.oauth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.soukly.social.connection.Network;

/**
 * The OAuth states shared by every network (SOC-01, SOC-02): 32 random bytes, stored hashed with the member, the
 * network, the language and an expiry, and consumed by the first callback that names them.
 */
@Service
public class OAuthStates {

    static final Set<String> LANGUAGES = Set.of("fr", "ar", "en");
    static final String DEFAULT_LANG = "fr";

    private static final int STATE_BYTES = 32;

    private final OAuthStateRepository states;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    OAuthStates(OAuthStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    /** A new single-use state for this member and network; an unknown language falls back to French. */
    public String create(UUID userId, Network network, String lang, Duration ttl) {
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(state, userId, network, normalize(lang), clock.instant().plus(ttl));
        return state;
    }

    /**
     * Consumes the state.
     *
     * @return the language of the page that started the connection
     * @throws OAuthFailedException {@code oauth-state-invalid}: missing, unknown, expired, used, or another member's
     */
    public String consume(UUID userId, Network network, String state) {
        String lang = state == null || state.isBlank() ? null
                : states.consume(state, userId, network, clock.instant()).orElse(null);
        if (lang == null) {
            throw new OAuthFailedException(OAuthFailure.STATE_INVALID, network, null);
        }
        return lang;
    }

    /** Forgets the connections in progress of a member, on every network. */
    public void forget(UUID userId) {
        states.deleteAll(userId);
    }

    static String normalize(String lang) {
        return lang != null && LANGUAGES.contains(lang) ? lang : DEFAULT_LANG;
    }
}
