package com.soukly.social.oauth;

import com.soukly.social.connection.Network;

/** The app id or secret of this network is not set in this environment: {@code {network}-not-configured}. */
public class NetworkNotConfiguredException extends RuntimeException {

    private final Network network;

    public NetworkNotConfiguredException(Network network) {
        super(null, null, false, false);
        this.network = network;
    }

    public Network network() {
        return network;
    }
}
