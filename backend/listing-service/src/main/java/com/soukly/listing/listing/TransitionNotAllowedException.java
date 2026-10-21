package com.soukly.listing.listing;

/** The listing is not in a status the action starts from ({@code 409}), for example already deactivated. */
public class TransitionNotAllowedException extends RuntimeException {

    private final ListingAction action;
    private final ListingStatus status;

    public TransitionNotAllowedException(ListingAction action, ListingStatus status) {
        super("Transition not allowed", null, false, false);
        this.action = action;
        this.status = status;
    }

    public ListingAction action() {
        return action;
    }

    public ListingStatus status() {
        return status;
    }
}
