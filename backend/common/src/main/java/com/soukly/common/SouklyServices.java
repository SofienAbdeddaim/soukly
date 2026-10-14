package com.soukly.common;

/**
 * Names of the Soukly backend services, as used in {@code spring.application.name}.
 */
public final class SouklyServices {

    public static final String GATEWAY = "gateway";
    public static final String USER_SERVICE = "user-service";
    public static final String LISTING_SERVICE = "listing-service";
    public static final String SOCIAL_PUBLISHER = "social-publisher";

    private SouklyServices() {
    }
}
