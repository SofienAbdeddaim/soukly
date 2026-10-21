package com.soukly.listing.contact;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.listing.listing.Listing;
import com.soukly.listing.listing.ListingGoneException;
import com.soukly.listing.listing.ListingNotFoundException;
import com.soukly.listing.listing.ListingService;
import com.soukly.listing.listing.OwnListingException;
import com.soukly.listing.seller.SellerDirectory;
import com.soukly.listing.seller.SellerUnavailableException;

/**
 * "Show the number" of a listing page (CATA-03): reserved to members, limited to {@link ContactProperties#maxListings()}
 * distinct listings per member over a sliding window, and recorded in {@code contact_reveals}. The number is read from
 * user-service at each request, never stored or logged.
 */
@Service
@EnableConfigurationProperties(ContactProperties.class)
public class ContactService {

    private final ListingService listings;
    private final SellerDirectory sellers;
    private final ContactRevealRepository reveals;
    private final ContactProperties properties;
    private final Clock clock;

    ContactService(ListingService listings, SellerDirectory sellers, ContactRevealRepository reveals,
            ContactProperties properties, Clock clock) {
        this.listings = listings;
        this.sellers = sellers;
        this.reveals = reveals;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return the phone number of the seller, E.164
     * @throws ListingNotFoundException      unknown listing, or a draft
     * @throws OwnListingException           a listing of {@code viewerId}
     * @throws ListingGoneException          a listing that is not online any more, or whose seller is not active
     * @throws ContactLimitReachedException  too many other listings within the window
     * @throws SellerUnavailableException    user-service did not answer
     */
    @Transactional
    public String reveal(UUID viewerId, UUID listingId) {
        Listing listing = listings.contactable(viewerId, listingId);
        reveals.lockViewer(viewerId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant since = now.minus(properties.window());
        if (!reveals.revealedSince(viewerId, listingId, since)
                && reveals.countListingsSince(viewerId, since) >= properties.maxListings()) {
            Instant freed = reveals.firstToLeaveSince(viewerId, since).orElse(now).plus(properties.window());
            throw new ContactLimitReachedException(atLeastOneSecond(Duration.between(now, freed)));
        }
        String phone = sellers.phone(listing.ownerId()).orElseThrow(() -> new ListingGoneException(listing));
        reveals.insert(listingId, viewerId, now);
        return phone;
    }

    private static Duration atLeastOneSecond(Duration duration) {
        long seconds = (duration.toMillis() + 999) / 1000;
        return Duration.ofSeconds(Math.max(1, seconds));
    }
}
