package org.ugaddress.register;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real infrastructure for integration tests: the same PostGIS image and init script as docker-compose, and the
 * Record Store image as S3 store. Containers start once per JVM and are shared by all tests.
 */
public final class TestInfrastructure {

    /** Password of {@code register_owner} in tests. */
    public static final String OWNER_PASSWORD = "test-owner";

    /** Password of {@code register_app} in tests. */
    public static final String APP_PASSWORD = "test-app";

    /** S3 access key in tests. */
    public static final String S3_ACCESS_KEY = "testaccesskey0123456789";

    /** S3 secret key in tests. */
    public static final String S3_SECRET_KEY = "testsecretkey0123456789abcdefghijklmnop";

    /** Bucket for field photos in tests. */
    public static final String BUCKET = "field-photos-test";

    private static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
        .withEnv("REGISTER_OWNER_PASSWORD", OWNER_PASSWORD)
        .withEnv("REGISTER_APP_PASSWORD", APP_PASSWORD)
        .withEnv("MARTIN_READER_PASSWORD", "test-martin")
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of(System.getProperty("postgis.initScript")), 0755),
            "/docker-entrypoint-initdb.d/20-register.sh")
        .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    private static final GenericContainer<?> RECORD_STORE =
        new GenericContainer<>(DockerImageName.parse("ghcr.io/openelementslabs/record-store:0.2.1"))
            .withEnv("RECORD_STORE_ROOT_ACCESS_KEY", S3_ACCESS_KEY)
            .withEnv("RECORD_STORE_ROOT_SECRET_KEY", S3_SECRET_KEY)
            .withEnv("RECORD_STORE_CREDENTIAL_MASTER_KEY", "0123456789abcdef0123456789abcdef0123456789abcdef")
            .withEnv("RECORD_STORE_MANAGEMENT_SYSTEM_TOKEN", "fedcba9876543210fedcba9876543210fedcba9876543210")
            .withExposedPorts(7600, 7601)
            .waitingFor(Wait.forHttp("/ready").forPort(7601).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        POSTGIS.start();
        RECORD_STORE.start();
    }

    private TestInfrastructure() {
    }

    /**
     * Returns the JDBC URL of the register database.
     *
     * @return the URL
     */
    public static String jdbcUrl() {
        return "jdbc:postgresql://" + POSTGIS.getHost() + ":" + POSTGIS.getMappedPort(5432) + "/register";
    }

    /**
     * Registers Spring properties that point the application at the test containers.
     *
     * @param registry the registry
     */
    public static void register(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestInfrastructure::jdbcUrl);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("REGISTER_FIXTURES_DIR", () -> System.getProperty("fixtures.dir"));
        registry.add("register.storage.endpoint",
            () -> "http://" + RECORD_STORE.getHost() + ":" + RECORD_STORE.getMappedPort(7600));
        registry.add("register.storage.access-key", () -> S3_ACCESS_KEY);
        registry.add("register.storage.secret-key", () -> S3_SECRET_KEY);
        registry.add("register.storage.bucket", () -> BUCKET);
        // Tests authenticate with the MockMvc jwt() post-processor; the key set is never fetched.
        registry.add("register.security.jwk-set-uri", () -> "http://127.0.0.1:9/jwks");
    }
}
