package com.soukly.social.connection;

/** The member has no connection of this network ({@code 404 connection-not-found}). */
public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException() {
        super("Connection not found", null, false, false);
    }
}
