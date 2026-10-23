package com.soukly.common.event;

/**
 * Data of {@code social.connection.needs-reconnect} (SOC-06).
 *
 * @param network     {@code FACEBOOK} or {@code INSTAGRAM}
 * @param accountName public name of the connected account: the name of the Facebook Page, or {@code @username} of the
 *                    Instagram account; {@code null} when unknown
 */
public record SocialConnectionNeedsReconnectData(String network, String accountName) {
}
