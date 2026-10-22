package com.soukly.social.facebook;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Set;
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

/**
 * Connection of a Facebook Page (SOC-01): the OAuth dialog, the exchange of the code on the server only, the reading
 * of the managed Pages, the choice among several Pages and the disconnection. One Page per member: connecting another
 * one replaces it.
 *
 * <p>The OAuth state is 32 random bytes, stored hashed with the member, the language and an expiry, and consumed by the
 * first callback that names it. Tokens never leave the service, and never reach a log.
 */
@Service
@EnableConfigurationProperties(FacebookProperties.class)
public class FacebookConnector {

    static final Set<String> LANGUAGES = Set.of("fr", "ar", "en");
    static final String DEFAULT_LANG = "fr";

    /** {@code error} of the redirect when the person cancels on Facebook. */
    static final String ACCESS_DENIED = "access_denied";

    private static final Logger log = LoggerFactory.getLogger(FacebookConnector.class);
    private static final int STATE_BYTES = 32;

    private final FacebookProperties properties;
    private final FacebookGraphClient graph;
    private final OAuthStateRepository states;
    private final PendingSelectionRepository selections;
    private final SocialConnectionRepository connections;
    private final FacebookRevocation revocation;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    FacebookConnector(FacebookProperties properties, FacebookGraphClient graph, OAuthStateRepository states,
            PendingSelectionRepository selections, SocialConnectionRepository connections,
            FacebookRevocation revocation, Clock clock) {
        this.properties = properties;
        this.graph = graph;
        this.states = states;
        this.selections = selections;
        this.connections = connections;
        this.revocation = revocation;
        this.clock = clock;
    }

    /** Starts a connection: the URL of the Facebook OAuth dialog, with a new single-use state. */
    public String start(UUID userId, String lang) {
        if (!properties.configured()) {
            throw new FacebookNotConfiguredException();
        }
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(state, userId, Network.FACEBOOK, normalize(lang), clock.instant().plus(properties.stateTtl()));
        return UriComponentsBuilder.fromUri(properties.dialogBaseUrl())
                .path("/" + properties.graphVersion() + "/dialog/oauth")
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
     * Ends a connection with what Facebook sent back to the callback route.
     *
     * @throws OAuthFailedException the state is not valid, the member cancelled, a permission is missing, there is no
     *                              Page, or Facebook failed
     */
    public CallbackResult callback(UUID userId, String code, String state, String error) {
        String lang = state == null || state.isBlank() ? null
                : states.consume(state, userId, Network.FACEBOOK, clock.instant()).orElse(null);
        if (lang == null) {
            throw new OAuthFailedException(OAuthFailure.STATE_INVALID, null);
        }
        if (error != null && !error.isBlank()) {
            throw new OAuthFailedException(ACCESS_DENIED.equals(error) ? OAuthFailure.CANCELLED
                    : OAuthFailure.FACEBOOK_UNAVAILABLE, lang);
        }
        if (code == null || code.isBlank() || !properties.configured()) {
            throw new OAuthFailedException(OAuthFailure.FACEBOOK_UNAVAILABLE, lang);
        }
        try {
            String userToken = graph.exchangeForLongLived(graph.exchangeCode(code, properties.redirectUri()));
            if (!graph.grantedPermissions(userToken).containsAll(properties.requiredPermissions())) {
                throw new OAuthFailedException(OAuthFailure.PERMISSION_MISSING, lang);
            }
            String externalUserId = graph.userId(userToken);
            List<ManagedPage> pages = graph.managedPages(userToken);
            if (pages.isEmpty()) {
                throw new OAuthFailedException(OAuthFailure.NO_PAGE, lang);
            }
            if (pages.size() == 1) {
                selections.delete(userId);
                return new CallbackResult(lang, connect(userId, externalUserId, pages.getFirst()), null);
            }
            selections.save(userId, new PendingSelection(externalUserId, pages),
                    clock.instant().plus(properties.selectionTtl()));
            log.info("Facebook connection of {}: {} Pages to choose from", userId, pages.size());
            return new CallbackResult(lang, null, pages.stream().map(PageCandidate::of).toList());
        } catch (GraphApiException e) {
            log.warn("Facebook connection of {} failed: {}", userId, e.getMessage());
            throw new OAuthFailedException(OAuthFailure.FACEBOOK_UNAVAILABLE, lang);
        }
    }

    /** The Pages of the choice in progress. */
    public List<PageCandidate> pendingPages(UUID userId) {
        return selections.find(userId, clock.instant())
                .orElseThrow(PageSelectionNotFoundException::new)
                .pages().stream().map(PageCandidate::of).toList();
    }

    /** Connects the Page chosen among several. */
    public ConnectionView choose(UUID userId, String pageId) {
        PendingSelection selection = selections.find(userId, clock.instant())
                .orElseThrow(PageSelectionNotFoundException::new);
        ManagedPage page = selection.pages().stream()
                .filter(candidate -> candidate.id().equals(pageId))
                .findFirst()
                .orElseThrow(PageSelectionNotFoundException::new);
        ConnectionView connection = connect(userId, selection.externalUserId(), page);
        selections.delete(userId);
        return connection;
    }

    /** Deletes the connection, then tries to revoke the permissions at Meta. Nothing to delete is not an error. */
    public void disconnect(UUID userId) {
        selections.delete(userId);
        connections.delete(userId, Network.FACEBOOK).ifPresent(revocation::revokeQuietly);
        log.info("Facebook Page of {} disconnected", userId);
    }

    private ConnectionView connect(UUID userId, String externalUserId, ManagedPage page) {
        ConnectionView connection = connections.save(userId, new NewConnection(Network.FACEBOOK, page.id(),
                externalUserId, page.name(), page.pictureUrl(), page.accessToken()));
        log.info("Facebook Page {} connected to {}", page.id(), userId);
        return connection;
    }

    static String normalize(String lang) {
        return lang != null && LANGUAGES.contains(lang) ? lang : DEFAULT_LANG;
    }

    /** Forgets the connections in progress of a deleted account (states and Page choice). */
    public void forgetPending(UUID userId) {
        states.deleteAll(userId);
        selections.delete(userId);
    }
}
