package com.soukly.social.facebook;

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
}
