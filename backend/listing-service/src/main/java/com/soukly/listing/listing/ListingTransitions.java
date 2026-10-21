package com.soukly.listing.listing;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The only place that says which action a listing allows in which status (ANN-05):
 *
 * <table>
 * <caption>Transitions</caption>
 * <tr><th>Action</th><th>From</th><th>To</th></tr>
 * <tr><td>EDIT</td><td>DRAFT, PUBLISHED, EXPIRED, INACTIVE</td><td>the same status</td></tr>
 * <tr><td>PUBLISH</td><td>DRAFT</td><td>PUBLISHED</td></tr>
 * <tr><td>DEACTIVATE</td><td>PUBLISHED</td><td>INACTIVE</td></tr>
 * <tr><td>RENEW</td><td>EXPIRED, INACTIVE, PUBLISHED expiring within the reminder period</td><td>PUBLISHED</td></tr>
 * <tr><td>DELETE</td><td>every status but DELETED</td><td>DELETED (a draft is erased instead)</td></tr>
 * </table>
 *
 * <p>Callers check it with the listing locked, in the transaction of the change, so that two requests in parallel
 * never both pass it.
 */
@Component
public class ListingTransitions {

    private final Duration renewWindow;

    @Autowired
    ListingTransitions(PublicationProperties publication) {
        this(publication.reminderBefore());
    }

    /** @param renewWindow a published listing can be renewed when it expires within this period */
    ListingTransitions(Duration renewWindow) {
        this.renewWindow = renewWindow;
    }

    /**
     * The status of {@code listing} after {@code action}.
     *
     * @throws AlreadyPublishedException     {@code PUBLISH} of a listing that was published already (ANN-03)
     * @throws RenewNotAllowedException      {@code RENEW} of a listing that is not allowed to (ANN-03)
     * @throws TransitionNotAllowedException any other action that the status does not allow
     */
    public ListingStatus target(Listing listing, ListingAction action, Instant now) {
        ListingStatus from = listing.status();
        if (from == ListingStatus.DELETED) {
            throw new TransitionNotAllowedException(action, from);
        }
        return switch (action) {
            case EDIT -> from;
            case PUBLISH -> {
                if (from != ListingStatus.DRAFT) {
                    throw new AlreadyPublishedException();
                }
                yield ListingStatus.PUBLISHED;
            }
            case DEACTIVATE -> {
                if (from != ListingStatus.PUBLISHED) {
                    throw new TransitionNotAllowedException(action, from);
                }
                yield ListingStatus.INACTIVE;
            }
            case RENEW -> {
                if (!renewable(listing, now)) {
                    throw new RenewNotAllowedException();
                }
                yield ListingStatus.PUBLISHED;
            }
            case DELETE -> ListingStatus.DELETED;
        };
    }

    /** Whether {@code action} is allowed now, without throwing. */
    public boolean allows(Listing listing, ListingAction action, Instant now) {
        try {
            target(listing, action, now);
            return true;
        } catch (TransitionNotAllowedException | AlreadyPublishedException | RenewNotAllowedException e) {
            return false;
        }
    }

    private boolean renewable(Listing listing, Instant now) {
        return switch (listing.status()) {
            case EXPIRED, INACTIVE -> true;
            case PUBLISHED -> listing.expiresAt().isBefore(now.plus(renewWindow));
            case DRAFT, DELETED -> false;
        };
    }
}
