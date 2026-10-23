package com.soukly.social.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.soukly.common.outbox.OutboxSettings;

/**
 * Relay of the social events outbox (SOC-06), bound from {@code soukly.social.outbox.*}; the same settings as the ones
 * of listing-service (ANN-03) and user-service (AUTH-05).
 *
 * @param relayInterval     pause between two runs of the relay
 * @param relayInitialDelay first run after the start
 * @param batchSize         events locked and sent per transaction
 * @param sendTimeout       wait for the acknowledgements of one batch; beyond, the events stay unsent
 * @param retention         sent events are deleted after this
 * @param purgeInterval     period of that deletion
 * @param partitions        partitions of the topic, when social-publisher creates it (local and dev)
 */
@ConfigurationProperties("soukly.social.outbox")
public record OutboxProperties(@DefaultValue("1s") Duration relayInterval,
        @DefaultValue("5s") Duration relayInitialDelay, @DefaultValue("100") int batchSize,
        @DefaultValue("15s") Duration sendTimeout, @DefaultValue("7d") Duration retention,
        @DefaultValue("1h") Duration purgeInterval, @DefaultValue("3") int partitions) implements OutboxSettings {
}
