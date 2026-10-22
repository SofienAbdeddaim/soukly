package com.soukly.social.instagram;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.soukly.social.connection.ConnectionView;
import com.soukly.social.connection.Network;
import com.soukly.social.connection.NewConnection;
import com.soukly.social.connection.SocialConnectionRepository;
import com.soukly.social.graph.GraphApiException;
import com.soukly.social.oauth.NetworkNotConfiguredException;
import com.soukly.social.oauth.OAuthFailedException;
import com.soukly.social.oauth.OAuthFailure;
import com.soukly.social.oauth.OAuthStates;

/**
 * Connection of an Instagram professional account (SOC-02) with Instagram Login, without a Facebook Page: the
 * authorization dialog, the exchange of the code on the server only, the long-lived token, the check of the account
 * type and the disconnection. One account per member: connecting another one replaces it.
 *
 * <p>Same guarantees as Facebook (SOC-01): the OAuth state comes from {@link OAuthStates}, tokens never leave the
 * service and never reach a log.
 */
@Service
@EnableConfigurationProperties(InstagramProperties.class)
public class InstagramConnector {

    /** {@code error} of the redirect when the person cancels on Instagram. */
    static final String ACCESS_DENIED = "access_denied";

    /** Instagram appends this to the code in the redirect; it is not part of the code. */
    private static final String CODE_SUFFIX = "#_";

    private static final Logger log = LoggerFactory.getLogger(InstagramConnector.class);

    private final InstagramProperties properties;
    private final InstagramGraphClient instagram;
    private final OAuthStates states;
    private final SocialConnectionRepository connections;
    private final Clock clock;

    InstagramConnector(InstagramProperties properties, InstagramGraphClient instagram, OAuthStates states,
            SocialConnectionRepository connections, Clock clock) {
        this.properties = properties;
        this.instagram = instagram;
        this.states = states;
        this.connections = connections;
        this.clock = clock;
    }

    /** Starts a connection: the URL of the Instagram authorization dialog, with a new single-use state. */
    public String start(UUID userId, String lang) {
        if (!properties.configured()) {
            throw new NetworkNotConfiguredException(Network.INSTAGRAM);
        }
        String state = states.create(userId, Network.INSTAGRAM, lang, properties.stateTtl());
        return UriComponentsBuilder.fromUri(properties.dialogBaseUrl())
                .path("/oauth/authorize")
                .queryParam("client_id", properties.appId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(",", properties.scopes()))
                .encode()
                .build()
                .toUriString();
    }

    /**
     * Ends a connection with what Instagram sent back to the callback route.
     *
     * @throws OAuthFailedException the state is not valid, the member cancelled, a permission is missing, the account
     *                              is a personal one, or Instagram failed
     */
    public InstagramCallbackResult callback(UUID userId, String code, String state, String error) {
        String lang = states.consume(userId, Network.INSTAGRAM, state);
        if (error != null && !error.isBlank()) {
            throw new OAuthFailedException(ACCESS_DENIED.equals(error) ? OAuthFailure.CANCELLED
                    : OAuthFailure.INSTAGRAM_UNAVAILABLE, Network.INSTAGRAM, lang);
        }
        if (code == null || code.isBlank() || !properties.configured()) {
            throw new OAuthFailedException(OAuthFailure.INSTAGRAM_UNAVAILABLE, Network.INSTAGRAM, lang);
        }
        try {
            InstagramGraphClient.ShortLivedToken shortLived = instagram.exchangeCode(stripSuffix(code),
                    properties.redirectUri());
            if (shortLived.permissions() != null
                    && !shortLived.permissions().containsAll(properties.requiredPermissions())) {
                throw new OAuthFailedException(OAuthFailure.PERMISSION_MISSING, Network.INSTAGRAM, lang);
            }
            InstagramGraphClient.LongLivedToken longLived = instagram.exchangeForLongLived(shortLived.accessToken());
            InstagramProfile profile = instagram.profile(longLived.accessToken());
            if (!profile.professional()) {
                log.info("Instagram connection of {} refused: personal account", userId);
                throw new OAuthFailedException(OAuthFailure.INSTAGRAM_NOT_PROFESSIONAL, Network.INSTAGRAM, lang);
            }
            Instant expiresAt = clock.instant().plusSeconds(longLived.expiresInSeconds());
            String accountId = profile.userId() != null ? profile.userId()
                    : shortLived.userId() != null ? shortLived.userId() : profile.id();
            ConnectionView connection = connections.save(userId, new NewConnection(Network.INSTAGRAM, accountId,
                    profile.id(), "@" + profile.username(), profile.pictureUrl(), longLived.accessToken(),
                    expiresAt));
            log.info("Instagram account {} connected to {}", accountId, userId);
            return new InstagramCallbackResult(lang, connection);
        } catch (GraphApiException e) {
            log.warn("Instagram connection of {} failed: {}", userId, e.getMessage());
            throw new OAuthFailedException(OAuthFailure.INSTAGRAM_UNAVAILABLE, Network.INSTAGRAM, lang);
        }
    }

    /**
     * Deletes the connection. Instagram Login offers no call to revoke the access: the member removes Soukly from the
     * apps of their Instagram account, and the token expires on its own. Nothing to delete is not an error.
     */
    public void disconnect(UUID userId) {
        connections.delete(userId, Network.INSTAGRAM);
        log.info("Instagram account of {} disconnected", userId);
    }

    private static String stripSuffix(String code) {
        return code.endsWith(CODE_SUFFIX) ? code.substring(0, code.length() - CODE_SUFFIX.length()) : code;
    }
}
