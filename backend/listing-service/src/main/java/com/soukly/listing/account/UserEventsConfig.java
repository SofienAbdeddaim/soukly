package com.soukly.listing.account;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.soukly.common.event.UserEvents;

/**
 * Errors of the user events consumer: a failed message is retried {@link UserEventsProperties#retries()} times, then
 * published to {@code soukly.user-events.dlq} with the cause in the {@code kafka_dlt-*} headers, and the consumer
 * moves on. A message that does not follow the contract goes to the DLQ at once. Boot applies this error handler to
 * the listener container factory.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserEventsProperties.class)
class UserEventsConfig {

    @Bean
    DefaultErrorHandler userEventsErrorHandler(KafkaTemplate<Object, Object> kafka, UserEventsProperties properties) {
        // Partition -1: the producer chooses it (the DLQ has a single partition)
        DeadLetterPublishingRecoverer dlq = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(UserEvents.DLQ_TOPIC, -1));
        DefaultErrorHandler handler = new DefaultErrorHandler(dlq,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.retries()));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
