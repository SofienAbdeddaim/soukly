package com.soukly.listing.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * Redpanda of the tests: the image of docker-compose.yml (SETUP-02), started once for every test class.
 */
public final class TestRedpanda {

    @SuppressWarnings("resource") // stopped by Testcontainers (Ryuk) when the JVM exits
    private static final RedpandaContainer CONTAINER = new RedpandaContainer("redpandadata/redpanda:v26.2.4");

    private static volatile boolean started;

    private TestRedpanda() {
    }

    public static synchronized String bootstrapServers() {
        if (!started) {
            CONTAINER.start();
            started = true;
        }
        return CONTAINER.getBootstrapServers();
    }

    /**
     * Reads {@code topic} from its beginning until the records accepted by {@code filter} satisfy {@code done}, or
     * the timeout; returns the accepted records in the order of each partition.
     */
    public static List<ConsumerRecord<String, String>> read(String topic,
            Predicate<ConsumerRecord<String, String>> filter, Predicate<List<ConsumerRecord<String, String>>> done,
            Duration timeout) {
        List<ConsumerRecord<String, String>> accepted = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            // Every partition from its beginning, without a consumer group (no rebalance to wait for)
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(partition -> new TopicPartition(topic, partition.partition())).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline) && !done.test(accepted)) {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    if (filter.test(record)) {
                        accepted.add(record);
                    }
                });
            }
        }
        return accepted;
    }

    /** Records of {@code topic} with this key, read until {@code expected} of them arrived or the timeout. */
    public static List<ConsumerRecord<String, String>> readKey(String topic, String key, int expected,
            Duration timeout) {
        return read(topic, record -> key.equals(record.key()), records -> records.size() >= expected, timeout);
    }
}
