package com.soukly.social.facebook;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.soukly.social.graph.GraphApiException;

/**
 * The calls of social-publisher to the Graph API of Meta (SOC-01). Simulated in the tests: the real API is never called
 * by an automated test. Every failure is a {@link GraphApiException} whose message never contains a token, the app
 * secret or the requested URL.
 */
public interface FacebookGraphClient {

    /** Exchanges the code of the OAuth dialog for a short-lived user access token. */
    String exchangeCode(String code, String redirectUri);

    /** Exchanges a short-lived user access token for a long-lived one (about 60 days). */
    String exchangeForLongLived(String userAccessToken);

    /** App-scoped id of the person. */
    String userId(String userAccessToken);

    /** Permissions the person granted to the app (status {@code granted}). */
    Set<String> grantedPermissions(String userAccessToken);

    /**
     * Pages the person manages, with their Page tokens. A Page token read with a long-lived user token does not
     * expire on its own.
     */
    List<ManagedPage> managedPages(String userAccessToken);

    /** Removes every permission the person granted to the app, with the app access token. */
    void revokePermissions(String userId);

    /**
     * Uploads a photo to the Page from its public URL without publishing it ({@code POST /{page-id}/photos} with
     * {@code published=false}), to attach it to a post (SOC-04).
     *
     * @return the id of the unpublished photo
     */
    String uploadUnpublishedPhoto(String pageId, String pageToken, URI photoUrl);

    /**
     * Publishes a post on the Page with this text and these unpublished photos, in this order
     * ({@code POST /{page-id}/feed} with {@code attached_media}).
     *
     * @return the id of the post, {@code {page-id}_{post-id}}
     */
    String createPost(String pageId, String pageToken, String message, List<String> photoIds);

    /** Address of the post on Facebook ({@code permalink_url}), or {@code null} when Meta does not give it. */
    String permalink(String postId, String pageToken);

    /** Deletes a post of the Page ({@code DELETE /{post-id}}). */
    void deletePost(String postId, String pageToken);
}
