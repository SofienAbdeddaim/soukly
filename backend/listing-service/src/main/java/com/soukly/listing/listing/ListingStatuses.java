package com.soukly.listing.listing;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Status of listings for the other services (SOC-06): whether each one is online, as the search and the listing page
 * see it. A {@code PUBLISHED} listing past its expiry is already {@code EXPIRED} for them (the expiry task runs every
 * hour).
 */
@Service
public class ListingStatuses {

    private final JdbcClient jdbc;
    private final Clock clock;

    ListingStatuses(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param id     the listing
     * @param status its status, {@code EXPIRED} for a {@code PUBLISHED} listing past its expiry
     * @param online {@code true} when the listing is {@code PUBLISHED} and not expired
     */
    public record StatusView(UUID id, ListingStatus status, boolean online) {
    }

    /** The statuses of the listings that exist among {@code ids}, in no particular order. */
    public List<StatusView> of(Collection<UUID> ids) {
        Instant now = clock.instant();
        return jdbc.sql("SELECT id, status, expires_at FROM listings WHERE id IN (:ids)")
                .param("ids", ids)
                .query((rs, row) -> {
                    ListingStatus status = ListingStatus.valueOf(rs.getString("status"));
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    if (status == ListingStatus.PUBLISHED && expiresAt != null
                            && !expiresAt.toInstant().isAfter(now)) {
                        status = ListingStatus.EXPIRED;
                    }
                    return new StatusView(rs.getObject("id", UUID.class), status,
                            status == ListingStatus.PUBLISHED);
                })
                .list();
    }
}
