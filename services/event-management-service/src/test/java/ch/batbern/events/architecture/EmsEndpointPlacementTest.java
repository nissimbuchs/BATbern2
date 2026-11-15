package ch.batbern.events.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Endpoint placement rules that hold without exception, so they are not frozen (the frozen walls are
 * in {@link EmsModuleWallsTest}, whose store {@link FrozenRuleKeysTest} pins). Story 17.13: the
 * registration endpoints live in {@code RegistrationController}; {@code EventController} maps none,
 * neither by hand nor by implementing the generated {@code RegistrationsApi}. #1315:
 * {@code RegistrationController} uses no {@code RegistrationRepository}. Backlog story "EventController keeps
 * only HTTP": {@code EventController} uses event core and platform only.
 * {@code EmsModuleRulesFixtureTest} proves that every rule fires.
 */
@AnalyzeClasses(packages = EmsModuleWallsTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class EmsEndpointPlacementTest {

    @ArchTest
    static final ArchRule EVENT_CONTROLLER_MAPS_NO_REGISTRATION_ENDPOINT =
            EmsModuleRules.noRegistrationEndpointIn(EmsModuleWallsTest.ROOT, "EventController");

    /** #1315: the registration writes are transactional service methods, not repository calls. */
    @ArchTest
    static final ArchRule REGISTRATION_CONTROLLER_USES_NO_REPOSITORY =
            EmsModuleRules.controllerUsesNoRepository(EmsModuleWallsTest.ROOT, "RegistrationController",
                    "RegistrationRepository");

    /** EventController keeps only HTTP: event core services, no repository, no other module. */
    @ArchTest
    static final ArchRule EVENT_CONTROLLER_USES_ONLY_EVENT_CORE =
            EmsModuleRules.controllerUsesOnlyEventCore(EmsModuleWallsTest.ROOT, "EventController");
}
