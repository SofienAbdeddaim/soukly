package com.soukly.listing.listing;

/**
 * Social networks a draft can be shared to when it is published (SOC-03). listing-service does not know whether the
 * seller connected them: social-publisher skips a network that is not connected, and records why.
 */
public enum ShareNetwork {
    FACEBOOK,
    INSTAGRAM
}
