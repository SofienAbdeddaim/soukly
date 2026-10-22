package com.soukly.social.facebook;

import java.util.List;

/**
 * The Pages offered to a member who manages several, with their tokens: stored encrypted, never returned.
 *
 * @param externalUserId app-scoped id of the person who granted the permissions
 */
record PendingSelection(String externalUserId, List<ManagedPage> pages) {

    @Override
    public String toString() {
        return "PendingSelection[pages=" + pages.size() + "]";
    }
}
