package com.soukly.listing.listing;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.soukly.listing.photo.PhotoView;

/**
 * Body of the listing endpoints. Empty fields are present with {@code null}, so the front end can tell them apart
 * from unknown members.
 *
 * @param price  whole dinars (TND)
 * @param photos READY photos, cover first (ANN-02)
 */
public record ListingResponse(UUID id, ListingStatus status, long version, ListingKind kind, String categoryCode,
        String title, String description, PriceMode priceMode, Long price, boolean negotiable,
        String governorateCode, String delegationCode, Map<String, Object> attributes, List<Photo> photos,
        List<String> missingFields, List<String> warnings, Instant createdAt, Instant updatedAt) {

    static ListingResponse of(ListingView view) {
        Listing listing = view.listing();
        ListingContent content = listing.content();
        return new ListingResponse(listing.id(), listing.status(), listing.version(), content.kind(),
                content.categoryCode(), content.title(), content.description(), content.priceMode(),
                content.price(), content.negotiable(), content.governorateCode(), content.delegationCode(),
                content.attributes(), view.photos().stream().map(Photo::of).toList(), view.missingFields(),
                view.warnings(), listing.createdAt(), listing.updatedAt());
    }

    /** A photo of the listing; the status is left out, since only READY photos are listed. */
    public record Photo(UUID id, URI url, URI thumbnailUrl, int width, int height, int position) {

        static Photo of(PhotoView photo) {
            return new Photo(photo.id(), photo.url(), photo.thumbnailUrl(), photo.width(), photo.height(),
                    photo.position());
        }
    }
}
