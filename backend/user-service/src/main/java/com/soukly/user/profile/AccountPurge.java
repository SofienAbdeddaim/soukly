package com.soukly.user.profile;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.user.config.UserProperties;
import com.soukly.user.token.OpaqueTokens;

/**
 * Daily purge of the deleted accounts (AUTH-05): an account {@code DELETED} for longer than
 * {@link UserProperties#accountPurgeAfter()} is erased for good, with its tokens and sessions ({@code ON DELETE
 * CASCADE}) and its login failure counter. Its listings are erased by the purge of listing-service (ANN-05). Each
 * batch is one transaction that locks its accounts with {@code SKIP LOCKED}, so several instances can run it at the
 * same time.
 */
@Component
class AccountPurge {

    private static final Logger log = LoggerFactory.getLogger(AccountPurge.class);

    static final int BATCH_SIZE = 100;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final UserProperties properties;
    private final Clock clock;

    AccountPurge(JdbcClient jdbc, PlatformTransactionManager transactions, UserProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.properties = properties;
        this.clock = clock;
    }

    /** @return the accounts erased */
    @Scheduled(cron = "${soukly.user.account-purge-cron:0 45 3 * * *}", zone = "UTC")
    public int purge() {
        OffsetDateTime before = clock.instant().minus(properties.accountPurgeAfter()).atOffset(ZoneOffset.UTC);
        int total = 0;
        int erased;
        do {
            Integer count = transaction.execute(status -> eraseBatch(before));
            erased = count == null ? 0 : count;
            total += erased;
        } while (erased == BATCH_SIZE);
        log.info("Account purge: {} deleted account(s) erased", total);
        return total;
    }

    private int eraseBatch(OffsetDateTime before) {
        List<Deleted> accounts = jdbc.sql("""
                SELECT id, deleted_email FROM users
                WHERE status = 'DELETED' AND deleted_at < :before
                ORDER BY deleted_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED""")
                .param("before", before)
                .param("limit", BATCH_SIZE)
                .query((rs, row) -> new Deleted(rs.getObject("id", UUID.class), rs.getString("deleted_email")))
                .list();
        if (accounts.isEmpty()) {
            return 0;
        }
        List<String> emailHashes = accounts.stream().map(Deleted::email).filter(Objects::nonNull)
                .map(OpaqueTokens::sha256Hex).toList();
        if (!emailHashes.isEmpty()) {
            jdbc.sql("DELETE FROM login_throttle WHERE email_hash IN (:hashes)").param("hashes", emailHashes).update();
        }
        return jdbc.sql("DELETE FROM users WHERE id IN (:ids)")
                .param("ids", accounts.stream().map(Deleted::id).toList())
                .update();
    }

    private record Deleted(UUID id, String email) {
    }
}
