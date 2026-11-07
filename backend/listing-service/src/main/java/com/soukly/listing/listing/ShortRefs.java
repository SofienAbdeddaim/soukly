package com.soukly.listing.listing;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Short references of the listings (SOC-05), written in the Instagram captions where links are not clickable
 * ({@code soukly.tn/r/8F3C2K9A}) and read over the phone: {@value #LENGTH} characters among the capital letters and
 * digits without {@code 0}, {@code O}, {@code 1} and {@code I}, which are easily confused. 32^8, about a thousand
 * billion references: a collision is drawn again ({@link ListingRepository#insert}).
 */
final class ShortRefs {

    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int LENGTH = 8;

    private static final RandomGenerator RANDOM = new SecureRandom();

    private ShortRefs() {
    }

    static String generate() {
        return generate(RANDOM);
    }

    static String generate(RandomGenerator random) {
        StringBuilder ref = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            ref.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return ref.toString();
    }

    /** A reference as typed, in any case, or empty when it cannot be one: nothing to look up then. */
    static Optional<String> normalize(String typed) {
        if (typed == null || typed.length() != LENGTH) {
            return Optional.empty();
        }
        String ref = typed.toUpperCase(Locale.ROOT);
        for (int i = 0; i < LENGTH; i++) {
            if (ALPHABET.indexOf(ref.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(ref);
    }
}
