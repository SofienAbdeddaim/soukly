package com.soukly.listing.referential;

public class GovernorateNotFoundException extends RuntimeException {

    public GovernorateNotFoundException(String code) {
        super("No governorate with code " + code);
    }
}
