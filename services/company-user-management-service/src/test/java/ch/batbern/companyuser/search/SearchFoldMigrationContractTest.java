package ch.batbern.companyuser.search;

import ch.batbern.shared.test.SearchFoldContract;
import org.junit.jupiter.api.Test;

/** This service's fold migration carries the canonical batbern_fold byte for byte. */
class SearchFoldMigrationContractTest {

    @Test
    void should_createCanonicalBatbernFold() {
        SearchFoldContract.verify("db/migration/V27__search_fold.sql");
    }
}
