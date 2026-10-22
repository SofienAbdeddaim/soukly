package com.soukly.social.facebook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.soukly.social.connection.Network;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.graph.GraphApiException;

/**
 * Removes the permissions a person granted to Soukly on Facebook, after their connection was deleted (disconnection or
 * account deletion). Best effort: a failure is logged and never blocks. The permissions stay when another connection
 * still uses them (the same person connected a Page to two Soukly accounts).
 */
@Component
public class FacebookRevocation {

    private static final Logger log = LoggerFactory.getLogger(FacebookRevocation.class);

    private final FacebookGraphClient graph;
    private final FacebookProperties properties;
    private final SocialConnectionRepository connections;

    FacebookRevocation(FacebookGraphClient graph, FacebookProperties properties,
            SocialConnectionRepository connections) {
        this.graph = graph;
        this.properties = properties;
        this.connections = connections;
    }

    public void revokeQuietly(String externalUserId) {
        if (externalUserId == null || !properties.configured()) {
            return;
        }
        if (connections.usedByAnyConnection(Network.FACEBOOK, externalUserId)) {
            log.info("Facebook permissions kept: another connection uses them");
            return;
        }
        try {
            graph.revokePermissions(externalUserId);
        } catch (GraphApiException e) {
            log.warn("Facebook permissions not revoked: {}", e.getMessage());
        }
    }
}
