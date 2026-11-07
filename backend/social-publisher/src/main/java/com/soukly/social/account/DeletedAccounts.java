package com.soukly.social.account;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.social.connection.DeletedConnection;
import com.soukly.social.event.ProcessedEvents;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.facebook.FacebookConnector;
import com.soukly.social.facebook.FacebookRevocation;
import com.soukly.social.post.PostRemoval;

/**
 * Deletes the social network connections of a deleted account (SOC-01), with its connections in progress. Before, while
 * the tokens are still there, the posts of the member are removed from the networks (SOC-04, {@link PostRemoval}). The
 * event is recorded in {@code processed_events} in the transaction that deletes the connections and the post rows: a
 * second delivery does nothing. The permissions are then revoked at Meta, after the commit and at best effort.
 */
@Service
class DeletedAccounts {

    private static final Logger log = LoggerFactory.getLogger(DeletedAccounts.class);

    private final ProcessedEvents processed;
    private final SocialConnectionRepository connections;
    private final FacebookConnector facebook;
    private final FacebookRevocation revocation;
    private final PostRemoval posts;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DeletedAccounts(ProcessedEvents processed, SocialConnectionRepository connections, FacebookConnector facebook,
            FacebookRevocation revocation, PostRemoval posts, TransactionTemplate transactions, Clock clock) {
        this.processed = processed;
        this.connections = connections;
        this.facebook = facebook;
        this.revocation = revocation;
        this.posts = posts;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void deleteConnections(UUID eventId, UUID userId) {
        if (processed.isProcessed(eventId)) {
            log.info("User event {} was processed already: connections not deleted again", eventId);
            return;
        }
        // Idempotent: a delivery after a crash finds the posts already removed, or their rows deleted
        posts.removeUser(userId);
        List<DeletedConnection> deleted = transactions.execute(status -> {
            if (!processed.markProcessed(eventId, clock.instant())) {
                log.info("User event {} was processed already: connections not deleted again", eventId);
                return List.<DeletedConnection>of();
            }
            facebook.forgetPending(userId);
            posts.forget(userId);
            List<DeletedConnection> all = connections.deleteAll(userId);
            log.info("Account {} deleted: {} social connection(s) deleted", userId, all.size());
            return all;
        });
        // Instagram offers no revocation: the member removes Soukly from the apps of their Instagram account
        deleted.stream()
                .filter(connection -> connection.network() == Network.FACEBOOK && connection.externalUserId() != null)
                .forEach(connection -> revocation.revokeQuietly(connection.externalUserId()));
    }
}
