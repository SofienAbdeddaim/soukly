package com.soukly.social.facebook;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.social.connection.ConnectionView;

/** Connection of a Facebook Page (SOC-01), for the member of the access token. */
@RestController
class FacebookController {

    private final FacebookConnector connector;

    FacebookController(FacebookConnector connector) {
        this.connector = connector;
    }

    /** {@code {lang}} optional: the language to come back to after Facebook ({@code fr} by default). */
    @PostMapping("/social/facebook/connect")
    Map<String, String> connect(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) ConnectRequest body) {
        return Map.of("authorizeUrl", connector.start(userId(jwt), body == null ? null : body.lang()));
    }

    @PostMapping("/social/facebook/callback")
    CallbackResult callback(@AuthenticationPrincipal Jwt jwt, @RequestBody CallbackRequest body) {
        return connector.callback(userId(jwt), body.code(), body.state(), body.error());
    }

    /** The Pages of the choice in progress, for a page reloaded during the choice; {@code 404} when there is none. */
    @GetMapping("/social/facebook/page-selection")
    Map<String, List<PageCandidate>> pageSelection(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("pendingSelection", connector.pendingPages(userId(jwt)));
    }

    @PostMapping("/social/facebook/page")
    ConnectionView choosePage(@AuthenticationPrincipal Jwt jwt, @RequestBody ChoosePageRequest body) {
        return connector.choose(userId(jwt), body.pageId() == null ? "" : body.pageId());
    }

    @DeleteMapping("/social/facebook")
    ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
        connector.disconnect(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record ConnectRequest(String lang) {
    }

    /** What the callback route read from the redirect of Facebook: {@code code} and {@code state}, or {@code error}. */
    record CallbackRequest(String code, String state, String error) {

        @Override
        public String toString() {
            return "CallbackRequest[error=" + error + "]";
        }
    }

    record ChoosePageRequest(String pageId) {
    }
}
