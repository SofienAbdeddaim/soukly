package com.soukly.social.instagram;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Instagram API with Facebook Login for Business, the "Unified login" of new Meta apps (2026-11), bound from
 * {@code soukly.social.instagram.*}. The app id and secret are those of the Meta app (the same as Facebook); the
 * dialog is the Facebook one with the saved Instagram configuration ({@code config_id}), and every call goes to the
 * Graph API of Facebook.
 *
 * @param appId          {@code META_APP_ID}; empty: Instagram is not configured in this environment
 * @param appSecret      {@code META_APP_SECRET}; never logged or returned
 * @param loginConfigId  {@code META_INSTAGRAM_LOGIN_CONFIG_ID}: the Facebook Login for Business configuration
 *                       (system-user access token that never expires, Instagram accounts); empty: not configured
 * @param graphVersion   version of the Graph API, for example {@code v26.0}
 * @param graphBaseUrl   origin of the Graph API: code exchange, accounts and content publishing
 * @param dialogBaseUrl  origin of the login dialog
 * @param frontBaseUrl   origin of the front end, where the callback route lives
 * @param stateTtl       validity of an OAuth state
 */
@ConfigurationProperties("soukly.social.instagram")
public record InstagramProperties(String appId, String appSecret, String loginConfigId,
        @DefaultValue("v26.0") String graphVersion,
        @DefaultValue("https://graph.facebook.com") URI graphBaseUrl,
        @DefaultValue("https://www.facebook.com") URI dialogBaseUrl,
        @DefaultValue("http://localhost:4200") URI frontBaseUrl,
        @DefaultValue("10m") Duration stateTtl,
        @DefaultValue("3s") Duration connectTimeout, @DefaultValue("10s") Duration readTimeout) {

    public boolean configured() {
        return present(appId) && present(appSecret) && present(loginConfigId);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /** The single redirect URI declared at Meta: no language prefix, like Facebook (SOC-01). */
    public String redirectUri() {
        String origin = frontBaseUrl.toString();
        return (origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin) + "/oauth/instagram/callback";
    }

    /** The secret is never part of the string form of the properties. */
    @Override
    public String toString() {
        return "InstagramProperties[appId=" + appId + ", loginConfigId=" + loginConfigId + ", graphVersion="
                + graphVersion + ", graphBaseUrl=" + graphBaseUrl + "]";
    }
}
