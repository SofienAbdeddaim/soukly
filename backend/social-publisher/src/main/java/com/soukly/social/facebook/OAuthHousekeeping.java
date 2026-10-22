package com.soukly.social.facebook;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes the expired OAuth states and Page choices, hourly: a connection never finished leaves nothing behind. */
@Component
@EnableScheduling
class OAuthHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(OAuthHousekeeping.class);

    private final OAuthStateRepository states;
    private final PendingSelectionRepository selections;
    private final Clock clock;

    OAuthHousekeeping(OAuthStateRepository states, PendingSelectionRepository selections, Clock clock) {
        this.states = states;
        this.selections = selections;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${soukly.social.facebook.purge-interval:PT1H}",
            initialDelayString = "${soukly.social.facebook.purge-initial-delay:PT5M}")
    void purgeExpired() {
        Instant now = clock.instant();
        int purged = states.deleteExpired(now) + selections.deleteExpired(now);
        if (purged > 0) {
            log.info("{} expired OAuth state(s) or Page choice(s) deleted", purged);
        }
    }
}
