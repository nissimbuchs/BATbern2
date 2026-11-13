package ch.batbern.events.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Data ownership rules that hold without exception, so they are not frozen (the frozen walls are in
 * {@link EmsModuleWallsTest}, whose store {@link FrozenRuleKeysTest} pins). Story 17.11: EMS keeps its
 * files in {@code event_management_uploads} and maps no upload table of another service
 * ({@code company_user_uploads}, or {@code logos}, dropped by #1307).
 */
@AnalyzeClasses(packages = EmsModuleWallsTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class EmsDataOwnershipTest {

    @ArchTest
    static final ArchRule NO_ENTITY_MAPS_FOREIGN_UPLOAD_TABLE =
            EmsModuleRules.noEntityMapsForeignUploadTable(EmsModuleWallsTest.ROOT);
}
