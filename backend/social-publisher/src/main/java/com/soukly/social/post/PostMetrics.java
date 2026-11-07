package com.soukly.social.post;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.soukly.social.connection.Network;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Metrics of the posts (SOC-04): the counter {@code social.posts{network,status}}, increased each time a post enters a
 * status, and the timer {@code social.posts.publish{network,outcome}}, the duration of a publication (photos, post and
 * permalink). Exposed on {@code /actuator/metrics}.
 */
@Component
class PostMetrics {

    private final MeterRegistry registry;

    PostMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void entered(Network network, PostStatus status) {
        entered(network, status, 1);
    }

    void entered(Network network, PostStatus status, int posts) {
        registry.counter("social.posts", "network", network.name(), "status", status.name()).increment(posts);
    }

    /** @param outcome {@code published}, {@code retry}, {@code failed} */
    void published(Network network, String outcome, Duration duration) {
        Timer.builder("social.posts.publish")
                .description("Duration of a publication on a social network")
                .tag("network", network.name())
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }
}
