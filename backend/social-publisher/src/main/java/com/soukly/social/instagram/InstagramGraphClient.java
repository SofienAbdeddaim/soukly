package com.soukly.social.instagram;

import java.net.URI;
import java.util.List;

import com.soukly.social.graph.GraphApiException;

/**
 * The calls of social-publisher to Instagram (SOC-02, SOC-05): Instagram API with Facebook Login for Business (the
 * "Unified login" of new Meta apps), its connection and its content publishing (containers, carousels, publication),
 * all on the Graph API of Facebook. Simulated in the tests: the real API is never called by an automated test. Every
 * failure is a {@link GraphApiException} whose message never contains a token, the app secret or the requested URL.
 */
public interface InstagramGraphClient {

    /**
     * Exchanges the code of the login dialog for the access token of the configuration: a business integration
     * system-user token that does not expire, valid for the Instagram accounts the person granted.
     */
    String exchangeCode(String code, String redirectUri);

    /** The Instagram professional accounts the token was granted ({@code /me/assigned_instagram_accounts}). */
    List<InstagramAccount> assignedAccounts(String accessToken);

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
}
