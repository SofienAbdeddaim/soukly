package com.soukly.listing.photo;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.listing.listing.FieldError;
import com.soukly.listing.listing.InvalidListingException;
import com.soukly.listing.listing.Listing;
import com.soukly.listing.listing.ListingIncompleteException;
import com.soukly.listing.listing.ListingNotFoundException;
import com.soukly.listing.listing.ListingService;

/**
 * Photos of a listing (ANN-02): upload request, completion, order, deletion and cleanup. Only the owner of the
 * listing can change them; anyone else gets {@code 404}, like for the listing itself.
 *
 * <p>The file never goes through the services: the browser uploads it straight to the bucket with a presigned URL,
 * then asks for its completion. The conversion runs outside any database transaction, so a slow photo never holds
 * the lock of its listing.
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    static final String CONTENT_TYPE = "contentType";
    static final String SIZE_BYTES = "sizeBytes";
    static final String PHOTO_IDS = "photoIds";
    /** Member of {@code missingFields} when a listing has no READY photo (same as {@code ListingViews}). */
    static final String MISSING_PHOTOS = "photos";

    private final ListingService listings;
    private final PhotoRepository repository;
    private final PhotoStorage storage;
    private final PhotoProcessor processor;
    private final PhotoUrls urls;
    private final PhotoProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    PhotoService(ListingService listings, PhotoRepository repository, PhotoStorage storage, PhotoProcessor processor,
            PhotoUrls urls, PhotoProperties properties, PlatformTransactionManager transactions, Clock clock) {
        this.listings = listings;
        this.repository = repository;
        this.storage = storage;
        this.processor = processor;
        this.urls = urls;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * Creates a {@code PENDING_UPLOAD} photo, last of the listing, and a presigned {@code PUT} URL for its original.
     *
     * @param contentType declared type of the file, checked again from its bytes at completion
     * @param sizeBytes   declared size, checked again at completion
     * @throws ListingNotFoundException    unknown listing, or a listing of another account
     * @throws InvalidListingException     a type other than JPEG, PNG or WebP, or a size above the maximum
     * @throws PhotoLimitReachedException  the listing already has the maximum number of photos
     */
    @Transactional
    public UploadTicket requestUpload(UUID ownerId, UUID listingId, String contentType, Long sizeBytes) {
        listings.lockForPhotos(ownerId, listingId);
        List<FieldError> errors = new ArrayList<>();
        if (contentType == null || ImageFormat.ofMediaType(contentType).isEmpty()) {
            errors.add(new FieldError(CONTENT_TYPE, PhotoRejection.UNSUPPORTED_FORMAT.code()));
        }
        if (sizeBytes == null || sizeBytes < 1) {
            errors.add(new FieldError(SIZE_BYTES, "photo.invalid-size"));
        } else if (sizeBytes > properties.maxSize().toBytes()) {
            errors.add(new FieldError(SIZE_BYTES, PhotoRejection.TOO_LARGE.code()));
        }
        if (!errors.isEmpty()) {
            throw new InvalidListingException(errors);
        }
        if (repository.countActive(listingId) >= properties.maxPerListing()) {
            throw new PhotoLimitReachedException(properties.maxPerListing());
        }
        UUID photoId = UUID.randomUUID();
        repository.insertPending(photoId, listingId, sizeBytes, now());
        PhotoStorage.PresignedUpload upload = storage.presignUpload(PhotoKeys.incoming(listingId, photoId),
                contentType, properties.uploadUrlTtl());
        return new UploadTicket(photoId, upload.url(), upload.method(), upload.headers(), upload.expiresAt());
    }

    /**
     * Checks and converts the uploaded original, deletes it and makes the photo {@code READY}, last of the listing.
     * Completing a photo again answers it again: {@code READY} as is, {@code REJECTED} with the same error.
     *
     * @throws ListingNotFoundException unknown listing, or a listing of another account
     * @throws PhotoNotFoundException   unknown photo
     * @throws PhotoRejectedException   the file cannot become a photo; the photo is {@code REJECTED}
     */
    public PhotoView complete(UUID ownerId, UUID listingId, UUID photoId) {
        ListingPhoto photo = locked(ownerId, listingId, () -> find(listingId, photoId));
        switch (photo.status()) {
            case READY -> {
                return urls.view(photo);
            }
            case REJECTED -> throw new PhotoRejectedException(PhotoRejection.ofCode(photo.rejectionCode()));
            case PENDING_UPLOAD -> {
                // converted below
            }
        }
        PhotoProcessor.Converted converted;
        try {
            converted = processor.process(listingId, photoId);
        } catch (PhotoRejectedException e) {
            transaction.executeWithoutResult(status -> repository.markRejected(photoId, e.rejection(), now()));
            storage.delete(List.of(PhotoKeys.incoming(listingId, photoId)));
            throw e;
        }
        storage.delete(List.of(PhotoKeys.incoming(listingId, photoId)));
        try {
            return transaction.execute(status -> {
                Listing listing = listings.lockForPhotos(ownerId, listingId);
                repository.markReady(photoId, converted.width(), converted.height(), converted.bytes(), now());
                ListingPhoto done = find(listingId, photoId);
                if (done.status() == PhotoStatus.REJECTED) {
                    // Rejected by the cleanup while it was converted
                    throw new PhotoRejectedException(PhotoRejection.ofCode(done.rejectionCode()));
                }
                listings.photosChanged(listing);
                return urls.view(done);
            });
        } catch (ListingNotFoundException | PhotoNotFoundException | PhotoRejectedException e) {
            // Deleted or rejected meanwhile: its versions must not stay in the bucket
            storage.delete(PhotoKeys.all(listingId, photoId));
            throw e;
        }
    }

    /**
     * Puts the READY photos in the given order; the first one is the cover.
     *
     * @param photoIds every READY photo of the listing, each once
     * @return the READY photos in their new order
     * @throws InvalidListingException  {@code photoIds} is not exactly the READY photos
     */
    @Transactional
    public List<PhotoView> reorder(UUID ownerId, UUID listingId, List<UUID> photoIds) {
        Listing listing = listings.lockForPhotos(ownerId, listingId);
        List<ListingPhoto> ready = repository.ready(listingId);
        Map<UUID, ListingPhoto> byId = ready.stream().collect(Collectors.toMap(ListingPhoto::id, Function.identity()));
        if (photoIds == null || photoIds.size() != ready.size() || !byId.keySet().equals(new HashSet<>(photoIds))) {
            throw new InvalidListingException(List.of(new FieldError(PHOTO_IDS, "photo-order.invalid")));
        }
        Instant now = now();
        boolean moved = false;
        for (int position = 0; position < photoIds.size(); position++) {
            if (byId.get(photoIds.get(position)).position() != position) {
                repository.setPosition(photoIds.get(position), position, now);
                moved = true;
            }
        }
        if (moved) {
            listings.photosChanged(listing);
        }
        return repository.ready(listingId).stream().map(urls::view).toList();
    }

    /**
     * Deletes a photo, whatever its status, and its objects; the next ones move up. A listing that was published keeps
     * at least one READY photo (ANN-05).
     *
     * @throws ListingNotFoundException    unknown listing, or a listing of another account
     * @throws PhotoNotFoundException      unknown photo
     * @throws ListingIncompleteException  the last READY photo of a listing that was published
     */
    @Transactional
    public void delete(UUID ownerId, UUID listingId, UUID photoId) {
        Listing listing = listings.lockForPhotos(ownerId, listingId);
        ListingPhoto photo = find(listingId, photoId);
        boolean ready = photo.status() == PhotoStatus.READY;
        if (ready && listing.publishedAt() != null && repository.ready(listingId).size() <= 1) {
            throw new ListingIncompleteException(List.of(MISSING_PHOTOS));
        }
        // Objects first: if the storage fails, the photo stays and the request can be repeated.
        storage.delete(PhotoKeys.all(listingId, photoId));
        repository.delete(photoId);
        repository.compact(listingId, now());
        if (ready) {
            listings.photosChanged(listing);
        }
    }

    /**
     * Deletes every photo of a listing and its objects, for the deletion and the purge of the listing (ANN-05), which
     * call it with the listing locked.
     */
    @Transactional
    public void deleteAllPhotos(UUID listingId) {
        List<ListingPhoto> photos = repository.all(listingId);
        storage.delete(photos.stream().flatMap(photo -> PhotoKeys.all(listingId, photo.id()).stream()).toList());
        repository.deleteAll(listingId);
    }

    /**
     * Rejects the uploads not completed within {@code pendingTtl} and deletes the originals left in
     * {@code incoming/} for longer than that. A key under {@code incoming/} contains the id of its photo, so it is
     * always uploaded after the photo was created: an original that old belongs to a photo already rejected.
     *
     * @return number of photos rejected and of objects deleted
     */
    public Cleanup cleanUp() {
        return cleanUp(clock.instant());
    }

    /** {@link #cleanUp()} as if it ran at {@code at}. */
    Cleanup cleanUp(Instant at) {
        Instant before = at.minus(properties.pendingTtl());
        int rejected = 0;
        for (ListingPhoto photo : repository.pendingCreatedBefore(before)) {
            if (Boolean.TRUE.equals(transaction.execute(
                    status -> repository.markRejected(photo.id(), PhotoRejection.NOT_UPLOADED, now())))) {
                rejected++;
            }
            storage.delete(List.of(PhotoKeys.incoming(photo.listingId(), photo.id())));
        }
        List<String> orphans = storage.keysOlderThan(PhotoKeys.INCOMING_PREFIX, before);
        storage.delete(orphans);
        if (rejected > 0 || !orphans.isEmpty()) {
            log.info("Photo cleanup: {} uploads rejected, {} originals deleted", rejected, orphans.size());
        }
        return new Cleanup(rejected, orphans.size());
    }

    private ListingPhoto find(UUID listingId, UUID photoId) {
        return repository.find(listingId, photoId).orElseThrow(PhotoNotFoundException::new);
    }

    /** Runs {@code work} in a transaction, with the listing of {@code ownerId} locked. */
    private <T> T locked(UUID ownerId, UUID listingId, Supplier<T> work) {
        return transaction.execute(status -> {
            listings.lockForPhotos(ownerId, listingId);
            return work.get();
        });
    }

    /** Truncated to the precision of PostgreSQL, so that an answer and a later reading agree. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Answer of an upload request: the browser sends the file with {@code method} to {@code uploadUrl}, with exactly
     * {@code headers}, before {@code expiresAt}.
     */
    public record UploadTicket(UUID photoId, URI uploadUrl, String method, Map<String, String> headers,
            Instant expiresAt) {
    }

    public record Cleanup(int rejectedUploads, int deletedOriginals) {
    }
}
