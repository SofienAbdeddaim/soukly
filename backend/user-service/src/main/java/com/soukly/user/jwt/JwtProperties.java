package com.soukly.user.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Signing of the access tokens, bound from {@code soukly.user.jwt.*}. The key paths are file system paths, relative
 * to the working directory or absolute (strings: Spring would read them as resource locations).
 *
 * @param issuer                {@code iss} claim, checked by the gateway ({@code JWT_ISSUER})
 * @param keyId                 {@code kid} of the active key ({@code JWT_KEY_ID})
 * @param privateKeyPath        active RSA private key, PEM PKCS#8 ({@code JWT_PRIVATE_KEY_PATH})
 * @param previousKeyId         {@code kid} of the previous key during a rotation ({@code JWT_PREVIOUS_KEY_ID})
 * @param previousPublicKeyPath previous RSA public key, PEM, published in the JWKS during a rotation
 *                              ({@code JWT_PREVIOUS_PUBLIC_KEY_PATH}); empty outside a rotation
 * @param accessTokenTtl        validity of an access token ({@code ACCESS_TOKEN_TTL})
 */
@ConfigurationProperties("soukly.user.jwt")
public record JwtProperties(@DefaultValue("soukly-user-service") String issuer, String keyId, String privateKeyPath,
        String previousKeyId, String previousPublicKeyPath, @DefaultValue("15m") Duration accessTokenTtl) {
}
