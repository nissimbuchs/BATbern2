package ch.batbern.events.config;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.client.impl.UserApiClientImpl;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

/**
 * Self-test of the EMS-wide CUMS harness (Story 17.0b2): the user list honours the company filter,
 * and a test's own {@code ContractServer} routed with {@code onlyFor} leaves CUMS on the harness.
 */
@SpringBootTest(classes = {UserApiClientImpl.class, RestClientConfig.class, CacheConfig.class,
    CumsContractHarnessConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "user-service.base-url=" + CumsContractHarnessTest.CUMS)
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("CumsContractHarness")
class CumsContractHarnessTest {

    static final String CUMS = "http://cums.test";

    @Autowired
    private UserApiClient client;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private RestTemplate restTemplate;

    @AfterEach
    void reset() {
        cums.reset();
    }

    @Test
    @DisplayName("users of a company are listed for that company only; a wrong company gets none")
    void should_keyUserList_onRoleAndCompany() {
        cums.usersOfCompany("Meier AG", "PARTNER", "anna.meier");

        assertThat(client.getUsersByCompany("Meier AG", "PARTNER")).extracting(UserResponse::getId)
                .containsExactly("anna.meier");
        assertThat(client.getUsersByCompany("Other AG", "PARTNER")).isEmpty();
        assertThat(client.getPartnerUsernames()).isEmpty();
    }

    @Test
    @DisplayName("role-wide users (no company filter) are not a company's users")
    void should_listRoleWideUsers_withoutCompanyFilterOnly() {
        cums.usersWithRole("PARTNER", "anna.meier");

        assertThat(client.getPartnerUsernames()).containsExactly("anna.meier");
        assertThat(client.getUsersByCompany("Meier AG", "PARTNER")).isEmpty();
    }

    @Test
    @DisplayName("a per-test ContractServer routed with onlyFor leaves CUMS calls on the harness")
    void should_keepCums_when_testRoutesOnlyItsOwnProvider() {
        // Any provider spec other than CUMS will do; ems-internal needs no JWT, so no
        // missing-authorization exemption (Epic 17, entry 8 removed the last partners-api binding).
        try (ContractServer other = ContractServer.bindTo(restTemplate, "docs/api/internal/ems-internal.openapi.yml")
                .onlyFor(uri -> !uri.toString().startsWith(CUMS))) {
            other.expect(GET, "/internal/events/{eventCode}/schedule").respondWithExample("agendaPublished");

            assertThat(restTemplate.getForObject("http://ems.test/internal/events/BATbern142/schedule", String.class))
                    .contains("BATbern142");
            assertThat(client.getUserByUsername("anna.meier").getEmail()).isEqualTo("anna.meier@test.com");
            other.verify();
        }
    }
}
