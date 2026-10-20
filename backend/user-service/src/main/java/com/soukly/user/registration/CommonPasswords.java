package com.soukly.user.registration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The 10,000 most common passwords (see the user-service README for the source), compared without case.
 */
@Component
class CommonPasswords {

    static final String RESOURCE = "security/common-passwords.txt";

    private final Set<String> passwords;

    CommonPasswords() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            passwords = reader.lines().filter(line -> !line.isEmpty())
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    boolean contains(String password) {
        return passwords.contains(password.toLowerCase(Locale.ROOT));
    }
}
