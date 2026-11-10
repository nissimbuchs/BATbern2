package ch.batbern.events.client.impl;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.config.RestClientConfig;
import ch.batbern.events.dto.CompanyBasicDto;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract test of every public {@code /api/v1} call of {@link UserApiClientImpl} against the CUMS
 * specs (Story 17.0b1): the real client on the real {@code RestTemplate} bean, each request
 * validated against {@code users-api} / {@code companies-api}, answers taken from their examples.
 * The {@code /internal} calls stay in {@code UserApiClientImplInternalApiTest} until 0b2 specs them.
 */
@SpringBootTest(classes = {UserApiClientImpl.class, RestClientConfig.class, CacheConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "user-service.base-url=http://cums.test")
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("UserApiClientImpl public calls honour the CUMS contract")
class UserApiClientImplContractTest {

    private static final String USERS = "docs/api/users-api.openapi.yml";
    private static final String COMPANIES = "docs/api/companies-api.openapi.yml";
    private static final String TOKEN = "organizer-token";

    @Autowired
    private UserApiClient client;

    @Autowired
    private RestTemplate restTemplate;

    private ContractServer server;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "organizer").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (server != null) {
            server.close();
        }
    }

    private ContractServer bind(String spec) {
        server = ContractServer.bindTo(restTemplate, spec);
        return server;
    }

    private static void forwardsJwt(org.springframework.http.client.ClientHttpRequest request) {
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TOKEN);
    }

    // ------------------------------------------------------------------ users-api

    @Test
    void should_provisionSpeaker_withValidRequest() {
        bind(USERS).expect(POST, "/api/v1/users/provision")
                .andExpect(UserApiClientImplContractTest::forwardsJwt)
                .respondWithExample("created");
        ProvisionUserRequest request = new ProvisionUserRequest()
                .email("jane.smith@example.com").firstName("Jane").lastName("Smith")
                .role(ProvisionUserRequest.RoleEnum.SPEAKER);

        ProvisionUserResponse response = client.provisionUserWithRole(request);

        assertThat(response.getUsername()).isEqualTo("jane.smith");
        assertThat(response.getCreated()).isTrue();
        server.verify();
    }

    @Test
    void should_provisionWithEmailOnly_whenNamesUnknown() {
        bind(USERS).expect(POST, "/api/v1/users/provision").respondWithExample("alreadyExisted");

        ProvisionUserResponse response = client.provisionUserWithRole(new ProvisionUserRequest()
                .email("jane.smith@example.com").role(ProvisionUserRequest.RoleEnum.SPEAKER));

        assertThat(response.getCreated()).isFalse();
        server.verify();
    }

    @Test
    void should_patchProfile_withValidRequest() {
        bind(USERS).expect(PATCH, "/api/v1/users/{username}/profile")
                .andExpect(UserApiClientImplContractTest::forwardsJwt)
                .respondWithExample();

        UserResponse user = client.patchUserProfile("jane.smith", new PatchUserProfileRequest()
                .bio("Experienced security architect with 15 years in the industry."));

        assertThat(user.getId()).isEqualTo("jane.smith");
        assertThat(user.getBio()).startsWith("Experienced security architect");
        server.verify();
    }

    @Test
    void should_returnFreshTempPassword_when_cumsIssuesOne() {
        bind(USERS).expect(POST, "/api/v1/users/{username}/issue-invitation-credentials")
                .andExpect(UserApiClientImplContractTest::forwardsJwt)
                .respondWithExample("freshTempPassword");

        InvitationCredentialsResponse result = client.issueInvitationCredentials("jane.smith");

        assertThat(result.getAction()).isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        assertThat(result.getTemporaryPassword()).isEqualTo("Tk7!aB2@nx9pQ4#z");
        server.verify();
    }

    @Test
    void should_keepExistingPassword_when_userConfirmed() {
        bind(USERS).expect(POST, "/api/v1/users/{username}/issue-invitation-credentials")
                .respondWithExample("useExistingPassword");

        InvitationCredentialsResponse result = client.issueInvitationCredentials("jane.smith");

        assertThat(result.getAction()).isEqualTo(InvitationCredentialsResponse.ActionEnum.USE_EXISTING_PASSWORD);
        assertThat(result.getTemporaryPassword()).isNull();
        server.verify();
    }

    @Test
    void should_failFast_when_freshPasswordActionComesWithNullPassword() {
        bind(USERS).expect(POST, "/api/v1/users/{username}/issue-invitation-credentials")
                .respondWith(HttpStatus.OK, "{\"temporaryPassword\": null, \"action\": \"FRESH_TEMP_PASSWORD\"}");

        assertThatThrownBy(() -> client.issueInvitationCredentials("jane.smith"))
                .isInstanceOf(UserServiceException.class)
                .hasStackTraceContaining("no temporaryPassword");
    }

    // ------------------------------------------------------------------ companies-api

    @Test
    @DisplayName("#1072: getAllCompanies pages within the CUMS limit and collects every page")
    void should_collectCompaniesFromAllPages_withinSpecLimit() {
        ContractServer cums = bind(COMPANIES);
        cums.expect(GET, "/api/v1/companies").withQueryParam("page", "1")
                .andExpect(UserApiClientImplContractTest::forwardsJwt)
                .respondWith(HttpStatus.OK, """
                        {"data": [{"name": "AcmeZH", "isVerified": true, "createdAt": "2025-01-15T10:00:00Z",
                                   "updatedAt": "2025-01-15T10:00:00Z", "createdBy": "lead.organizer"},
                                  {"name": "GoogleZH", "isVerified": true, "createdAt": "2025-01-15T10:00:00Z",
                                   "updatedAt": "2025-01-15T10:00:00Z", "createdBy": "lead.organizer"}],
                         "pagination": {"page": 1, "limit": 100, "totalItems": 101, "totalPages": 2,
                                        "hasNext": true, "hasPrev": false}}""");
        cums.expect(GET, "/api/v1/companies").withQueryParam("page", "2").respondWithExample();

        List<CompanyBasicDto> companies = client.getAllCompanies();

        assertThat(companies).extracting(CompanyBasicDto::getName)
                .containsExactly("AcmeZH", "GoogleZH", "swissitsolut");
        cums.verify();
    }

    @Test
    void should_returnDisplayName_when_cumsSuppliesIt() {
        bind(COMPANIES).expect(GET, "/api/v1/companies/{name}")
                .andExpect(UserApiClientImplContractTest::forwardsJwt)
                .respondWithExample();

        assertThat(client.getCompanyDisplayName("swissitsolut")).isEqualTo("Swiss IT Solutions AG");
        server.verify();
    }

    @Test
    void should_fallBackToName_when_displayNameMissing() {
        bind(COMPANIES).expect(GET, "/api/v1/companies/{name}").respondWith(HttpStatus.OK, """
                {"name": "adesso", "isVerified": false, "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z",
                 "createdBy": "lead.organizer"}""");

        assertThat(client.getCompanyDisplayName("adesso")).isEqualTo("adesso");
        server.verify();
    }

    @Test
    void should_returnNull_when_companyUnknown() {
        bind(COMPANIES).expect(GET, "/api/v1/companies/{name}").respondWithStatus(HttpStatus.NOT_FOUND);

        assertThat(client.getCompanyDisplayName("ghost")).isNull();
        server.verify();
    }

    @Test
    void should_returnNull_when_cumsUnreachable() {
        bind(COMPANIES).expect(GET, "/api/v1/companies/{name}").respondWith(request -> {
            throw new ResourceAccessException("connection refused");
        });

        assertThat(client.getCompanyDisplayName("mobiliar")).isNull();
        server.verify();
    }

    @Test
    void should_notCallCums_when_slugBlank() {
        bind(COMPANIES);

        assertThat(client.getCompanyDisplayName(null)).isNull();
        assertThat(client.getCompanyDisplayName("")).isNull();
        assertThat(client.getCompanyDisplayName("   ")).isNull();
        server.verify();
    }
}
