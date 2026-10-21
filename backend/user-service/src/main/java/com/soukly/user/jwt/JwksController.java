package com.soukly.user.jwt;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public keys of the access tokens, read by the gateway. Not exposed by the gateway: only the services call it.
 */
@RestController
class JwksController {

    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final Map<String, Object> jwks;

    JwksController(JwtKeys keys) {
        // JWKSet.toJSONObject() keeps public parameters only.
        this.jwks = keys.publicKeys().toJSONObject();
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(MAX_AGE)).body(jwks);
    }
}
