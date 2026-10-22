package com.soukly.social.connection;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /social/connections}: the networks the member connected, never a token (SOC-01). */
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
}
