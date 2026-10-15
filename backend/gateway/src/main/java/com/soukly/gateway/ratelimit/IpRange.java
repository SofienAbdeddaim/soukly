package com.soukly.gateway.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * An IP address ({@code 10.0.0.5}) or a CIDR range ({@code 172.16.0.0/12}, {@code fd00::/8}), IPv4 or IPv6.
 * Only IP literals are accepted, never host names.
 */
final class IpRange {

    private final byte[] network;
    private final int prefixLength;

    private IpRange(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    static IpRange parse(String value) {
        int slash = value.indexOf('/');
        byte[] address = toBytes(slash < 0 ? value : value.substring(0, slash));
        int prefix = slash < 0 ? address.length * 8 : Integer.parseInt(value.substring(slash + 1));
        if (prefix < 0 || prefix > address.length * 8) {
            throw new IllegalArgumentException("Invalid prefix length in " + value);
        }
        return new IpRange(address, prefix);
    }

    boolean contains(String ip) {
        byte[] candidate;
        try {
            candidate = toBytes(ip);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    private static byte[] toBytes(String ip) {
        String literal = ip.trim();
        boolean isLiteral = literal.contains(":") || literal.matches("[0-9.]+");
        if (literal.isEmpty() || !isLiteral) {
            throw new IllegalArgumentException("Not an IP literal: " + ip);
        }
        try {
            // An IP literal is parsed without any DNS lookup.
            return InetAddress.getByName(literal).getAddress();
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Not an IP literal: " + ip, ex);
        }
    }
}
