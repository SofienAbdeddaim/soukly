package com.soukly.listing.search;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opaque cursors: {@code base64url(json).base64url(hmac)}. The HMAC-SHA256 signature makes a changed cursor fail;
 * the values it carries are only ever bound as SQL parameters.
 */
@Component
class SearchCursorCodec {

    private static final Logger log = LoggerFactory.getLogger(SearchCursorCodec.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    /** Far above any real cursor; refused before decoding. */
    private static final int MAX_LENGTH = 512;

    private final SecretKeySpec key;
    private final JsonMapper json;

    SearchCursorCodec(SearchProperties properties, JsonMapper json) {
        this.key = new SecretKeySpec(secret(properties.cursorSecret()), ALGORITHM);
        this.json = json;
    }

    String encode(SearchCursor cursor) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("s", cursor.sort());
        body.put("k", cursor.key());
        // Microseconds since the epoch: exactly the precision of timestamptz
        body.put("p", ChronoUnit.MICROS.between(Instant.EPOCH, cursor.publishedAt()));
        body.put("i", cursor.id().toString());
        byte[] payload = json.writeValueAsBytes(body);
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
    }

    /**
     * @throws InvalidSearchException {@code cursor.invalid}: malformed, changed, or written for another sort
     */
    SearchCursor decode(String token, ListingRankingStrategy ranking) {
        try {
            if (token.length() > MAX_LENGTH) {
                throw invalid();
            }
            int dot = token.indexOf('.');
            if (dot < 0) {
                throw invalid();
            }
            byte[] payload = DECODER.decode(token.substring(0, dot));
            byte[] signature = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                throw invalid();
            }
            JsonNode body = json.readTree(payload);
            if (!ranking.name().equals(body.path("s").asString(null))) {
                throw invalid();
            }
            JsonNode key = body.path("k");
            Number value = key.isNumber() ? key.numberValue() : null;
            ranking.bindKey(value);
            JsonNode publishedAt = body.path("p");
            if (!publishedAt.canConvertToLong()) {
                throw invalid();
            }
            Instant date = Instant.EPOCH.plus(publishedAt.longValue(), ChronoUnit.MICROS);
            return new SearchCursor(ranking.name(), value, date, UUID.fromString(body.path("i").asString("")));
        } catch (IllegalArgumentException | JacksonException | ArithmeticException e) {
            throw invalid();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static InvalidSearchException invalid() {
        return new InvalidSearchException(InvalidSearchException.INVALID_CURSOR);
    }

    private static byte[] secret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        log.info("No SEARCH_CURSOR_SECRET: the search cursors are signed with a random key until the next start");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }
}
