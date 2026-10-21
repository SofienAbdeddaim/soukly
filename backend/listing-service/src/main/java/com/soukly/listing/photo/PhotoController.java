package com.soukly.listing.photo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.listing.listing.ListingNotFoundException;

/**
 * Photos of a listing (ANN-02), under {@code /api/listings/{id}/photos} at the gateway. Only the owner of the listing
 * reaches them; the file itself is uploaded straight to the bucket, never through these endpoints.
 */
@RestController
@RequestMapping("/listings/{id}/photos")
class PhotoController {

    private final PhotoService photos;

    PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PhotoService.UploadTicket> requestUpload(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String id, @RequestBody UploadRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(photos.requestUpload(ownerId(jwt), listingId(id), body.contentType(), body.sizeBytes()));
    }

    @PostMapping("/{photoId}/complete")
    ResponseEntity<PhotoView> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @PathVariable String photoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(photos.complete(ownerId(jwt), listingId(id), photoId(photoId)));
    }

    @PutMapping(path = "/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<PhotoView>> reorder(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody OrderRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(photos.reorder(ownerId(jwt), listingId(id), body.photoIds()));
    }

    @DeleteMapping("/{photoId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @PathVariable String photoId) {
        photos.delete(ownerId(jwt), listingId(id), photoId(photoId));
        return ResponseEntity.noContent().build();
    }

    /** The {@code sub} is checked to be a UUID when the token is decoded. */
    private static UUID ownerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** An id that is not a UUID cannot exist: {@code 404}, like an unknown one. */
    private static UUID listingId(String id) {
        return uuid(id).orElseThrow(ListingNotFoundException::new);
    }

    private static UUID photoId(String id) {
        return uuid(id).orElseThrow(PhotoNotFoundException::new);
    }

    private static Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * @param contentType {@code image/jpeg}, {@code image/png} or {@code image/webp}
     * @param sizeBytes   size of the file the browser will upload
     */
    record UploadRequest(String contentType, Long sizeBytes) {
    }

    record OrderRequest(List<UUID> photoIds) {
    }
}
