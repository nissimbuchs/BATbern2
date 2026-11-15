package ch.batbern.events.architecture;

import ch.batbern.events.architecture.fixtures.client.PartnerFixtureClient;
import ch.batbern.events.architecture.fixtures.client.UserFixtureClient;
import ch.batbern.events.architecture.fixtures.eventcontrollerhttp.FixtureEventRepository;
import ch.batbern.events.architecture.fixtures.eventcontrollerhttp.SessionFixtureService;
import ch.batbern.events.architecture.fixtures.eventcontrollerhttp.analytics.api.generated.FixtureReportingApi;
import ch.batbern.events.architecture.fixtures.eventcoreinherited.FixtureRegistrationsApi;
import ch.batbern.events.architecture.fixtures.exceptionmapping.exception.FixtureAgendaFullException;
import ch.batbern.events.architecture.fixtures.exceptionmapping.exception.FixtureNotFoundException;
import ch.batbern.events.architecture.fixtures.exceptionmapping.exception.FixtureStatusException;
import ch.batbern.events.architecture.fixtures.exceptionmapping.exception.GlobalExceptionHandlerSupport;
import ch.batbern.events.architecture.fixtures.forwarding.FixtureForwarder;
import ch.batbern.events.architecture.fixtures.mailcenter.FixtureMailCenterService;
import ch.batbern.events.architecture.fixtures.mailcenter.render.FixtureMailPipeline;
import ch.batbern.events.architecture.fixtures.mailcenter.variables.FixtureMailVariable;
import ch.batbern.events.architecture.fixtures.notification.FixtureNotifier;
import ch.batbern.events.architecture.fixtures.registrationcontroller.RegistrationController;
import ch.batbern.events.architecture.fixtures.registrationcontroller.RegistrationRepository;
import ch.batbern.events.architecture.fixtures.registrations.FixtureRegistrationService;
import ch.batbern.events.architecture.fixtures.uploads.FixtureCumsUploadEntity;
import ch.batbern.events.architecture.fixtures.uploads.FixtureLogoEntity;
import ch.batbern.events.architecture.fixtures.uploads.FixtureUploadEntity;
import ch.batbern.events.architecture.fixtures.watch.FixtureWatchService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.ViolationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that every rule kind of {@link EmsModuleWallsTest} fires, on fixture classes, so the gate
 * does not rest on a manual check.
 */
@DisplayName("EMS ArchUnit rules fire on fixtures")
class EmsModuleRulesFixtureTest {

    private static final String ROOT = "ch.batbern.events.architecture.fixtures";

    private static final JavaClasses ALL = new ClassFileImporter().importPackages(ROOT);

    /** Fixtures named EventController (the rules go by simple name), hence qualified. */
    private static final Class<?> HAND_MAPPED = ch.batbern.events.architecture.fixtures.eventcore.EventController.class;
    private static final Class<?> INHERITING =
            ch.batbern.events.architecture.fixtures.eventcoreinherited.EventController.class;
    private static final Class<?> CLEAN = ch.batbern.events.architecture.fixtures.eventcoreclean.EventController.class;

    /** The "EventController uses event core only" rule: one root that breaks it, one that keeps it. */
    private static final String HTTP_ROOT = ROOT + ".eventcontrollerhttp";
    private static final String HTTP_CLEAN_ROOT = ROOT + ".eventcontrollerhttpclean";
    private static final JavaClasses HTTP = new ClassFileImporter().importPackages(HTTP_ROOT);
    private static final JavaClasses HTTP_CLEAN = new ClassFileImporter().importPackages(HTTP_CLEAN_ROOT);

    /** The exception rules take {@code <root>.exception}: one root that breaks them, one that keeps them. */
    private static final String MAPPING_ROOT = ROOT + ".exceptionmapping";
    private static final String MAPPING_CLEAN_ROOT = ROOT + ".exceptionmappingclean";
    private static final JavaClasses MAPPING = new ClassFileImporter().importPackages(MAPPING_ROOT);
    private static final JavaClasses MAPPING_CLEAN = new ClassFileImporter().importPackages(MAPPING_CLEAN_ROOT);

    @Test
    @DisplayName("should_fail_when_classOutsideEdgeModuleUsesIt")
    void should_fail_when_classOutsideEdgeModuleUsesIt() {
        assertViolations(mailCenter(), ALL, FixtureRegistrationService.class.getName());
        assertViolations(edge("usermerge"), ALL, FixtureRegistrationService.class.getName());
        assertViolations(edge("watch"), ALL, FixtureNotifier.class.getName());
    }

    @Test
    @DisplayName("should_fail_when_classOutsideMailCenterUsesForwarding")
    void should_fail_when_classOutsideMailCenterUsesForwarding() {
        EvaluationResult result = mailCenter().evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureRegistrationService.class.getName())
                        && line.contains(FixtureForwarder.class.getName()))
                .noneMatch(line -> line.startsWith("Method <" + FixtureForwarder.class.getName()));
    }

    @Test
    @DisplayName("should_allow_when_classUsesMailCoreInsideMailcenter")
    void should_allow_when_classUsesMailCoreInsideMailcenter() {
        EvaluationResult result = mailCenter().evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureMailCenterService.class.getName()))
                .noneMatch(line -> line.contains(FixtureMailPipeline.class.getName()));
    }

    @Test
    @DisplayName("should_pass_when_onlyTheModuleUsesItself")
    void should_pass_when_onlyTheModuleUsesItself() {
        JavaClasses clean = new ClassFileImporter().importClasses(
                FixtureMailCenterService.class, FixtureForwarder.class, UserFixtureClient.class);

        assertThat(mailCenter().evaluate(clean).hasViolation()).isFalse();
        assertThat(EmsModuleRules.notificationUsesNoEdgeModule(ROOT).evaluate(clean).hasViolation()).isFalse();
        assertThat(EmsModuleRules.mailCoreUsesNoMailCenter(ROOT).evaluate(clean).hasViolation()).isFalse();
        assertThat(EmsModuleRules.noClientTowardPartnerService(ROOT).evaluate(clean).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("should_fail_when_notificationDependsOnEdgeModule")
    void should_fail_when_notificationDependsOnEdgeModule() {
        EvaluationResult result = EmsModuleRules.notificationUsesNoEdgeModule(ROOT).evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureNotifier.class.getName()))
                .noneMatch(line -> line.contains(FixtureRegistrationService.class.getName()));
    }

    @Test
    @DisplayName("should_fail_when_mailCoreUsesMailCenter")
    void should_fail_when_mailCoreUsesMailCenter() {
        EvaluationResult result = EmsModuleRules.mailCoreUsesNoMailCenter(ROOT).evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureMailVariable.class.getName()))
                .noneMatch(line -> line.startsWith("Method <" + FixtureMailPipeline.class.getName()));
    }

    @Test
    @DisplayName("should_fail_when_clientTowardPartnerServiceExists")
    void should_fail_when_clientTowardPartnerServiceExists() {
        EvaluationResult result = EmsModuleRules.noClientTowardPartnerService(ROOT).evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(PartnerFixtureClient.class.getName()))
                .noneMatch(line -> line.contains(UserFixtureClient.class.getName()));
    }

    @Test
    @DisplayName("should_fail_when_entityMapsUploadTableOfAnotherService")
    void should_fail_when_entityMapsUploadTableOfAnotherService() {
        EvaluationResult result = EmsModuleRules.noEntityMapsForeignUploadTable(ROOT).evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureLogoEntity.class.getName()))
                .anyMatch(line -> line.contains(FixtureCumsUploadEntity.class.getName()))
                .noneMatch(line -> line.contains(FixtureUploadEntity.class.getName()));
        assertThat(EmsModuleRules.noEntityMapsForeignUploadTable(ROOT)
                .evaluate(new ClassFileImporter().importClasses(FixtureUploadEntity.class)).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("should_fail_when_eventControllerMapsRegistrationEndpoint")
    void should_fail_when_eventControllerMapsRegistrationEndpoint() {
        EvaluationResult result = EmsModuleRules.noRegistrationEndpointIn(ROOT, "EventController").evaluate(ALL);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(HAND_MAPPED.getName())
                        && line.contains("/events/{eventCode}/registrations"))
                .noneMatch(line -> line.contains(CLEAN.getName()));
    }

    @Test
    @DisplayName("should_fail_when_eventControllerInheritsRegistrationEndpoint")
    void should_fail_when_eventControllerInheritsRegistrationEndpoint() {
        EvaluationResult result = EmsModuleRules.noRegistrationEndpointIn(ROOT, "EventController")
                .evaluate(new ClassFileImporter().importClasses(
                        INHERITING, FixtureRegistrationsApi.class));

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(INHERITING.getName())
                        && line.contains("/events/{eventCode}/my-registration")
                        && line.contains(FixtureRegistrationsApi.class.getName()));
    }

    @Test
    @DisplayName("should_pass_when_eventControllerMapsNoRegistrationEndpoint")
    void should_pass_when_eventControllerMapsNoRegistrationEndpoint() {
        // Positive control: the same rule reports the hand-mapped fixture.
        assertThat(EmsModuleRules.noRegistrationEndpointIn(ROOT, "EventController")
                .evaluate(new ClassFileImporter().importClasses(HAND_MAPPED))
                .hasViolation()).isTrue();

        assertThat(EmsModuleRules.noRegistrationEndpointIn(ROOT, "EventController")
                .evaluate(new ClassFileImporter().importClasses(CLEAN))
                .hasViolation()).isFalse();
    }

    @Test
    @DisplayName("should_fail_when_controllerUsesRepository")
    void should_fail_when_controllerUsesRepository() {
        EvaluationResult result = EmsModuleRules.controllerUsesNoRepository(ROOT, "RegistrationController",
                "RegistrationRepository").evaluate(new ClassFileImporter().importClasses(
                        RegistrationController.class, RegistrationRepository.class));

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(RegistrationController.class.getName())
                        && line.contains(RegistrationRepository.class.getName()));
    }

    @Test
    @DisplayName("should_fail_when_noClassHasTheControllerName")
    void should_fail_when_noClassHasTheControllerName() {
        // Positive control: a controller of that name that uses no repository passes.
        assertThat(EmsModuleRules.controllerUsesNoRepository(ROOT, "FixtureRegistrationService",
                "RegistrationRepository").evaluate(new ClassFileImporter().importClasses(
                        FixtureRegistrationService.class)).hasViolation()).isFalse();

        assertThatThrownBy(() -> EmsModuleRules.controllerUsesNoRepository(ROOT, "RenamedController",
                "RegistrationRepository").check(new ClassFileImporter().importClasses(RegistrationController.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("failed to check any classes");
    }

    @Test
    @DisplayName("should_fail_when_eventControllerUsesRepository")
    void should_fail_when_eventControllerUsesRepository() {
        EvaluationResult result = EmsModuleRules.controllerUsesOnlyEventCore(HTTP_ROOT, "EventController")
                .evaluate(HTTP);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(HTTP_ROOT + ".EventController")
                        && line.contains(FixtureEventRepository.class.getName()));
    }

    @Test
    @DisplayName("should_fail_when_eventControllerUsesClassOfAnotherModule")
    void should_fail_when_eventControllerUsesClassOfAnotherModule() {
        EvaluationResult result = EmsModuleRules.controllerUsesOnlyEventCore(HTTP_ROOT, "EventController")
                .evaluate(HTTP);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(SessionFixtureService.class.getName()))
                .anyMatch(line -> line.contains(FixtureReportingApi.class.getName()));
    }

    @Test
    @DisplayName("should_pass_when_eventControllerUsesOnlyEventCoreAndPlatform")
    void should_pass_when_eventControllerUsesOnlyEventCoreAndPlatform() {
        // Positive control: the same rule reports the breaking fixture.
        assertThat(EmsModuleRules.controllerUsesOnlyEventCore(HTTP_ROOT, "EventController")
                .evaluate(HTTP).hasViolation()).isTrue();

        assertThat(EmsModuleRules.controllerUsesOnlyEventCore(HTTP_CLEAN_ROOT, "EventController")
                .evaluate(HTTP_CLEAN).hasViolation()).isFalse();
        assertThatThrownBy(() -> EmsModuleRules.controllerUsesOnlyEventCore(HTTP_CLEAN_ROOT, "RenamedController")
                .check(HTTP_CLEAN))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("failed to check any classes");
    }

    @Test
    @DisplayName("should_classifyLikeTheModuleScript_when_givenClassNames")
    void should_classifyLikeTheModuleScript_when_givenClassNames() {
        assertThat(EmsModuleNames.moduleOf("service", "EventReadService")).isEqualTo(EmsModuleNames.EVENT_CORE);
        assertThat(EmsModuleNames.moduleOf("security", "CallerRoles")).isEqualTo(EmsModuleNames.PLATFORM);
        assertThat(EmsModuleNames.moduleOf("service", "SessionEventExpansion")).isEqualTo("sessions");
        assertThat(EmsModuleNames.moduleOf("service", "RegistrationEventAdapter")).isEqualTo("registrations");
        assertThat(EmsModuleNames.moduleOf("service", "TeaserImageEventExpansion")).isEqualTo("media");
        assertThat(EmsModuleNames.moduleOf("service", "TopicEventExpansion")).isEqualTo("topics_ai");
        assertThat(EmsModuleNames.moduleOf("service", "SpeakerMetricsExpansion")).isEqualTo("speakers");
        assertThat(EmsModuleNames.moduleOf("service", "EventAnalyticsService")).isEqualTo("analytics");
        assertThat(EmsModuleNames.moduleOf("publishing", "PublicEventShaper")).isEqualTo("publishing");
        assertThat(EmsModuleNames.moduleOf("service", "MailPipeline")).isEqualTo("mail_core");
        assertThat(EmsModuleNames.moduleOf("mailcenter", "ConversationService")).isEqualTo("mail_center");
        assertThat(EmsModuleNames.moduleOf("notification", "NotificationRepository")).isEqualTo("notif_core");
        assertThat(EmsModuleNames.moduleOf("service", "AiDescriptionService")).isEqualTo("ai");
    }

    @Test
    @DisplayName("should_fail_when_globalExceptionHandlerUsesDomainException")
    void should_fail_when_globalExceptionHandlerUsesDomainException() {
        EvaluationResult result = EmsModuleRules.globalExceptionHandlerUsesOnlyExceptionBase(MAPPING_ROOT)
                .evaluate(MAPPING);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureAgendaFullException.class.getName()))
                .anyMatch(line -> line.contains(GlobalExceptionHandlerSupport.class.getName()))
                .anyMatch(line -> line.contains(MAPPING_ROOT + ".other.DomainException"))
                // The base, the body builder and the handler's nested class are allowed.
                .noneMatch(line -> line.contains("handleDomainException"))
                .noneMatch(line -> line.contains("$Nested"));
    }

    @Test
    @DisplayName("should_pass_when_globalExceptionHandlerUsesOnlyExceptionBase")
    void should_pass_when_globalExceptionHandlerUsesOnlyExceptionBase() {
        // Positive control: the same rule reports the handler that maps by hand.
        assertThat(EmsModuleRules.globalExceptionHandlerUsesOnlyExceptionBase(MAPPING_ROOT)
                .evaluate(MAPPING).hasViolation()).isTrue();

        assertThat(EmsModuleRules.globalExceptionHandlerUsesOnlyExceptionBase(MAPPING_CLEAN_ROOT)
                .evaluate(MAPPING_CLEAN).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("should_fail_when_exceptionDoesNotExtendDomainExceptionOrHasResponseStatus")
    void should_fail_when_exceptionDoesNotExtendDomainExceptionOrHasResponseStatus() {
        EvaluationResult result = EmsModuleRules.exceptionsExtendDomainException(MAPPING_ROOT).evaluate(MAPPING);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(FixtureNotFoundException.class.getName()))
                .anyMatch(line -> line.contains(FixtureStatusException.class.getName()))
                .noneMatch(line -> line.contains(FixtureAgendaFullException.class.getName()));
    }

    @Test
    @DisplayName("should_pass_when_everyExceptionExtendsDomainException")
    void should_pass_when_everyExceptionExtendsDomainException() {
        // Positive control: the same rule reports the breaking fixtures.
        assertThat(EmsModuleRules.exceptionsExtendDomainException(MAPPING_ROOT)
                .evaluate(MAPPING).hasViolation()).isTrue();

        assertThat(EmsModuleRules.exceptionsExtendDomainException(MAPPING_CLEAN_ROOT)
                .evaluate(MAPPING_CLEAN).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("should_failOnNewViolation_and_shrinkStore_when_frozen")
    void should_failOnNewViolation_and_shrinkStore_when_frozen() {
        InMemoryViolationStore store = new InMemoryViolationStore();
        ArchRule rule = FreezingArchRule.freeze(edge("watch"))
                .persistIn(store);
        JavaClasses withNotifier = new ClassFileImporter()
                .importClasses(FixtureNotifier.class, FixtureWatchService.class);
        JavaClasses withNewViolation = new ClassFileImporter()
                .importClasses(FixtureNotifier.class, FixtureWatchService.class, WatchUserFixture.class);
        JavaClasses fixed = new ClassFileImporter().importClasses(FixtureWatchService.class);

        rule.check(withNotifier);
        assertThat(store.violations()).anyMatch(v -> v.contains(FixtureNotifier.class.getName()));

        assertThatThrownBy(() -> rule.check(withNewViolation))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(WatchUserFixture.class.getName())
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(FixtureNotifier.class.getName()));

        rule.check(fixed);
        assertThat(store.violations()).isEmpty();
    }

    private static ArchRule mailCenter() {
        return EmsModuleRules.edgeModuleIsolated(ROOT, "mail center", EmsModuleRules.MAIL_CENTER);
    }

    private static ArchRule edge(String module) {
        return EmsModuleRules.edgeModuleIsolated(ROOT, module, List.of(module));
    }

    private static void assertViolations(ArchRule rule, JavaClasses classes, String offender) {
        assertThatThrownBy(() -> rule.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(offender);
    }

    /** A second class outside {@code watch} that uses it: the "new violation" after freezing. */
    static class WatchUserFixture {
        void use(FixtureWatchService watch) {
            watch.push();
        }
    }

    /** Store kept in memory so the freeze test never touches the committed store. */
    static class InMemoryViolationStore implements ViolationStore {
        private final Map<String, List<String>> rules = new HashMap<>();

        @Override
        public void initialize(Properties properties) {
        }

        @Override
        public boolean contains(ArchRule rule) {
            return rules.containsKey(rule.getDescription());
        }

        @Override
        public void save(ArchRule rule, List<String> violations) {
            rules.put(rule.getDescription(), new ArrayList<>(violations));
        }

        @Override
        public List<String> getViolations(ArchRule rule) {
            return rules.getOrDefault(rule.getDescription(), List.of());
        }

        List<String> violations() {
            return rules.values().stream().flatMap(List::stream).toList();
        }
    }
}
