package ch.batbern.events.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Endpoint placement rules that hold without exception, so they are not frozen (the frozen walls are
 * in {@link EmsModuleWallsTest}, whose store {@link FrozenRuleKeysTest} pins). Story 17.13: the
 * registration endpoints live in {@code RegistrationController}; {@code EventController} maps none,
 * neither by hand nor by implementing the generated {@code RegistrationsApi}.
 * {@code EmsModuleRulesFixtureTest} proves that the rule fires.
 */
@AnalyzeClasses(packages = EmsModuleWallsTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class EmsEndpointPlacementTest {

    @ArchTest
    static final ArchRule EVENT_CONTROLLER_MAPS_NO_REGISTRATION_ENDPOINT =
            EmsModuleRules.noRegistrationEndpointIn(EmsModuleWallsTest.ROOT, "EventController");
}
