package com.soukly.social.instagram;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.social.connection.ExpiringToken;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.graph.GraphApiException;

/**
 * Refreshes, daily, the Instagram tokens that expire within {@code refresh-before} (10 days by default) (SOC-02). A
 * token Instagram refuses (expired, or the access was removed) puts the connection in {@code NEEDS_RECONNECT}; when
 * Instagram does not answer, the token is tried again the next day.
 *
 * <p>One run at a time: in this instance through a flag, across instances through the row lock ({@code FOR UPDATE
 * SKIP LOCKED}) each token is refreshed under, in its own transaction.
 */
@Component
@EnableScheduling
public class InstagramTokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(InstagramTokenRefresher.class);

    private final SocialConnectionRepository connections;
    private final InstagramGraphClient instagram;
    private final InstagramProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    InstagramTokenRefresher(SocialConnectionRepository connections, InstagramGraphClient instagram,
            InstagramProperties properties, TransactionTemplate transactions, Clock clock) {
        this.connections = connections;
        this.instagram = instagram;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** What a run did. */
    public record Result(int refreshed, int needsReconnect, int failed) {
    }

    @Scheduled(fixedDelayString = "${soukly.social.instagram.refresh-interval:P1D}",
            initialDelayString = "${soukly.social.instagram.refresh-initial-delay:PT10M}")
    void scheduledRun() {
        refreshExpiring();
    }

    /** Refreshes every token that expires soon; does nothing while another run of this instance is in progress. */
    public Result refreshExpiring() {
        if (!properties.configured() || !running.compareAndSet(false, true)) {
            return new Result(0, 0, 0);
        }
        try {
            Instant before = clock.instant().plus(properties.refreshBefore());
            int refreshed = 0;
            int needsReconnect = 0;
            int failed = 0;
            ExpiringToken last = null;
            while (true) {
                ExpiringToken after = last;
                Optional<Outcome> outcome = transactions.execute(status -> connections
                        .lockNextExpiring(Network.INSTAGRAM, before, after).map(this::refresh));
                if (outcome == null || outcome.isEmpty()) {
                    break;
                }
                last = outcome.get().token();
                switch (outcome.get().kind()) {
                    case REFRESHED -> refreshed++;
                    case NEEDS_RECONNECT -> needsReconnect++;
                    case FAILED -> failed++;
                }
            }
            if (refreshed + needsReconnect + failed > 0) {
                log.info("Instagram tokens: {} refreshed, {} to reconnect, {} to try again", refreshed,
                        needsReconnect, failed);
            }
            return new Result(refreshed, needsReconnect, failed);
        } finally {
            running.set(false);
        }
    }

    private Outcome refresh(ExpiringToken token) {
        if (!token.expiresAt().isAfter(clock.instant())) {
            // An expired token cannot be refreshed any more
            connections.markNeedsReconnect(token.id());
            log.info("Instagram token of {} expired: to reconnect", token.userId());
            return new Outcome(token, Kind.NEEDS_RECONNECT);
        }
        try {
            InstagramGraphClient.LongLivedToken refreshed = instagram.refresh(token.token());
            connections.updateToken(token, refreshed.accessToken(),
                    clock.instant().plusSeconds(refreshed.expiresInSeconds()));
            return new Outcome(token, Kind.REFRESHED);
        } catch (GraphApiException e) {
            HttpStatus status = HttpStatus.resolve(e.status());
            if (status != null && status.is4xxClientError() && status != HttpStatus.TOO_MANY_REQUESTS) {
                connections.markNeedsReconnect(token.id());
                log.info("Instagram token of {} refused ({}): to reconnect", token.userId(), e.getMessage());
                return new Outcome(token, Kind.NEEDS_RECONNECT);
            }
            log.warn("Instagram token of {} not refreshed, tried again next run: {}", token.userId(), e.getMessage());
            return new Outcome(token, Kind.FAILED);
        }
    }

    private enum Kind {
        REFRESHED, NEEDS_RECONNECT, FAILED
    }

    private record Outcome(ExpiringToken token, Kind kind) {
    }
}
