package com.soukly.social.connection;

/** Social networks a member can connect: a Facebook Page (SOC-01), an Instagram professional account (SOC-02). */
public enum Network {
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram");

    private final String displayName;

    Network(String displayName) {
        this.displayName = displayName;
    }

    /** The name of the network in English, for the details of the problems. */
    public String displayName() {
        return displayName;
    }
}
