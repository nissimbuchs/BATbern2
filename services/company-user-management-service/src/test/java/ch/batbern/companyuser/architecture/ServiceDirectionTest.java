package ch.batbern.companyuser.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * No new CUMS client toward a downstream service, frozen. The existing profile-merge and
 * Mail Center clients are recorded in {@code src/test/resources/archunit_store} until they are
 * replaced (owner story in the module map); a fixed one leaves the store on the next run (commit that change). Never edit the store to
 * accept a new violation. See {@code docs/architecture/module-map.md}.
 */
@AnalyzeClasses(packages = "ch.batbern.companyuser", importOptions = ImportOption.DoNotIncludeTests.class)
class ServiceDirectionTest {

    @ArchTest
    static final ArchRule NO_CLIENT_TOWARD_DOWNSTREAM =
            freeze(ServiceDirectionRules.noClientTowardDownstreamService("ch.batbern.companyuser"));
}
