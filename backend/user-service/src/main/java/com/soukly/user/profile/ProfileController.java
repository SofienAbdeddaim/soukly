package com.soukly.user.profile;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.user.session.OriginPolicy;
import com.soukly.user.session.RefreshCookies;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

/**
 * Profile of the signed-in account (AUTH-05), exposed by the gateway under {@code /api/users/**}. Every endpoint but
 * the confirmation of a new address needs an access token; the account is its subject.
 */
@RestController
@RequestMapping("/users")
class ProfileController {

    static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    private final ProfileService profiles;
    private final EmailChanges emailChanges;
    private final AccountDeletion deletion;
    private final RefreshCookies cookies;
    private final OriginPolicy origins;

    ProfileController(ProfileService profiles, EmailChanges emailChanges, AccountDeletion deletion,
            RefreshCookies cookies, OriginPolicy origins) {
        this.profiles = profiles;
        this.emailChanges = emailChanges;
        this.deletion = deletion;
        this.cookies = cookies;
        this.origins = origins;
    }

    @GetMapping("/me")
    ResponseEntity<ProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.get(userId(jwt)));
    }

    @PatchMapping(path = "/me", consumes = { MERGE_PATCH_JSON, MediaType.APPLICATION_JSON_VALUE })
    ResponseEntity<ProfileResponse> update(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode patch) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.update(userId(jwt), patch));
    }

    /** The refresh cookie (path {@code /api/users}) tells which device asked: it stays signed in. */
    @PostMapping("/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileRequests.ChangePassword body,
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        profiles.changePassword(userId(jwt), body.currentPassword(), body.newPassword(), refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/email")
    ResponseEntity<Void> changeEmail(@AuthenticationPrincipal Jwt jwt, @RequestBody ProfileRequests.ChangeEmail body) {
        emailChanges.request(userId(jwt), body.newEmail(), body.currentPassword());
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/me/email-change")
    ResponseEntity<Void> cancelEmailChange(@AuthenticationPrincipal Jwt jwt) {
        emailChanges.cancel(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Public: the link of the email may be opened on another device. */
    @PostMapping("/email/confirm")
    ResponseEntity<Void> confirmEmail(@RequestBody ProfileRequests.ConfirmEmail body) {
        emailChanges.confirm(body.token());
        return ResponseEntity.noContent().build();
    }

    /** Deletes the account; the response deletes the refresh cookie, hence the check of {@code Origin}. */
    @DeleteMapping("/me")
    ResponseEntity<Void> delete(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileRequests.DeleteAccount body) {
        origins.check(request);
        deletion.delete(userId(jwt), body.currentPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
