package com.soukly.social.connection;

public enum ConnectionStatus {
    CONNECTED,
    /** Meta refused the token (expired, password changed, permission removed): set by SOC-04, cleared by a new connection. */
    NEEDS_RECONNECT
}
