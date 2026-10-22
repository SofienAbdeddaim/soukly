package com.soukly.social.connection;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.social.web.FieldError;
import com.soukly.social.web.InvalidRequestException;

/**
 * The networks the member connected, never a token (SOC-01), and their preference "tick by default for every new
 * listing" (SOC-03).
 */
@RestController
class ConnectionsController {

    private final SocialConnectionRepository connections;

    ConnectionsController(SocialConnectionRepository connections) {
        this.connections = connections;
    }

    @GetMapping("/social/connections")
    List<ConnectionView> connections(@AuthenticationPrincipal Jwt jwt) {
        return connections.findAll(UUID.fromString(jwt.getSubject()));
    }

    /**
     * {@code {network}} is {@code facebook} or {@code instagram}; the body is {@code {"autoShare": true|false}}.
     *
     * @throws ConnectionNotFoundException the member has no connection of this network, or an unknown network
     * @throws InvalidRequestException     {@code autoShare} missing or not a boolean
     */
    @PatchMapping("/social/connections/{network}")
    ConnectionView updatePreference(@AuthenticationPrincipal Jwt jwt, @PathVariable String network,
            @RequestBody(required = false) PreferenceRequest body) {
        Network parsed = parse(network);
        if (body == null || body.autoShare() == null) {
            throw new InvalidRequestException(List.of(new FieldError("autoShare", "auto-share.required")));
        }
        return connections.updateAutoShare(UUID.fromString(jwt.getSubject()), parsed, body.autoShare())
                .orElseThrow(ConnectionNotFoundException::new);
    }

    private static Network parse(String network) {
        try {
            return Network.valueOf(network.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConnectionNotFoundException();
        }
    }

    record PreferenceRequest(Boolean autoShare) {
    }
}
