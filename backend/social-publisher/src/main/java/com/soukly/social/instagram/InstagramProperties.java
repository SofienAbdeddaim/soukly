package com.soukly.social.instagram;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Instagram API with Instagram Login (SOC-02), bound from {@code soukly.social.instagram.*}. The Instagram product of
 * the Meta app has its own app id and secret, distinct from those of Facebook Login.
 *
 * @param appId               {@code INSTAGRAM_APP_ID}; empty: Instagram is not configured in this environment
 * @param appSecret           {@code INSTAGRAM_APP_SECRET}; never logged or returned
 * @param graphVersion        version of the Instagram Graph API, for example {@code v26.0}
 * @param graphBaseUrl        origin of the Instagram Graph API: profile, long-lived token and refresh
 * @param apiBaseUrl          origin of the exchange of the code (a local fake in the tests, like the two others)
 * @param dialogBaseUrl       origin of the authorization dialog
 * @param frontBaseUrl        origin of the front end, where the callback route lives
 * @param scopes              permissions asked in the authorization dialog
 * @param requiredPermissions permissions without which the connection is refused ({@code oauth-permission-missing})
 * @param stateTtl            validity of an OAuth state
 * @param refreshBefore       a token that expires within this delay is refreshed by the daily task
 */
@ConfigurationProperties("soukly.social.instagram")
public record InstagramProperties(String appId, String appSecret, @DefaultValue("v26.0") String graphVersion,
        @DefaultValue("https://graph.instagram.com") URI graphBaseUrl,
        @DefaultValue("https://api.instagram.com") URI apiBaseUrl,
        @DefaultValue("https://www.instagram.com") URI dialogBaseUrl,
        @DefaultValue("http://localhost:4200") URI frontBaseUrl,
        @DefaultValue({ "instagram_business_basic", "instagram_business_content_publish" }) List<String> scopes,
        @DefaultValue({ "instagram_business_basic", "instagram_business_content_publish" })
        List<String> requiredPermissions,
        @DefaultValue("10m") Duration stateTtl, @DefaultValue("10d") Duration refreshBefore,
        @DefaultValue("3s") Duration connectTimeout, @DefaultValue("10s") Duration readTimeout) {

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    /** The single redirect URI declared at Meta: no language prefix, like Facebook (SOC-01). */
    public String redirectUri() {
        String origin = frontBaseUrl.toString();
        return (origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin) + "/oauth/instagram/callback";
    }

    /** The secret is never part of the string form of the properties. */
    @Override
    public String toString() {
        return "InstagramProperties[appId=" + appId + ", graphVersion=" + graphVersion + ", graphBaseUrl="
                + graphBaseUrl + "]";
    }
}
