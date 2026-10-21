package com.soukly.listing.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import com.jayway.jsonpath.JsonPath;

/**
 * Garage of the tests: the image of docker-compose.yml (SETUP-02), one node, started once for every test class and
 * set up like {@code infra/garage/init.sh} (layout, imported key with read and write, bucket) through the admin API
 * v2. The secrets are random, generated when the tests start.
 */
public final class TestGarage {

    public static final String BUCKET = "soukly-listings";
    public static final String REGION = "garage";

    private static final int S3_PORT = 3900;
    private static final int ADMIN_PORT = 3903;

    private static final String ADMIN_TOKEN = hex(32);
    public static final String ACCESS_KEY = "GK" + hex(12);
    public static final String SECRET_KEY = hex(32);

    private static final String CONFIG = """
            metadata_dir = "/var/lib/garage/meta"
            data_dir = "/var/lib/garage/data"
            db_engine = "lmdb"
            replication_factor = 1
            rpc_bind_addr = "0.0.0.0:3901"
            rpc_public_addr = "127.0.0.1:3901"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "0.0.0.0:3900"

            [admin]
            api_bind_addr = "0.0.0.0:3903"
            """;

    @SuppressWarnings("resource") // stopped by Testcontainers (Ryuk) when the JVM exits
    private static final GenericContainer<?> CONTAINER = new GenericContainer<>("dxflrs/garage:v2.4.1")
            .withEnv("GARAGE_RPC_SECRET", hex(32))
            .withEnv("GARAGE_ADMIN_TOKEN", ADMIN_TOKEN)
            .withCopyToContainer(Transferable.of(CONFIG), "/etc/garage.toml")
            .withExposedPorts(S3_PORT, ADMIN_PORT)
            .waitingFor(Wait.forLogMessage(".*Admin API server listening.*", 1));

    private static volatile boolean started;

    private TestGarage() {
    }

    /** S3 API of the container, also the host of the presigned URLs. */
    public static synchronized URI endpoint() {
        if (!started) {
            CONTAINER.start();
            setUp();
            started = true;
        }
        return URI.create("http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(S3_PORT));
    }

    private static void setUp() {
        String nodeId = JsonPath.read(admin("GET", "GetClusterStatus", null), "$.nodes[0].id");
        admin("POST", "UpdateClusterLayout", """
                {"roles": [{"id": "%s", "zone": "test", "capacity": 1000000000, "tags": []}]}""".formatted(nodeId));
        admin("POST", "ApplyClusterLayout", "{\"version\": 1}");
        admin("POST", "ImportKey", """
                {"accessKeyId": "%s", "secretAccessKey": "%s", "name": "soukly-test"}""".formatted(ACCESS_KEY,
                SECRET_KEY));
        String bucketId = JsonPath.read(admin("POST", "CreateBucket", "{\"globalAlias\": \"" + BUCKET + "\"}"),
                "$.id");
        admin("POST", "AllowBucketKey", """
                {"bucketId": "%s", "accessKeyId": "%s", "permissions": {"read": true, "write": true, "owner": false}}"""
                .formatted(bucketId, ACCESS_KEY));
    }

    private static String admin(String method, String endpoint, String body) {
        URI uri = URI.create("http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(ADMIN_PORT) + "/v2/"
                + endpoint);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            // The admin API may answer 503 for a moment after the layout change
            for (int attempt = 1;; attempt++) {
                HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (attempt == 20) {
                    throw new IllegalStateException("Garage " + endpoint + " answered " + response.statusCode()
                            + ": " + response.body());
                }
                Thread.sleep(250);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String hex(int bytes) {
        byte[] random = new byte[bytes];
        RandomGenerator.of("L64X128MixRandom").nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}
