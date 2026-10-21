package com.soukly.listing.listing;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.soukly.listing.photo.PhotoView;
import com.soukly.listing.seller.SellerProfile;

/**
 * Body of the listing endpoints. Empty fields are present with {@code null}, so the front end can tell them apart
 * from unknown members.
 *
 * @param price       whole dinars (TND)
 * @param photos      READY photos, cover first (ANN-02)
 * @param publishedAt first publication, null for a draft (ANN-03)
 * @param expiresAt   end of visibility of a published listing, its past expiry for an expired one
 * @param seller      public profile of the seller when the owner reads a listing that is not a draft (CATA-03),
 *                    otherwise null
 */
public record ListingResponse(UUID id, ListingStatus status, long version, ListingKind kind, String categoryCode,
        String title, String description, PriceMode priceMode, Long price, boolean negotiable,
        String governorateCode, String delegationCode, Map<String, Object> attributes, List<Photo> photos,
        List<String> missingFields, List<String> warnings, Instant createdAt, Instant updatedAt,
        Instant publishedAt, Instant expiresAt, Seller seller) {

    /** The only currency of V1. */
    static final String CURRENCY = "TND";

    static ListingResponse of(ListingView view) {
        return of(view, null);
    }

    static ListingResponse of(ListingReading reading) {
        return of(reading.view(), Seller.of(reading.view().listing().ownerId(), reading.seller()));
    }

    private static ListingResponse of(ListingView view, Seller seller) {
        Listing listing = view.listing();
        ListingContent content = listing.content();
        return new ListingResponse(listing.id(), listing.status(), listing.version(), content.kind(),
                content.categoryCode(), content.title(), content.description(), content.priceMode(),
                content.price(), content.negotiable(), content.governorateCode(), content.delegationCode(),
                content.attributes(), view.photos().stream().map(Photo::of).toList(), view.missingFields(),
                view.warnings(), listing.createdAt(), listing.updatedAt(), listing.publishedAt(),
                listing.expiresAt(), seller);
    }

    /**
     * The seller as the listing page shows them (CATA-03).
     *
     * @param memberSince month of the registration, {@code 2026-03}
     */
    public record Seller(UUID id, String displayName, String memberSince) {

        static Seller of(UUID id, SellerProfile profile) {
            return profile == null ? null : new Seller(id, profile.displayName(), profile.memberSince());
        }
    }

    /** A photo of the listing; the status is left out, since only READY photos are listed. */
    public record Photo(UUID id, URI url, URI thumbnailUrl, int width, int height, int position) {

        static Photo of(PhotoView photo) {
            return new Photo(photo.id(), photo.url(), photo.thumbnailUrl(), photo.width(), photo.height(),
                    photo.position());
        }
    }
}
