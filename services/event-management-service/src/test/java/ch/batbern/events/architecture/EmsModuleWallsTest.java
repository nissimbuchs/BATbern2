package ch.batbern.events.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * EMS module walls and service direction, frozen. Today's violations are recorded in
 * {@code src/test/resources/archunit_store}; a new one fails this test, a fixed one leaves the
 * store on the next run (commit that change). Never edit the store to accept a new violation; a
 * new or renamed rule would silently accept its current violations, {@link FrozenRuleKeysTest} stops that.
 * See {@code docs/architecture/module-map.md}.
 */
@AnalyzeClasses(packages = EmsModuleWallsTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class EmsModuleWallsTest {

    static final String ROOT = "ch.batbern.events";

    @ArchTest
    static final ArchRule MAIL_CENTER_IS_EDGE =
            freeze(EmsModuleRules.edgeModuleIsolated(ROOT, "mail center", EmsModuleRules.MAIL_CENTER));

    @ArchTest
    static final ArchRule WATCH_IS_EDGE = freeze(EmsModuleRules.edgeModuleIsolated(ROOT, "watch", List.of("watch")));

    @ArchTest
    static final ArchRule USERMERGE_IS_EDGE =
            freeze(EmsModuleRules.edgeModuleIsolated(ROOT, "usermerge", List.of("usermerge")));

    @ArchTest
    static final ArchRule MAIL_CORE_USES_NO_MAIL_CENTER = freeze(EmsModuleRules.mailCoreUsesNoMailCenter(ROOT));

    @ArchTest
    static final ArchRule NOTIFICATION_USES_NO_EDGE = freeze(EmsModuleRules.notificationUsesNoEdgeModule(ROOT));

    @ArchTest
    static final ArchRule NO_CLIENT_TOWARD_PCS = freeze(EmsModuleRules.noClientTowardPartnerService(ROOT));
}
