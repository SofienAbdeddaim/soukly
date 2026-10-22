package com.soukly.social.instagram;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Connection of an Instagram professional account (SOC-02), for the member of the access token. */
@RestController
class InstagramController {

    private final InstagramConnector connector;

    InstagramController(InstagramConnector connector) {
        this.connector = connector;
    }

    /** {@code {lang}} optional: the language to come back to after Instagram ({@code fr} by default). */
    @PostMapping("/social/instagram/connect")
    Map<String, String> connect(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) ConnectRequest body) {
        return Map.of("authorizeUrl", connector.start(userId(jwt), body == null ? null : body.lang()));
    }

    @PostMapping("/social/instagram/callback")
    InstagramCallbackResult callback(@AuthenticationPrincipal Jwt jwt, @RequestBody CallbackRequest body) {
        return connector.callback(userId(jwt), body.code(), body.state(), body.error());
    }

    @DeleteMapping("/social/instagram")
    ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
        connector.disconnect(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record ConnectRequest(String lang) {
    }

    /** What the callback route read from the redirect of Instagram: {@code code} and {@code state}, or {@code error}. */
    record CallbackRequest(String code, String state, String error) {

        @Override
        public String toString() {
            return "CallbackRequest[error=" + error + "]";
        }
    }
}
