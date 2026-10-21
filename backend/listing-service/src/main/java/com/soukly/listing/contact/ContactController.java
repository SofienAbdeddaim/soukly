package com.soukly.listing.contact;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.listing.listing.ListingNotFoundException;

/**
 * {@code POST /listings/{id}/contact} (CATA-03), {@code POST /api/listings/{id}/contact} at the gateway: the phone
 * number of the seller, for a member only. A {@code POST}, since the request is counted and must never be cached or
 * prefetched.
 */
@RestController
class ContactController {

    private final ContactService contacts;

    ContactController(ContactService contacts) {
        this.contacts = contacts;
    }

    @PostMapping("/listings/{id}/contact")
    ResponseEntity<Contact> contact(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        UUID listingId;
        try {
            listingId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ListingNotFoundException();
        }
        String phone = contacts.reveal(UUID.fromString(jwt.getSubject()), listingId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Contact(phone));
    }

    /** @param phone E.164, {@code +21620123456} */
    record Contact(String phone) {
    }
}
