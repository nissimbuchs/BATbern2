package ch.batbern.shared.test;

/**
 * The company re-key contract (owner decision 2026-11-09, 17.0 deferred work): every service that
 * holds company keys re-keys them with a Flyway migration that contains the canonical
 * {@code db/company/company_key_changes.sql} from shared-kernel byte for byte.
 *
 * <p>All services share one database and deploy in any order. The canonical block computes the
 * mapping old key → new key once, into the shared table {@code company_key_changes}, from the
 * {@code companies} table as the first migration to run finds it (under an advisory lock); each
 * service then rewrites only its own tables from that table. A copy that differs by one byte could
 * compute a different key for the same company in another service. Each such service calls
 * {@link #verify} for its migration in a plain unit test.
 */
public final class CompanyKeyChangesContract {

    /** Classpath location of the canonical block (shared-kernel main resources). */
    public static final String CANONICAL = "db/company/company_key_changes.sql";

    private CompanyKeyChangesContract() {
    }

    /** Fails unless the migration at {@code migrationClasspathLocation} contains the canonical block. */
    public static void verify(String migrationClasspathLocation) {
        String canonical = SearchFoldContract.read(CANONICAL).strip();
        String migration = SearchFoldContract.read(migrationClasspathLocation);
        if (!migration.contains(canonical)) {
            throw new AssertionError(migrationClasspathLocation
                    + " must contain the canonical company re-key block from shared-kernel " + CANONICAL
                    + " byte for byte (one shared database: every service must derive the same keys).\n"
                    + "Expected:\n" + canonical);
        }
    }
}
