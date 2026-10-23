package com.soukly.social.connection;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.common.event.SocialConnectionNeedsReconnectData;
import com.soukly.common.event.SocialEventEnvelope;
import com.soukly.common.event.SocialEvents;
import com.soukly.social.outbox.Outbox;

/**
 * A connection the network refused (an invalid token at a publication, SOC-04 and SOC-05, or a refresh that failed,
 * SOC-02) goes to {@code NEEDS_RECONNECT}. At that change, and at most once per connection within
 * {@link ReconnectNoticeProperties#interval()}, {@code social.connection.needs-reconnect} is written to the outbox in
 * the same transaction (SOC-06): user-service sends the email. social-publisher never sends an email itself.
 */
@Component
@EnableConfigurationProperties(ReconnectNoticeProperties.class)
public class ConnectionHealth {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHealth.class);

    private final SocialConnectionRepository connections;
    private final Outbox outbox;
    private final ReconnectNoticeProperties properties;
    private final Clock clock;

    ConnectionHealth(SocialConnectionRepository connections, Outbox outbox, ReconnectNoticeProperties properties,
            Clock clock) {
        this.connections = connections;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    /** Joins the transaction of the post or of the token that showed it, when there is one. */
    @Transactional
    public void needsReconnect(UUID connectionId) {
        connections.markNeedsReconnect(connectionId, properties.interval()).ifPresent(change -> {
            if (!change.sendEmail()) {
                log.info("{} connection {} to reconnect; member told less than {} ago, no new email",
                        change.network().displayName(), connectionId, properties.interval());
                return;
            }
            outbox.write(new SocialEventEnvelope<>(UUID.randomUUID(), SocialEvents.CONNECTION_NEEDS_RECONNECT,
                    SocialEvents.SCHEMA_VERSION, clock.instant(), change.userId(),
                    new SocialConnectionNeedsReconnectData(change.network().name(), change.displayName())));
            log.info("{} connection {} to reconnect; the member is told by email", change.network().displayName(),
                    connectionId);
        });
    }
}
