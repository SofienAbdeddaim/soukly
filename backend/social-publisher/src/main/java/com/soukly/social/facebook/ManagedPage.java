package com.soukly.social.facebook;

/**
 * A Page the person manages, read from {@code /me/accounts}, with its Page access token: kept on the server only.
 *
 * @param pictureUrl picture of the Page, or {@code null}
 */
public record ManagedPage(String id, String name, String category, String pictureUrl, String accessToken) {

    /** The token is never part of the string form. */
    @Override
    public String toString() {
        return "ManagedPage[id=" + id + ", name=" + name + "]";
    }
}
