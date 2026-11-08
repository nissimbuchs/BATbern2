package ch.batbern.shared.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The search fold contract: every service that searches creates the SQL function
 * {@code batbern_fold} with a Flyway migration whose function body is byte-identical to the
 * canonical {@code db/search/batbern_fold.sql} in shared-kernel.
 *
 * <p>All services share one database, so the migrations {@code CREATE OR REPLACE} the same
 * function; a copy that differs by one byte would silently change every other service's search
 * the next time that service's migration ran. Each searching service calls {@link #verify} for its
 * migration in a plain unit test.
 */
public final class SearchFoldContract {

    /** Classpath location of the canonical function (shared-kernel main resources). */
    public static final String CANONICAL = "db/search/batbern_fold.sql";

    private static final String EXTENSION = "CREATE EXTENSION IF NOT EXISTS unaccent;";

    private SearchFoldContract() {
    }

    /**
     * Fails unless the migration at {@code migrationClasspathLocation} (e.g.
     * {@code db/migration/V27__search_fold.sql}) creates the {@code unaccent} extension and contains
     * the canonical function byte for byte.
     */
    public static void verify(String migrationClasspathLocation) {
        String canonical = read(CANONICAL).strip();
        String migration = read(migrationClasspathLocation);
        if (!migration.contains(EXTENSION)) {
            throw new AssertionError(migrationClasspathLocation + " must contain '" + EXTENSION + "'");
        }
        if (!migration.contains(canonical)) {
            throw new AssertionError(migrationClasspathLocation
                    + " must contain the canonical batbern_fold from shared-kernel " + CANONICAL
                    + " byte for byte (one shared database: every service replaces the same function).\n"
                    + "Expected:\n" + canonical);
        }
    }

    static String read(String classpathLocation) {
        ClassLoader loader = SearchFoldContract.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new AssertionError("Not on the classpath: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
