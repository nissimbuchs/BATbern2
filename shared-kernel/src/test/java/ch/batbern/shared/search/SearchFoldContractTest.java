package ch.batbern.shared.search;

import ch.batbern.shared.test.SearchFoldContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SearchFoldContract: a service migration carries the canonical batbern_fold byte for byte")
class SearchFoldContractTest {

    @Test
    void should_pass_when_migrationContainsCanonicalFunction() {
        assertThatCode(() -> SearchFoldContract.verify("db/search-contract/V1__matches.sql"))
                .doesNotThrowAnyException();
    }

    @Test
    void should_fail_when_migrationDivergesByOneByte() {
        assertThatThrownBy(() -> SearchFoldContract.verify("db/search-contract/V1__diverged.sql"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("byte for byte");
    }

    @Test
    void should_fail_when_migrationDoesNotCreateUnaccent() {
        assertThatThrownBy(() -> SearchFoldContract.verify("db/search-contract/V1__no_extension.sql"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unaccent");
    }

    @Test
    void should_fail_when_migrationIsMissing() {
        assertThatThrownBy(() -> SearchFoldContract.verify("db/search-contract/V9__absent.sql"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Not on the classpath");
    }
}
