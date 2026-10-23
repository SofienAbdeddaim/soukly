package com.soukly.social.instagram;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.soukly.social.graph.GraphApiException;

/**
 * The calls of social-publisher to Instagram (SOC-02, SOC-05): Instagram API with Instagram Login, its connection and
 * its content publishing (containers, carousels, publication). Simulated in the tests: the
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
     * Creates the container of a post of one photo ({@code POST /{ig-user-id}/media}, {@code image_url} and
     * {@code caption}): Instagram fetches the JPEG from its public URL.
     *
     * @return the container id
     */
    String createImageContainer(String accountId, String token, URI imageUrl, String caption);

    /** Creates the container of one photo of a carousel ({@code is_carousel_item=true}, without caption). */
    String createCarouselItem(String accountId, String token, URI imageUrl);

    /** Creates the container of a carousel ({@code media_type=CAROUSEL}) of 2 to 10 item containers, in order. */
    String createCarousel(String accountId, String token, List<String> children, String caption);

    /**
     * {@code status_code} of a container: {@code IN_PROGRESS}, {@code FINISHED} (ready to publish), {@code ERROR},
     * {@code EXPIRED} (not published within 24 hours) or {@code PUBLISHED}.
     */
    String containerStatus(String containerId, String token);

    /**
     * Publishes a container ({@code POST /{ig-user-id}/media_publish}).
     *
     * @return the id of the published media
     */
    String publish(String accountId, String token, String containerId);

    /** {@code permalink} of a published media. */
    String permalink(String mediaId, String token);

    /** Publications of the account through the API over the moving window ({@code content_publishing_limit}). */
    PublishingLimit publishingLimit(String accountId, String token);

    /**
     * @param usage posts published through the API within the window
     * @param total posts allowed within the window ({@code config.quota_total})
     */
    record PublishingLimit(int usage, int total) {

        public boolean reached() {
            return usage >= total;
        }
    }

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
