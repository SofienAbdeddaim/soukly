package com.soukly.social.instagram;

/**
 * An Instagram professional account the login granted, read from {@code /me/assigned_instagram_accounts}.
 *
 * @param id         id of the Instagram professional account, used to publish (SOC-05)
 * @param pictureUrl profile picture, or {@code null}
 */
public record InstagramAccount(String id, String username, String pictureUrl) {
}
