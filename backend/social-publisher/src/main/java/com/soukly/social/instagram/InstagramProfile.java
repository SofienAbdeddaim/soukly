package com.soukly.social.instagram;

import java.util.Set;

/**
 * An Instagram account read from {@code /me}.
 *
 * @param id          app-scoped id of the account
 * @param userId      id of the professional account, used to publish (SOC-05); may be {@code null}
 * @param accountType {@code BUSINESS}, {@code MEDIA_CREATOR} or, for a personal account, another value
 * @param pictureUrl  profile picture, or {@code null}
 */
public record InstagramProfile(String id, String userId, String username, String accountType, String pictureUrl) {

    /** Account types of the professional accounts: Business and Creator. */
    static final Set<String> PROFESSIONAL_TYPES = Set.of("BUSINESS", "MEDIA_CREATOR");

    /**
     * Whether Soukly can post on the account. Instagram Login only lets professional accounts in, so a profile without
     * {@code account_type} is taken as professional; a type outside Business and Creator is a personal account.
     */
    boolean professional() {
        return accountType == null || PROFESSIONAL_TYPES.contains(accountType);
    }
}
