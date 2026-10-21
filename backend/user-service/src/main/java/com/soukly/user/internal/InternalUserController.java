package com.soukly.user.internal;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;

/**
 * Seller data read by listing-service for the listing page (CATA-03). Never routed by the gateway, and protected by
 * {@link InternalTokenFilter}. Only active accounts are answered; any other id is {@code 404}.
 */
@RestController
@RequestMapping("/internal/users/{id}")
class InternalUserController {

    /** The months of "member since" are those of Tunisia. */
    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    private final UserAccountRepository accounts;

    InternalUserController(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** What the listing page shows of its seller; cached by listing-service. */
    @GetMapping("/public-profile")
    @Transactional(readOnly = true)
    ResponseEntity<PublicProfile> publicProfile(@PathVariable String id) {
        UserAccount account = active(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(new PublicProfile(account.displayName(),
                        YearMonth.from(account.createdAt().atZone(TUNIS)).toString()));
    }

    /** The phone number, for {@code POST /listings/{id}/contact} only; never stored by a cache. */
    @GetMapping("/contact")
    @Transactional(readOnly = true)
    ResponseEntity<Contact> contact(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Contact(active(id).phoneE164()));
    }

    private UserAccount active(String id) {
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new UserNotFoundException();
        }
        return accounts.findById(userId).filter(account -> account.status() == AccountStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }

    /** @param memberSince month of the registration, {@code 2026-03} */
    record PublicProfile(String displayName, String memberSince) {
    }

    /** @param phone E.164, {@code +21620123456} */
    record Contact(String phone) {
    }
}
