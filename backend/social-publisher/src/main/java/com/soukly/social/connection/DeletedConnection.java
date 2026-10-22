package com.soukly.social.connection;

/**
 * A connection deleted with the account of its member.
 *
 * @param externalUserId the app-scoped id of the person, to revoke the permissions; {@code null} when unknown
 */
public record DeletedConnection(Network network, String externalUserId) {
}
