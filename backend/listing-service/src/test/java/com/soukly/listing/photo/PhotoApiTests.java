package com.soukly.listing.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestGarage;
import com.soukly.listing.support.TestJwks;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Scenarios of ANN-02 against PostgreSQL and Garage (Testcontainers) and the real libvips tools: the files are
 * uploaded to the presigned URLs exactly like a browser would, then completed.
 */
class PhotoApiTests extends IntegrationTest {

    private static final int MB = 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private S3Client s3;

    @Autowired
    private PhotoService photos;

    @TempDir
    private Path temp;

    private UUID owner;
    private String listingId;

    @BeforeEach
    void newDraft() throws Exception {
        owner = UUID.randomUUID();
        listingId = JsonPath.read(mockMvc.perform(post("/listings")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"categoryCode\": \"home.furniture\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void aJpegUploadedWithItsPresignedUrlBecomesTheCoverOfTheListing() throws Exception {
        getListing(owner).andExpect(jsonPath("$.missingFields", hasItem("photos")));

        String ticket = requestUpload("image/jpeg", 3 * MB)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(ticket, "$.photoId");
        String uploadUrl = JsonPath.read(ticket, "$.uploadUrl");
        assertThat(URI.create(uploadUrl).getPath()).isEqualTo("/" + TestGarage.BUCKET + "/"
                + PhotoKeys.incoming(UUID.fromString(listingId), UUID.fromString(photoId)));
        Instant expiresAt = Instant.parse(JsonPath.read(ticket, "$.expiresAt"));
        assertThat(expiresAt).isBetween(Instant.now().plusSeconds(9 * 60), Instant.now().plusSeconds(10 * 60 + 5));
        assertThat(photoStatus(photoId)).isEqualTo("PENDING_UPLOAD");

        // A camera photo: about 3 MB, with EXIF and XMP
        byte[] original = padded(TestImages.jpegWithMetadata(4000, 3000, 1), 3 * MB);
        assertThat(upload(ticket, original)).isEqualTo(200);

        complete(owner, photoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(photoId))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.width").value(1600))
                .andExpect(jsonPath("$.height").value(1200))
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.url").value(
                        "https://photos.example.test/listings/" + listingId + "/" + photoId + "/1600.webp"))
                .andExpect(jsonPath("$.thumbnailUrl").value(
                        "https://photos.example.test/listings/" + listingId + "/" + photoId + "/400.webp"));

        assertThat(photoStatus(photoId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT bytes FROM listing_photos WHERE id = ?::uuid", Long.class, photoId))
                .isEqualTo(original.length);
        assertThat(exists(large(photoId))).isTrue();
        assertThat(exists(thumbnail(photoId))).isTrue();
        assertThat(exists(incoming(photoId))).isFalse();
        assertThat(s3.headObject(head -> head.bucket(TestGarage.BUCKET).key(large(photoId))).contentType())
                .isEqualTo("image/webp");
        assertThat(dimensions(download(thumbnail(photoId)))).isEqualTo(new Vips.Dimensions(400, 300));

        getListing(owner)
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].id").value(photoId))
                .andExpect(jsonPath("$.photos[0].position").value(0))
                .andExpect(jsonPath("$.photos[0].width").value(1600))
                .andExpect(jsonPath("$.photos[0].height").value(1200))
                .andExpect(jsonPath("$.photos[0].thumbnailUrl").exists())
                .andExpect(jsonPath("$.missingFields", not(hasItem("photos"))));

        // Completing it again (a retry after a network error) answers the same photo
        complete(owner, photoId).andExpect(status().isOk()).andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void everyMetadataIsRemovedFromBothVersions() throws Exception {
        byte[] original = TestImages.jpegWithMetadata(1200, 900, 1);
        // The original does carry EXIF with a GPS position, and XMP
        assertThat(allFields(original)).contains("exif-ifd3-GPSLatitude", "xmp-data");

        String photoId = uploadAndComplete(original).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        photoId = JsonPath.read(photoId, "$.id");

        for (String key : List.of(large(photoId), thumbnail(photoId))) {
            byte[] webp = download(key);
            assertThat(TestImages.webpChunks(webp)).doesNotContain("EXIF", "XMP ", "ICCP");
            assertThat(new String(webp, StandardCharsets.ISO_8859_1)).doesNotContain("Exif", "GPS", "xmpmeta");
            assertThat(allFields(webp)).doesNotContainIgnoringCase("exif").doesNotContainIgnoringCase("xmp")
                    .doesNotContainIgnoringCase("gps").doesNotContain("icc-profile-data");
        }
    }

    @Test
    void aPortraitPhotoStoredInLandscapeIsRotatedFromItsExifOrientation() throws Exception {
        byte[] original = TestImages.jpegWithMetadata(1200, 800, TestImages.ROTATE_90);
        assertThat(allFields(original)).contains("orientation: 6");

        String photo = uploadAndComplete(original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(800))
                .andExpect(jsonPath("$.height").value(1200))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(photo, "$.id");

        Vips.Dimensions large = dimensions(download(large(photoId)));
        assertThat(large.height()).isGreaterThan(large.width());
        assertThat(large).isEqualTo(new Vips.Dimensions(800, 1200));
        assertThat(dimensions(download(thumbnail(photoId)))).isEqualTo(new Vips.Dimensions(267, 400));
    }

    @Test
    void aPngIsConvertedToo() throws Exception {
        uploadAndComplete("image/png", TestImages.png(640, 480))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                // Never enlarged
                .andExpect(jsonPath("$.width").value(640))
                .andExpect(jsonPath("$.height").value(480));
    }

    @Test
    void aPdfRenamedPhotoJpgIsRejected() throws Exception {
        String ticket = ticket("image/jpeg", TestImages.pdf().length);
        String photoId = JsonPath.read(ticket, "$.photoId");
        upload(ticket, TestImages.pdf());

        complete(owner, photoId)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:photo-rejected"))
                .andExpect(jsonPath("$.code").value("photo.unsupported-format"));

        assertThat(photoStatus(photoId)).isEqualTo("REJECTED");
        assertThat(exists(incoming(photoId))).isFalse();
        // Completing it again gives the same answer
        complete(owner, photoId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.unsupported-format"));
        getListing(owner).andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void aFileLargerThanDeclaredAndThanTheMaximumIsRejected() throws Exception {
        String ticket = ticket("image/jpeg", 2 * MB);
        String photoId = JsonPath.read(ticket, "$.photoId");
        assertThat(upload(ticket, TestImages.bytes(8 * MB))).isEqualTo(200);

        complete(owner, photoId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.too-large"));
        assertThat(photoStatus(photoId)).isEqualTo("REJECTED");
        assertThat(exists(incoming(photoId))).isFalse();
    }

    @Test
    void aCompletionWithoutUploadIsRejected() throws Exception {
        String photoId = JsonPath.read(ticket("image/webp", MB), "$.photoId");

        complete(owner, photoId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.not-uploaded"));
        assertThat(photoStatus(photoId)).isEqualTo("REJECTED");
    }

    @Test
    void imagesTooSmallOrWithTooManyPixelsAreRejected() throws Exception {
        uploadAndComplete(TestImages.jpeg(399, 300))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.too-small"));
        uploadAndComplete(TestImages.jpeg(1000, 299))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.too-small"));
        // A portrait photo is accepted: 300 × 400
        uploadAndComplete(TestImages.jpeg(300, 400)).andExpect(status().isOk());
        // 10,000 × 5,001 = 50,010,000 pixels declared by a file of a few dozen bytes: an image bomb
        uploadAndComplete("image/png", TestImages.pngHeader(10_000, 5_001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.too-many-pixels"));
    }

    @Test
    void theDeclaredTypeAndSizeAreCheckedBeforeAnyUpload() throws Exception {
        requestUpload("image/gif", MB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("contentType"))
                .andExpect(jsonPath("$.errors[0].code").value("photo.unsupported-format"));
        requestUpload("image/jpeg", 5 * MB + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sizeBytes"))
                .andExpect(jsonPath("$.errors[0].code").value("photo.too-large"));
        requestUpload("image/jpeg", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("photo.invalid-size"));
        assertThat(count()).isZero();
    }

    @Test
    void anEleventhPhotoIsRefused() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestUpload("image/jpeg", MB).andExpect(status().isCreated());
        }
        requestUpload("image/jpeg", MB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:photo-limit-reached"));

        // A rejected photo frees its place
        String photoId = jdbc.queryForObject("SELECT min(id::text) FROM listing_photos WHERE listing_id = ?::uuid",
                String.class, listingId);
        complete(owner, photoId).andExpect(status().isBadRequest());
        requestUpload("image/jpeg", MB).andExpect(status().isCreated());
    }

    @Test
    void theFirstPhotoOfTheOrderIsTheCover() throws Exception {
        String p1 = readyPhoto();
        String p2 = readyPhoto();
        String p3 = readyPhoto();
        getListing(owner).andExpect(jsonPath("$.photos[*].id").value(contains(p1, p2, p3)));

        order(owner, p3, p1, p2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(p3, p1, p2)))
                .andExpect(jsonPath("$[*].position").value(contains(0, 1, 2)));
        getListing(owner)
                .andExpect(jsonPath("$.photos[0].id").value(p3))
                .andExpect(jsonPath("$.photos[*].position").value(contains(0, 1, 2)));

        // Every READY photo, each once
        order(owner, p3, p1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("photoIds"))
                .andExpect(jsonPath("$.errors[0].code").value("photo-order.invalid"));
        order(owner, p3, p1, p1).andExpect(status().isBadRequest());
        order(owner, p3, p1, p2, UUID.randomUUID().toString()).andExpect(status().isBadRequest());

        // A new photo comes last
        String p4 = readyPhoto();
        getListing(owner).andExpect(jsonPath("$.photos[*].id").value(contains(p3, p1, p2, p4)));

        // Deleting one moves the next ones up and removes its files
        mockMvc.perform(delete("/listings/" + listingId + "/photos/" + p1)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(exists(large(p1))).isFalse();
        assertThat(exists(thumbnail(p1))).isFalse();
        getListing(owner)
                .andExpect(jsonPath("$.photos[*].id").value(contains(p3, p2, p4)))
                .andExpect(jsonPath("$.photos[*].position").value(contains(0, 1, 2)));
        mockMvc.perform(delete("/listings/" + listingId + "/photos/" + p1)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:photo-not-found"));
    }

    @Test
    void anotherAccountCannotTouchThePhotos() throws Exception {
        String photoId = readyPhoto();
        UUID other = UUID.randomUUID();

        mockMvc.perform(post("/listings/" + listingId + "/photos")
                .header(HttpHeaders.AUTHORIZATION, bearer(other))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"contentType\": \"image/jpeg\", \"sizeBytes\": 1000 }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:soukly:problem:listing-not-found"));
        complete(other, photoId).andExpect(status().isNotFound());
        order(other, photoId).andExpect(status().isNotFound());
        mockMvc.perform(delete("/listings/" + listingId + "/photos/" + photoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        // Without a token: 401
        mockMvc.perform(post("/listings/" + listingId + "/photos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"contentType\": \"image/jpeg\", \"sizeBytes\": 1000 }"))
                .andExpect(status().isUnauthorized());
        // An id that is not a UUID
        complete(owner, "not-a-uuid").andExpect(status().isNotFound());
        assertThat(exists(large(photoId))).isTrue();
    }

    @Test
    void theCleanupRejectsAbandonedUploadsAndDeletesTheirOriginals() throws Exception {
        String abandoned = JsonPath.read(uploaded(TestImages.jpeg(800, 600)), "$.photoId");
        String recent = JsonPath.read(uploaded(TestImages.jpeg(800, 600)), "$.photoId");
        jdbc.update("UPDATE listing_photos SET created_at = now() - interval '61 minutes' WHERE id = ?::uuid",
                abandoned);
        assertThat(exists(incoming(abandoned))).isTrue();

        photos.cleanUp();

        assertThat(photoStatus(abandoned)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT rejection_code FROM listing_photos WHERE id = ?::uuid", String.class,
                abandoned)).isEqualTo("photo.not-uploaded");
        assertThat(exists(incoming(abandoned))).isFalse();
        assertThat(photoStatus(recent)).isEqualTo("PENDING_UPLOAD");
        assertThat(exists(incoming(recent))).isTrue();
        complete(owner, abandoned).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("photo.not-uploaded"));

        // An original left in incoming/ for more than an hour is deleted, whatever its photo
        String orphan = "incoming/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        s3.putObject(put -> put.bucket(TestGarage.BUCKET).key(orphan),
                RequestBody.fromBytes(new byte[] { 1 }));
        PhotoService.Cleanup later = photos.cleanUp(Instant.now().plus(Duration.ofMinutes(61)));
        // The recent upload is now abandoned too: rejected, then its original deleted with the orphan
        assertThat(later.rejectedUploads()).isGreaterThanOrEqualTo(1);
        assertThat(later.deletedOriginals()).isGreaterThanOrEqualTo(1);
        assertThat(exists(orphan)).isFalse();
        assertThat(exists(incoming(recent))).isFalse();
        assertThat(photoStatus(recent)).isEqualTo("REJECTED");
    }

    @Test
    void deleteAllPhotosRemovesEveryPhotoAndItsFiles() throws Exception {
        String ready = readyPhoto();
        String pending = JsonPath.read(uploaded(TestImages.jpeg(800, 600)), "$.photoId");

        photos.deleteAllPhotos(UUID.fromString(listingId));

        assertThat(count()).isZero();
        assertThat(exists(large(ready))).isFalse();
        assertThat(exists(thumbnail(ready))).isFalse();
        assertThat(exists(incoming(pending))).isFalse();
    }

    // --- helpers ---

    private ResultActions requestUpload(String contentType, long sizeBytes) throws Exception {
        return mockMvc.perform(post("/listings/" + listingId + "/photos")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"contentType\": \"" + contentType + "\", \"sizeBytes\": " + sizeBytes + " }"));
    }

    private String ticket(String contentType, long sizeBytes) throws Exception {
        return requestUpload(contentType, sizeBytes).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** PUT on the presigned URL with the headers of the ticket, like the browser; returns the HTTP status. */
    private static int upload(String ticket, byte[] file) throws IOException, InterruptedException {
        Map<String, String> headers = JsonPath.read(ticket, "$.headers");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(JsonPath.read(ticket, "$.uploadUrl")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file));
        headers.forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Requests a JPEG upload and uploads {@code file}; returns the ticket. */
    private String uploaded(byte[] file) throws Exception {
        String ticket = ticket("image/jpeg", file.length);
        assertThat(upload(ticket, file)).isEqualTo(200);
        return ticket;
    }

    private ResultActions uploadAndComplete(byte[] jpeg) throws Exception {
        return uploadAndComplete("image/jpeg", jpeg);
    }

    private ResultActions uploadAndComplete(String contentType, byte[] file) throws Exception {
        String ticket = ticket(contentType, file.length);
        assertThat(upload(ticket, file)).isEqualTo(200);
        return complete(owner, JsonPath.read(ticket, "$.photoId"));
    }

    private String readyPhoto() throws Exception {
        return JsonPath.read(uploadAndComplete(TestImages.jpeg(800, 600)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions complete(UUID account, String photoId) throws Exception {
        return mockMvc.perform(post("/listings/" + listingId + "/photos/" + photoId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions order(UUID account, String... photoIds) throws Exception {
        String ids = String.join("\", \"", photoIds);
        return mockMvc.perform(put("/listings/" + listingId + "/photos/order")
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"photoIds\": [\"" + ids + "\"] }"));
    }

    private ResultActions getListing(UUID account) throws Exception {
        return mockMvc.perform(get("/listings/" + listingId).header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isOk());
    }

    private String photoStatus(String photoId) {
        return jdbc.queryForObject("SELECT status FROM listing_photos WHERE id = ?::uuid", String.class, photoId);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM listing_photos WHERE listing_id = ?::uuid", Integer.class,
                listingId);
    }

    private String incoming(String photoId) {
        return PhotoKeys.incoming(UUID.fromString(listingId), UUID.fromString(photoId));
    }

    private String large(String photoId) {
        return PhotoKeys.large(UUID.fromString(listingId), UUID.fromString(photoId));
    }

    private String thumbnail(String photoId) {
        return PhotoKeys.thumbnail(UUID.fromString(listingId), UUID.fromString(photoId));
    }

    private boolean exists(String key) {
        try {
            s3.headObject(head -> head.bucket(TestGarage.BUCKET).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private byte[] download(String key) {
        return s3.getObject(get -> get.bucket(TestGarage.BUCKET).key(key), ResponseTransformer.toBytes())
                .asByteArray();
    }

    /** Every metadata field libvips finds in the file ({@code vipsheader -a}). */
    private String allFields(byte[] image) throws Exception {
        Path file = Files.write(Files.createTempFile(temp, "image-", ".bin"), image);
        Process process = new ProcessBuilder("vipsheader", "-a", file.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }

    private Vips.Dimensions dimensions(byte[] image) throws Exception {
        String fields = allFields(image);
        return new Vips.Dimensions(field(fields, "width"), field(fields, "height"));
    }

    private static int field(String fields, String name) {
        return fields.lines().filter(line -> line.startsWith(name + ": ")).findFirst()
                .map(line -> Integer.parseInt(line.substring(name.length() + 2).trim()))
                .orElseThrow();
    }

    /** Pads a JPEG after its end marker up to {@code size} bytes: still a valid file, of a realistic weight. */
    private static byte[] padded(byte[] jpeg, int size) {
        return Arrays.copyOf(jpeg, Math.max(size, jpeg.length));
    }

    private static String bearer(UUID account) {
        return "Bearer " + TestJwks.token(account);
    }
}
