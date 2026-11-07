package com.soukly.social.connection;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The email "connect again" (SOC-06), bound from {@code soukly.social.reconnect-notice.*}.
 *
 * @param interval at most one email per connection within this delay
 */
@ConfigurationProperties("soukly.social.reconnect-notice")
public record ReconnectNoticeProperties(@DefaultValue("24h") Duration interval) {
}
