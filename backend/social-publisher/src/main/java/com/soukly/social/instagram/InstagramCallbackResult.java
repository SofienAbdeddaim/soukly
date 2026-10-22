package com.soukly.social.instagram;

import com.soukly.social.connection.ConnectionView;

/**
 * Answer of {@code POST /social/instagram/callback}: the connected account.
 *
 * @param lang the language of the page that started the connection, to go back to it
 */
public record InstagramCallbackResult(String lang, ConnectionView connection) {
}
