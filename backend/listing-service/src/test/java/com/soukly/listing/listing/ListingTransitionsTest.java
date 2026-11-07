package com.soukly.listing.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every action from every status (ANN-05). */
class ListingTransitionsTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Duration WINDOW = Duration.ofDays(3);

    private final ListingTransitions transitions = new ListingTransitions(WINDOW);

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = { "DRAFT", "PUBLISHED", "EXPIRED", "INACTIVE" })
    void editingKeepsTheStatus(ListingStatus status) {
        assertThat(transitions.target(listing(status, NOW.plus(Duration.ofDays(30))), ListingAction.EDIT, NOW))
                .isEqualTo(status);
    }

    @Test
    void onlyADraftIsPublished() {
        assertThat(transitions.target(listing(ListingStatus.DRAFT, null), ListingAction.PUBLISH, NOW))
                .isEqualTo(ListingStatus.PUBLISHED);
        for (ListingStatus status : new ListingStatus[] { ListingStatus.PUBLISHED, ListingStatus.EXPIRED,
                ListingStatus.INACTIVE }) {
            assertThatThrownBy(() -> transitions.target(listing(status, NOW), ListingAction.PUBLISH, NOW))
                    .isInstanceOf(AlreadyPublishedException.class);
        }
    }

    @Test
    void onlyAPublishedListingIsDeactivated() {
        assertThat(transitions.target(listing(ListingStatus.PUBLISHED, NOW.plus(Duration.ofDays(30))),
                ListingAction.DEACTIVATE, NOW)).isEqualTo(ListingStatus.INACTIVE);
        for (ListingStatus status : new ListingStatus[] { ListingStatus.DRAFT, ListingStatus.EXPIRED,
                ListingStatus.INACTIVE }) {
            assertThatThrownBy(() -> transitions.target(listing(status, NOW), ListingAction.DEACTIVATE, NOW))
                    .isInstanceOf(TransitionNotAllowedException.class);
        }
    }

    @Test
    void expiredAndDeactivatedListingsAreRenewed() {
        assertThat(transitions.target(listing(ListingStatus.EXPIRED, NOW.minus(Duration.ofDays(4))),
                ListingAction.RENEW, NOW)).isEqualTo(ListingStatus.PUBLISHED);
        // A deactivated listing keeps the expiry date it had, even a past one
        assertThat(transitions.target(listing(ListingStatus.INACTIVE, NOW.plus(Duration.ofDays(40))),
                ListingAction.RENEW, NOW)).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(transitions.target(listing(ListingStatus.INACTIVE, NOW.minus(Duration.ofDays(40))),
                ListingAction.RENEW, NOW)).isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void aPublishedListingIsRenewedOnlyWithinTheWindow() {
        Map<Duration, Boolean> cases = Map.of(
                Duration.ofDays(2), true,
                WINDOW.minusSeconds(1), true,
                WINDOW, false,
                Duration.ofDays(30), false);
        cases.forEach((left, allowed) -> {
            Listing listing = listing(ListingStatus.PUBLISHED, NOW.plus(left));
            assertThat(transitions.allows(listing, ListingAction.RENEW, NOW)).as("expires in %s", left)
                    .isEqualTo(allowed);
        });
        assertThatThrownBy(() -> transitions.target(listing(ListingStatus.PUBLISHED, NOW.plus(Duration.ofDays(30))),
                ListingAction.RENEW, NOW)).isInstanceOf(RenewNotAllowedException.class);
        assertThatThrownBy(() -> transitions.target(listing(ListingStatus.DRAFT, null), ListingAction.RENEW, NOW))
                .isInstanceOf(RenewNotAllowedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ListingStatus.class, names = { "DRAFT", "PUBLISHED", "EXPIRED", "INACTIVE" })
    void everyListingButADeletedOneIsDeleted(ListingStatus status) {
        assertThat(transitions.target(listing(status, NOW), ListingAction.DELETE, NOW))
                .isEqualTo(ListingStatus.DELETED);
    }

    @ParameterizedTest
    @EnumSource(ListingAction.class)
    void aDeletedListingAllowsNothing(ListingAction action) {
        Listing deleted = listing(ListingStatus.DELETED, NOW);
        assertThatThrownBy(() -> transitions.target(deleted, action, NOW))
                .isInstanceOf(TransitionNotAllowedException.class);
        assertThat(transitions.allows(deleted, action, NOW)).isFalse();
    }

    private static Listing listing(ListingStatus status, Instant expiresAt) {
        ListingContent content = new ListingContent("home.furniture", null, null, null, null, false, null, null,
                Map.of());
        Instant publishedAt = status == ListingStatus.DRAFT ? null : NOW.minus(Duration.ofDays(10));
        return new Listing(UUID.randomUUID(), UUID.randomUUID(), status, content, 1, NOW, NOW, publishedAt,
                expiresAt, null, null, "8F3C2K9A");
    }
}
