package com.soukly.social.facebook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.soukly.social.connection.ConnectionView;

/**
 * Answer of {@code POST /social/facebook/callback}: either the connected Page, or the Pages to choose from.
 *
 * @param lang the language of the page that started the connection, to go back to it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallbackResult(String lang, ConnectionView connection, List<PageCandidate> pendingSelection) {
}
