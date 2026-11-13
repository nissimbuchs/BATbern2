package ch.batbern.events.architecture;

import ch.batbern.events.architecture.fixtures.client.PartnerFixtureClient;
import ch.batbern.events.architecture.fixtures.client.UserFixtureClient;
import ch.batbern.events.architecture.fixtures.forwarding.FixtureForwarder;
import ch.batbern.events.architecture.fixtures.mailcenter.FixtureMailCenterService;
import ch.batbern.events.architecture.fixtures.mailcenter.render.FixtureMailPipeline;
import ch.batbern.events.architecture.fixtures.mailcenter.variables.FixtureMailVariable;
import ch.batbern.events.architecture.fixtures.notification.FixtureNotifier;
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
