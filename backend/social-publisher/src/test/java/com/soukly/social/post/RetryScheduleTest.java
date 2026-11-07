package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class RetryScheduleTest {

    private final RetrySchedule schedule = new RetrySchedule(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6)));

    @Test
    void eachFailureWaitsLongerThenTheRetriesAreUsedUp() {
        assertThat(schedule.delayAfter(1)).contains(Duration.ofMinutes(1));
        assertThat(schedule.delayAfter(2)).contains(Duration.ofMinutes(5));
        assertThat(schedule.delayAfter(3)).contains(Duration.ofMinutes(30));
        assertThat(schedule.delayAfter(4)).contains(Duration.ofHours(2));
        assertThat(schedule.delayAfter(5)).contains(Duration.ofHours(6));
        assertThat(schedule.delayAfter(6)).isEmpty();
    }

    @Test
    void anEmptyScheduleNeverRetries() {
        assertThat(new RetrySchedule(List.of()).delayAfter(1)).isEmpty();
    }
}
