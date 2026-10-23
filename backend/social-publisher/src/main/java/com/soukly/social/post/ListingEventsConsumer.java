package com.soukly.social.post;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.soukly.common.event.EventEnvelope;
import com.soukly.common.event.ListingEvents;
import com.soukly.common.web.SouklyHeaders;
import com.soukly.social.caption.CaptionLanguage;
import com.soukly.social.event.InvalidEventException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code soukly.listing-events} in the group {@code social-publisher} (SOC-04): {@code listing.published}
 * records the posts to publish, {@code listing.deleted} removes them. Every other type ({@code listing.updated},
 * {@code listing.renewed}, {@code listing.deactivated}, {@code listing.expired}...) is ignored in V1. Never calls Meta
 * for a publication: the tasks of {@link FacebookPublisher} and {@link InstagramPublisher} do. A failure is retried, then the message goes to
 * {@code soukly.listing-events.dlq} ({@code EventsConfig}).
 */
@Component
class ListingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ListingEventsConsumer.class);

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE = new TypeReference<>() {
    };

    private final ListingPosts posts;
    private final JsonMapper json;

    ListingEventsConsumer(ListingPosts posts, JsonMapper json) {
        this.posts = posts;
        this.json = json;
    }

    @KafkaListener(topics = ListingEvents.TOPIC)
    void onEvent(ConsumerRecord<String, String> record) {
        EventEnvelope<JsonNode> event = parse(record);
        boolean published = ListingEvents.PUBLISHED.equals(event.type());
        if (!published && !ListingEvents.DELETED.equals(event.type())) {
            return;
        }
        if (event.schemaVersion() != ListingEvents.SCHEMA_VERSION) {
            throw new InvalidEventException("Unsupported schemaVersion " + event.schemaVersion() + " of event "
                    + event.eventId());
        }
        String requestId = requestId(record);
        MDC.put("listingId", event.listingId().toString());
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
        try {
            if (published) {
                Published data = published(event);
                posts.recordPublished(event.eventId(), event.listingId(), event.ownerId(), data.shareTo(),
                        data.content(), requestId);
            } else {
                posts.removeDeleted(event.eventId(), event.listingId());
            }
        } finally {
            MDC.remove("listingId");
            MDC.remove("requestId");
        }
    }

    private EventEnvelope<JsonNode> parse(ConsumerRecord<String, String> record) {
        EventEnvelope<JsonNode> event;
        try {
            event = record.value() == null ? null : json.readValue(record.value(), ENVELOPE);
        } catch (JacksonException e) {
            // The exception message may quote the payload: only its type is kept.
            throw new InvalidEventException("Malformed listing event at offset " + record.offset() + " of partition "
                    + record.partition() + ": " + e.getClass().getSimpleName());
        }
        if (event == null || event.eventId() == null || event.type() == null || event.listingId() == null
                || event.ownerId() == null) {
            throw new InvalidEventException("Listing event without eventId, type, listingId or ownerId at offset "
                    + record.offset() + " of partition " + record.partition());
        }
        log.debug("Listing event {} of type {}", event.eventId(), event.type());
        return event;
    }

    /** What SOC-04 reads of {@code listing.published}; anything else of the event is ignored. */
    private record Published(List<String> shareTo, PostContent content) {
    }

    private static Published published(EventEnvelope<JsonNode> event) {
        UUID eventId = event.eventId();
        JsonNode data = event.data();
        if (data == null || !data.isObject()) {
            throw new InvalidEventException("Missing data in event " + eventId);
        }
        List<String> shareTo = new ArrayList<>();
        JsonNode networks = data.get("shareTo");
        if (networks != null && networks.isArray()) {
            networks.forEach(network -> shareTo.add(network.asString()));
        }
        if (shareTo.isEmpty()) {
            return new Published(shareTo, null);
        }
        String language = text(data, "shareLanguage");
        if (language == null || CaptionLanguage.of(language).isEmpty()) {
            throw new InvalidEventException("Invalid data.shareLanguage in event " + eventId);
        }
        JsonNode publicUrls = data.get("publicUrls");
        String listingUrl = publicUrls == null ? null : text(publicUrls, language);
        List<URI> photoUrls = new ArrayList<>();
        JsonNode photos = data.get("photoUrls");
        if (photos != null && photos.isArray()) {
            photos.forEach(photo -> photoUrls.add(uri(photo.asString(), eventId)));
        }
        if (listingUrl == null || photoUrls.isEmpty()) {
            throw new InvalidEventException("Missing data.publicUrls or data.photoUrls in event " + eventId);
        }
        JsonNode price = data.get("price");
        JsonNode negotiable = data.get("negotiable");
        return new Published(shareTo, new PostContent(text(data, "title"), text(data, "description"),
                text(data, "categoryCode"), text(data, "priceMode"),
                price == null || !price.isNumber() ? null : price.asLong(),
                negotiable != null && negotiable.asBoolean(), text(data, "governorateCode"),
                text(data, "delegationCode"), photoUrls, uri(listingUrl, eventId), language, text(data, "shortRef")));
    }

    private static String text(JsonNode node, String member) {
        JsonNode value = node.get(member);
        return value == null || !value.isString() ? null : value.asString();
    }

    private static URI uri(String value, UUID eventId) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidEventException("Invalid URL in event " + eventId);
        }
    }

    private static String requestId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(SouklyHeaders.REQUEST_ID);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
