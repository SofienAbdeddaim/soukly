package com.soukly.social.outbox;

import java.time.Clock;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.common.event.SocialEvents;
import com.soukly.common.outbox.OutboxRelay;
import com.soukly.common.outbox.OutboxRepository;

/** Topics of the social events (SOC-06), and the relay of the outbox with its schedule. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfig {

    /**
     * Created at startup when missing (local and dev; replication 1, a single Redpanda node); an existing topic is left
     * as it is. On the VM, {@code infra/compose/redpanda/init-topics.sh} creates them first.
     */
    @Bean
    NewTopic socialEventsTopic(OutboxProperties properties) {
        return TopicBuilder.name(SocialEvents.TOPIC).partitions(properties.partitions()).replicas(1).build();
    }

    @Bean
    NewTopic socialEventsDlqTopic() {
        return TopicBuilder.name(SocialEvents.DLQ_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    OutboxRepository outboxRepository(JdbcClient jdbc) {
        return new OutboxRepository(jdbc);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka,
            PlatformTransactionManager transactions, OutboxProperties properties, Clock clock) {
        return new OutboxRelay(SocialEvents.TOPIC, repository, kafka, new TransactionTemplate(transactions),
                properties, clock);
    }

    @Bean
    RelaySchedule outboxRelaySchedule(OutboxRelay relay) {
        return new RelaySchedule(relay);
    }

    /**
     * Relay every second after the end of the previous run, as many batches as needed to empty the outbox; purge of
     * the sent events every hour.
     */
    static class RelaySchedule {

        private static final Logger log = LoggerFactory.getLogger(RelaySchedule.class);

        private final OutboxRelay relay;

        RelaySchedule(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${soukly.social.outbox.relay-interval:PT1S}",
                initialDelayString = "${soukly.social.outbox.relay-initial-delay:PT5S}")
        void relay() {
            try {
                relay.relayAll();
            } catch (RuntimeException e) {
                // The database is unreachable, for example: the next run tries again.
                log.warn("Outbox relay failed: {}", e.getClass().getName());
            }
        }

        @Scheduled(fixedDelayString = "${soukly.social.outbox.purge-interval:PT1H}",
                initialDelayString = "${soukly.social.outbox.purge-interval:PT1H}")
        void purge() {
            relay.purgeSent();
        }
    }
}
