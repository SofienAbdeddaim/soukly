package com.soukly.social.instagram;

import java.util.List;
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
 * Connection of an Instagram professional account (SOC-02) with Facebook Login for Business, the "Unified login" of
 * new Meta apps: the Facebook dialog with the saved Instagram configuration ({@code config_id}), where the person
 * selects the account; the exchange of the code on the server only, for a system-user token that does not expire;
 * the reading of the granted account and the disconnection. One account per member: connecting another one replaces
 * it.
 *
 * <p>Same guarantees as Facebook (SOC-01): the OAuth state comes from {@link OAuthStates}, tokens never leave the
 * service and never reach a log.
 */
@Service
@EnableConfigurationProperties(InstagramProperties.class)
public class InstagramConnector {

    /** {@code error} of the redirect when the person cancels in the dialog. */
    static final String ACCESS_DENIED = "access_denied";

    private static final Logger log = LoggerFactory.getLogger(InstagramConnector.class);

    private final InstagramProperties properties;
    private final InstagramGraphClient instagram;
    private final OAuthStates states;
    private final SocialConnectionRepository connections;

    InstagramConnector(InstagramProperties properties, InstagramGraphClient instagram, OAuthStates states,
            SocialConnectionRepository connections) {
        this.properties = properties;
        this.instagram = instagram;
        this.states = states;
        this.connections = connections;
    }

    /**
     * Starts a connection: the URL of the login dialog, with a new single-use state. The configuration carries the
     * permissions and the choice of the account, so no {@code scope}; it gives a system-user token, which comes only
     * with the authorization code grant ({@code override_default_response_type}).
     */
    public String start(UUID userId, String lang) {
        if (!properties.configured()) {
            throw new NetworkNotConfiguredException(Network.INSTAGRAM);
        }
        String state = states.create(userId, Network.INSTAGRAM, lang, properties.stateTtl());
        return UriComponentsBuilder.fromUri(properties.dialogBaseUrl())
                .path("/" + properties.graphVersion() + "/dialog/oauth")
                .queryParam("client_id", properties.appId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("config_id", properties.loginConfigId())
                .queryParam("override_default_response_type", "true")
                .encode()
                .build()
                .toUriString();
    }

    /**
     * Ends a connection with what Meta sent back to the callback route.
     *
     * @throws OAuthFailedException the state is not valid, the member cancelled, no account or several accounts were
     *                              granted, or Meta failed
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
            String token = instagram.exchangeCode(code, properties.redirectUri());
            List<InstagramAccount> accounts = instagram.assignedAccounts(token);
            if (accounts.isEmpty()) {
                log.info("Instagram connection of {} refused: no account granted", userId);
                throw new OAuthFailedException(OAuthFailure.INSTAGRAM_NO_ACCOUNT, Network.INSTAGRAM, lang);
            }
            if (accounts.size() > 1) {
                log.info("Instagram connection of {} refused: {} accounts granted", userId, accounts.size());
                throw new OAuthFailedException(OAuthFailure.INSTAGRAM_SEVERAL_ACCOUNTS, Network.INSTAGRAM, lang);
            }
            InstagramAccount account = accounts.getFirst();
            // The token does not expire: no expiry, and nothing to refresh
            ConnectionView connection = connections.save(userId, new NewConnection(Network.INSTAGRAM, account.id(),
                    null, "@" + account.username(), account.pictureUrl(), token, null));
            log.info("Instagram account {} connected to {}", account.id(), userId);
            return new InstagramCallbackResult(lang, connection);
        } catch (GraphApiException e) {
            log.warn("Instagram connection of {} failed: {}", userId, e.getMessage());
            throw new OAuthFailedException(OAuthFailure.INSTAGRAM_UNAVAILABLE, Network.INSTAGRAM, lang);
        }
    }

    /**
     * Deletes the connection. The member can remove Soukly from the integrations of their business portfolio; the
     * token is never used again. Nothing to delete is not an error.
     */
    public void disconnect(UUID userId) {
        connections.delete(userId, Network.INSTAGRAM);
        log.info("Instagram account of {} disconnected", userId);
    }
}
