package com.soukly.listing.listing;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.soukly.common.event.EventEnvelope;
import com.soukly.common.event.ListingEvents;
import com.soukly.common.event.ListingExpiryData;
import com.soukly.common.event.ListingPublishedData;
import com.soukly.listing.photo.PhotoView;

/**
 * Builds the listing events of {@code docs/events/listing-events.md}. They never carry personal data of the seller:
 * only the account id.
 */
@Component
class ListingEventFactory {

    static final String CURRENCY = "TND";

    private final String publicBaseUrl;

    ListingEventFactory(PublicationProperties properties) {
        String url = properties.publicBaseUrl().toString();
        this.publicBaseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** @param view the published listing, with its READY photos in order */
    EventEnvelope<ListingPublishedData> published(ListingView view, Instant now) {
        Listing listing = view.listing();
        ListingContent content = listing.content();
        List<URI> photoUrls = view.photos().stream().map(PhotoView::url).toList();
        ListingPublishedData data = new ListingPublishedData(content.title(), content.description(),
                content.categoryCode(), content.kind().name(), content.priceMode().name(), content.price(), CURRENCY,
                content.negotiable(), content.governorateCode(), content.delegationCode(), photoUrls.getFirst(),
                photoUrls, publicUrls(listing.id()), List.of(), listing.expiresAt());
        return envelope(ListingEvents.PUBLISHED, listing, now, data);
    }

    EventEnvelope<ListingExpiryData> renewed(Listing listing, Instant now) {
        return expiry(ListingEvents.RENEWED, listing, now);
    }

    EventEnvelope<ListingExpiryData> expiringSoon(Listing listing, Instant now) {
        return expiry(ListingEvents.EXPIRING_SOON, listing, now);
    }

    EventEnvelope<ListingExpiryData> expired(Listing listing, Instant now) {
        return expiry(ListingEvents.EXPIRED, listing, now);
    }

    private static EventEnvelope<ListingExpiryData> expiry(String type, Listing listing, Instant now) {
        return envelope(type, listing, now, new ListingExpiryData(listing.content().title(), listing.expiresAt()));
    }

    private static <T> EventEnvelope<T> envelope(String type, Listing listing, Instant now, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), type, ListingEvents.SCHEMA_VERSION, now, listing.id(),
                listing.ownerId(), data);
    }

    /** Page of the listing (route of CATA-03) in each language. */
    private ListingPublishedData.PublicUrls publicUrls(UUID id) {
        return new ListingPublishedData.PublicUrls(page("fr", id), page("ar", id), page("en", id));
    }

    private URI page(String lang, UUID id) {
        return URI.create(publicBaseUrl + "/" + lang + "/listings/" + id);
    }
}
