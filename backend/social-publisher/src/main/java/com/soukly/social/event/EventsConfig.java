package com.soukly.social.event;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.soukly.common.event.ListingEvents;
import com.soukly.common.event.UserEvents;

/**
 * Errors of the event consumers (user events, SOC-01; listing events, SOC-04): a failed message is retried
 * {@link EventsProperties#retries()} times, then published to the DLQ of its topic ({@code soukly.user-events.dlq} or
 * {@code soukly.listing-events.dlq}) with the cause in the {@code kafka_dlt-*} headers, and the consumer moves on. A
 * message that does not follow the contract goes to the DLQ at once. Boot applies this error handler to the listener
 * container factory, shared by both consumers.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
class EventsConfig {

    @Bean
    DefaultErrorHandler eventsErrorHandler(KafkaTemplate<Object, Object> kafka, EventsProperties properties) {
        // Partition -1: the producer chooses it (each DLQ has a single partition)
        DeadLetterPublishingRecoverer dlq = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(deadLetterTopic(record.topic()), -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(dlq,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.retries()));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    static String deadLetterTopic(String topic) {
        return ListingEvents.TOPIC.equals(topic) ? ListingEvents.DLQ_TOPIC : UserEvents.DLQ_TOPIC;
    }
}
