package com.soukly.common.web;

/**
 * HTTP header names shared by the gateway and the services.
 */
public final class SouklyHeaders {

    /**
     * Correlation identifier of a request. The gateway generates it when the client sends none,
     * forwards it to the services and returns it in the response.
     */
    public static final String REQUEST_ID = "X-Request-Id";

    private SouklyHeaders() {
    }
}
