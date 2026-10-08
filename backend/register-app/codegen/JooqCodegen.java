import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.SchemaMappingType;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Generates jOOQ classes from the Flyway migrations.
 *
 * <p>Starts the same PostGIS image and init script as docker-compose, applies the migrations as
 * {@code register_owner} and runs jOOQ's code generator against the result. Run by Maven in the
 * {@code generate-sources} phase (see pom.xml); skipped when the migrations are unchanged.
 *
 * <p>Usage: {@code java -cp <test classpath> JooqCodegen.java <migrations dir> <init script> <output dir>}
 */
public final class JooqCodegen {

    private static final String IMAGE = "postgis/postgis:18-3.6";
    private static final String OWNER_PASSWORD = "codegen-owner";
    private static final String STAMP_FILE = ".inputs.sha256";
    /** Bump when the generator configuration below changes, so the next build regenerates. */
    private static final String GENERATOR_REVISION = "1";

    private JooqCodegen() {
    }

    public static void main(final String[] args) throws Exception {
        final Path migrations = Path.of(args[0]).toAbsolutePath();
        final Path initScript = Path.of(args[1]).toAbsolutePath();
        final Path output = Path.of(args[2]).toAbsolutePath();
        final String inputsHash = hashInputs(migrations, initScript);
        final Path stamp = output.resolve(STAMP_FILE);
        if (Files.exists(stamp) && Files.readString(stamp).equals(inputsHash)) {
            System.out.println("[jooq-codegen] Migrations unchanged; generated sources are up to date.");
            return;
        }

        try (PostgreSQLContainer postgis = new PostgreSQLContainer(
                DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))) {
            postgis.withEnv("REGISTER_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("REGISTER_APP_PASSWORD", "codegen-app")
                .withEnv("MARTIN_READER_PASSWORD", "codegen-martin")
                .withCopyFileToContainer(MountableFile.forHostPath(initScript, 0755),
                    "/docker-entrypoint-initdb.d/20-register.sh")
                .start();

            final String url = "jdbc:postgresql://" + postgis.getHost() + ":" + postgis.getMappedPort(5432) + "/register";

            Flyway.configure()
                .dataSource(url, "register_owner", OWNER_PASSWORD)
                .locations("filesystem:" + migrations)
                .schemas("register")
                .load()
                .migrate();

            GenerationTool.generate(new Configuration()
                .withJdbc(new Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl(url)
                    .withUser("register_owner")
                    .withPassword(OWNER_PASSWORD))
                .withGenerator(new Generator()
                    .withDatabase(new Database()
                        .withName("org.jooq.meta.postgres.PostgresDatabase")
                        .withSchemata(new SchemaMappingType().withInputSchema("register"))
                        .withExcludes("flyway_schema_history|scheduled_tasks")
                        .withForcedTypes(new ForcedType()
                            .withUserType("org.locationtech.jts.geom.Geometry")
                            .withBinding("org.ugaddress.db.binding.JtsGeometryBinding")
                            .withIncludeTypes("(?i:geometry.*)")))
                    .withGenerate(new Generate()
                        .withRecords(true)
                        .withPojos(false)
                        .withDaos(false)
                        .withJavaTimeTypes(true)
                        .withRoutines(true)
                        .withComments(true))
                    .withTarget(new Target()
                        .withPackageName("org.ugaddress.db.generated")
                        .withDirectory(output.toString())
                        .withClean(true))));
        }

        Files.writeString(stamp, inputsHash);
        System.out.println("[jooq-codegen] Generated jOOQ sources in " + output);
    }

    private static String hashInputs(final Path migrations, final Path initScript)
            throws IOException, NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final List<Path> files;
        try (Stream<Path> stream = Files.list(migrations)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        for (final Path file : files) {
            digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(file));
        }
        digest.update(Files.readAllBytes(initScript));
        digest.update((IMAGE + GENERATOR_REVISION).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }
}
