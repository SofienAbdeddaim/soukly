package com.soukly.social.token;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Encryption of the social network tokens (SOC-01), bound from {@code soukly.social.token-encryption.*}.
 *
 * @param key        32 random bytes in base64 ({@code SOCIAL_TOKEN_ENC_KEY}); never logged
 * @param keyVersion stored with every ciphertext, so that a later rotation knows which key encrypted it
 */
@ConfigurationProperties("soukly.social.token-encryption")
public record TokenEncryptionProperties(String key, @DefaultValue("1") int keyVersion) {

    /** The key is never part of the string form of the properties. */
    @Override
    public String toString() {
        return "TokenEncryptionProperties[keyVersion=" + keyVersion + "]";
    }
}
