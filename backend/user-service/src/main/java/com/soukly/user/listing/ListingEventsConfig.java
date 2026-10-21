package com.soukly.user.listing;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.soukly.common.event.ListingEvents;

/**
 * Errors of the listing events consumer: a failed message is retried {@link ListingEventsProperties#retries()} times,
 * then published to {@code soukly.listing-events.dlq} with the cause in the {@code kafka_dlt-*} headers, and the
 * consumer moves on. A message that does not follow the contract goes to the DLQ at once. Boot applies this error
 * handler to the listener container factory.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ListingEventsProperties.class)
class ListingEventsConfig {

    @Bean
    DefaultErrorHandler listingEventsErrorHandler(KafkaTemplate<Object, Object> kafka,
            ListingEventsProperties properties) {
        // Partition -1: the producer chooses it (the DLQ has a single partition)
        DeadLetterPublishingRecoverer dlq = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(ListingEvents.DLQ_TOPIC, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(dlq,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.retries()));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
