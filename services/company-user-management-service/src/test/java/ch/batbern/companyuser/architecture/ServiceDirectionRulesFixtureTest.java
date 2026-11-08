package ch.batbern.companyuser.architecture;

import ch.batbern.companyuser.architecture.fixtures.client.EventManagementFixtureClient;
import ch.batbern.companyuser.architecture.fixtures.client.EventManagementFixtureClientImpl;
import ch.batbern.companyuser.architecture.fixtures.client.HttpPartnerFixtureClient;
import ch.batbern.companyuser.architecture.fixtures.client.PartnerFixtureService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the CUMS direction rule fires on fixture classes. */
@DisplayName("CUMS service-direction rule fires on fixtures")
class ServiceDirectionRulesFixtureTest {

    private static final String ROOT = "ch.batbern.companyuser.architecture.fixtures";

    @Test
    @DisplayName("should_fail_when_clientTowardDownstreamServiceExists")
    void should_fail_when_clientTowardDownstreamServiceExists() {
        JavaClasses classes = new ClassFileImporter().importPackages(ROOT);

        EvaluationResult result = ServiceDirectionRules.noClientTowardDownstreamService(ROOT).evaluate(classes);

        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(EventManagementFixtureClient.class.getName()))
                .anyMatch(line -> line.contains(EventManagementFixtureClientImpl.class.getName()))
                .anyMatch(line -> line.contains(HttpPartnerFixtureClient.class.getName()))
                .noneMatch(line -> line.contains(PartnerFixtureService.class.getName()));
    }
}
