package ch.batbern.events.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Data ownership rules that hold without exception, so they are not frozen (the frozen walls are in
 * {@link EmsModuleWallsTest}, whose store {@link FrozenRuleKeysTest} pins). Story 17.11: EMS keeps its
 * files in {@code event_management_uploads} and maps no table of company-user-management.
 */
@AnalyzeClasses(packages = EmsModuleWallsTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class EmsDataOwnershipTest {

    @ArchTest
    static final ArchRule NO_ENTITY_MAPS_LOGOS = EmsModuleRules.noEntityMapsLogos(EmsModuleWallsTest.ROOT);
}
