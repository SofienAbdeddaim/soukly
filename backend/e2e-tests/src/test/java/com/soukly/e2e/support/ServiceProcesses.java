package com.soukly.e2e.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The packaged services of an end-to-end test, each run as a process from its executable jar, with its log in
 * {@code target/e2e-logs}. Stop them with {@link #stopAll()}.
 */
public final class ServiceProcesses {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final List<Process> processes = new ArrayList<>();
    private final Path logs;
    private final String scenario;

    /** @param scenario prefix of the log files, so that two test classes keep their own logs */
    public ServiceProcesses(String scenario) throws IOException {
        this.logs = Files.createDirectories(Path.of(System.getProperty("soukly.e2e.logs", "target/e2e-logs")));
        this.scenario = scenario;
    }

    /** Starts the jar of the module {@code name} (system property {@code soukly.e2e.<name>-jar}). */
    public void start(String name, Map<String, String> environment) throws IOException {
        Path jar = Path.of(System.getProperty("soukly.e2e." + name + "-jar"));
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(jar + " is missing: package the services first (./mvnw verify from "
                    + "backend/ builds them before this module)");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx384m", "-jar", jar.toString())
                .redirectErrorStream(true)
                .redirectOutput(log(name).toFile());
        builder.environment().putAll(environment);
        processes.add(builder.start());
    }

    /** Waits until {@code baseUrl/actuator/health} answers {@code 200}; fails with the end of the log otherwise. */
    public void awaitHealthy(String baseUrl, String name) throws Exception {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (processes.stream().anyMatch(process -> !process.isAlive())) {
                break;
            }
            try {
                HttpResponse<String> health = HTTP.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (health.statusCode() == 200) {
                    return;
                }
            } catch (IOException notYetListening) {
                // Still starting.
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(name + " did not start; see " + log(name) + ":\n" + tail(log(name)));
    }

    public void stopAll() {
        processes.forEach(Process::destroy);
    }

    /** RSA 2048 private key in PEM PKCS#8, like scripts/gen-jwt-key.sh writes it, in a temporary directory. */
    public static Path privateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        byte[] der = generator.generateKeyPair().getPrivate().getEncoded();
        Path directory = Files.createTempDirectory("soukly-e2e-jwt");
        directory.toFile().deleteOnExit();
        Path file = directory.resolve("jwt-private.pem");
        Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END PRIVATE KEY-----\n");
        file.toFile().deleteOnExit();
        return file;
    }

    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private Path log(String name) {
        return logs.resolve(scenario + "-" + name + ".log");
    }

    private static String tail(Path log) throws IOException {
        List<String> lines = Files.readAllLines(log);
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    }
}
