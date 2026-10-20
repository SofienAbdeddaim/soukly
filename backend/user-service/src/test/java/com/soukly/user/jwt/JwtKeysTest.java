package com.soukly.user.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.soukly.user.support.TestKeys;

class JwtKeysTest {

    @TempDir
    Path directory;

    @Test
    void missingPrivateKeyStopsTheServiceWithTheCommandToRun() {
        Path missing = directory.resolve("jwt-private.pem");

        assertThatThrownBy(() -> JwtKeys.load(properties("k1", missing, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missing.toAbsolutePath().normalize().toString())
                .hasMessageContaining("scripts/gen-jwt-key.sh");
    }

    @Test
    void activeKeyAloneIsPublishedWithoutItsPrivatePart() {
        Path key = TestKeys.writePrivate(TestKeys.ACTIVE, directory.resolve("private.pem"));

        JwtKeys keys = JwtKeys.load(properties("k2", key, null, null));

        assertThat(keys.signingKey().isPrivate()).isTrue();
        assertThat(keys.publicKeys().getKeys()).singleElement().satisfies(published -> {
            assertThat(published.getKeyID()).isEqualTo("k2");
            assertThat(published.isPrivate()).isFalse();
        });
    }

    @Test
    void keysShorterThan2048BitsAreRefused() {
        Path key = TestKeys.writePrivate(TestKeys.generate("weak", 1024), directory.resolve("weak.pem"));

        assertThatThrownBy(() -> JwtKeys.load(properties("weak", key, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2048");
    }

    @Test
    void publicKeyFileIsNotAPrivateKey() {
        Path key = TestKeys.writePublic(TestKeys.ACTIVE, directory.resolve("public.pem"));

        assertThatThrownBy(() -> JwtKeys.load(properties("k2", key, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PEM PKCS#8");
    }

    @Test
    void previousKeyNeedsItsOwnKid() {
        Path key = TestKeys.writePrivate(TestKeys.ACTIVE, directory.resolve("private.pem"));
        Path previous = TestKeys.writePublic(TestKeys.PREVIOUS, directory.resolve("previous.pem"));

        assertThatThrownBy(() -> JwtKeys.load(properties("k2", key, null, previous)))
                .hasMessageContaining("JWT_PREVIOUS_KEY_ID is not set");
        assertThatThrownBy(() -> JwtKeys.load(properties("k2", key, "k2", previous)))
                .hasMessageContaining("must differ");
    }

    @Test
    void keyIdIsRequired() throws Exception {
        Path key = TestKeys.writePrivate(TestKeys.ACTIVE, Files.createFile(directory.resolve("private.pem")));

        assertThatThrownBy(() -> JwtKeys.load(properties(" ", key, null, null)))
                .hasMessageContaining("JWT_KEY_ID is not set");
    }

    private static JwtProperties properties(String kid, Path privateKey, String previousKid, Path previousKey) {
        return new JwtProperties("soukly-user-service", kid, privateKey == null ? null : privateKey.toString(),
                previousKid, previousKey == null ? null : previousKey.toString(), Duration.ofMinutes(15));
    }
}
