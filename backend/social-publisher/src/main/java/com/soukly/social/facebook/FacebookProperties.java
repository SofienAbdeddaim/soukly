package com.soukly.social.facebook;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Facebook Login for Business and Graph API (SOC-01), bound from {@code soukly.social.facebook.*}. The permissions are
 * not asked in the dialog: they are those of the saved login configuration ({@code config_id}) of the Meta app, the
 * only way new Meta apps accept (Unified login, 2026-11).
 *
 * @param appId               {@code META_APP_ID}; empty: Facebook is not configured in this environment
 * @param appSecret           {@code META_APP_SECRET}; never logged or returned
 * @param graphVersion        {@code META_GRAPH_VERSION}, for example {@code v26.0}
 * @param graphBaseUrl        origin of the Graph API (a local fake in the tests)
 * @param dialogBaseUrl       origin of the OAuth dialog
 * @param frontBaseUrl        origin of the front end, where the callback route lives
 * @param loginConfigId       {@code META_FACEBOOK_LOGIN_CONFIG_ID}: the Facebook Login for Business configuration
 *                            (user access token, Pages); empty: Facebook is not configured in this environment
 * @param requiredPermissions permissions without which the connection is refused ({@code oauth-permission-missing})
 * @param stateTtl            validity of an OAuth state
 * @param selectionTtl        validity of a choice among several Pages
 */
@ConfigurationProperties("soukly.social.facebook")
public record FacebookProperties(String appId, String appSecret, @DefaultValue("v26.0") String graphVersion,
        @DefaultValue("https://graph.facebook.com") URI graphBaseUrl,
        @DefaultValue("https://www.facebook.com") URI dialogBaseUrl,
        @DefaultValue("http://localhost:4200") URI frontBaseUrl,
        String loginConfigId,
        @DefaultValue({ "pages_show_list", "pages_manage_posts" }) List<String> requiredPermissions,
        @DefaultValue("10m") Duration stateTtl, @DefaultValue("10m") Duration selectionTtl,
        @DefaultValue("3s") Duration connectTimeout, @DefaultValue("10s") Duration readTimeout) {

    public boolean configured() {
        return present(appId) && present(appSecret) && present(loginConfigId);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /** The single redirect URI declared at Meta: no language prefix (SOC-01). */
    public String redirectUri() {
        String origin = frontBaseUrl.toString();
        return (origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin) + "/oauth/facebook/callback";
    }

    /** The secret is never part of the string form of the properties. */
    @Override
    public String toString() {
        return "FacebookProperties[appId=" + appId + ", loginConfigId=" + loginConfigId + ", graphVersion=" + graphVersion + ", graphBaseUrl="
                + graphBaseUrl + "]";
    }
}
