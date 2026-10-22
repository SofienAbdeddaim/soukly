package com.soukly.social.instagram;

import java.util.Set;

import com.soukly.social.graph.GraphApiException;

/**
 * The calls of social-publisher to Instagram (SOC-02): Instagram API with Instagram Login. Simulated in the tests: the
 * real API is never called by an automated test. Every failure is a {@link GraphApiException} whose message never
 * contains a token, the app secret or the requested URL.
 */
public interface InstagramGraphClient {

    /** Exchanges the code of the authorization dialog for a short-lived token (about one hour). */
    ShortLivedToken exchangeCode(String code, String redirectUri);

    /** Exchanges a short-lived token for a long-lived one (about 60 days). */
    LongLivedToken exchangeForLongLived(String shortLivedToken);

    /**
     * Refreshes a long-lived token that is at least 24 hours old and not expired: a new token valid about 60 days.
     *
     * @throws GraphApiException with a 4xx status when Instagram refuses it (expired, or the access was removed)
     */
    LongLivedToken refresh(String longLivedToken);

    /** The account the token belongs to. */
    InstagramProfile profile(String accessToken);

    /**
     * @param userId      Instagram-scoped id of the account
     * @param permissions permissions granted in the dialog; {@code null} when Instagram did not list them
     */
    record ShortLivedToken(String accessToken, String userId, Set<String> permissions) {

        @Override
        public String toString() {
            return "ShortLivedToken[userId=" + userId + "]";
        }
    }

    /** @param expiresInSeconds validity of the token from now */
    record LongLivedToken(String accessToken, long expiresInSeconds) {

        @Override
        public String toString() {
            return "LongLivedToken[expiresInSeconds=" + expiresInSeconds + "]";
        }
    }
}
