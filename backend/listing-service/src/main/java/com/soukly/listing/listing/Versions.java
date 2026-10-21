package com.soukly.listing.listing;

/** The version of a listing as an HTTP entity tag: version 4 is {@code "4"}. */
public final class Versions {

    private Versions() {
    }

    public static String etag(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Strong comparison of {@code If-Match} (RFC 9110): {@code *} or one of the listed tags must be the current
     * version; a weak tag never matches.
     *
     * @throws IfMatchRequiredException when the header is missing or blank
     */
    public static boolean matches(String ifMatch, long version) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new IfMatchRequiredException();
        }
        String current = etag(version);
        for (String tag : ifMatch.split(",")) {
            String trimmed = tag.strip();
            if (trimmed.equals("*") || trimmed.equals(current)) {
                return true;
            }
        }
        return false;
    }
}
