package ch.batbern.partners.client;

import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.exception.CompanyNotFoundException;
import ch.batbern.partners.exception.CompanyServiceException;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;

/**
 * Contract test of {@link CompanyServiceClient} against {@code companies-api} (Story 17.0b1): the
 * real client on the real {@code RestTemplate}, each request validated against the CUMS spec and
 * answered from its examples. Replaces the former CompanyServiceClientTest, whose hand-written
 * bodies were never checked against the spec.
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.CompanyServiceClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = "company-service.base-url=http://company-user-management:8080")
class CompanyServiceClientContractTest {

    private static final String COMPANIES = "docs/api/companies-api.openapi.yml";
    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String COMPANY_NAMES = "/internal/companies";
    private static final String COMPANY = "/api/v1/companies/{name}";
    private static final String TOKEN = "test-jwt-token-abc123";

    @Autowired
    private CompanyServiceClient companyServiceClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    private ContractServer server;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "RS256").claim("sub", "organizer.user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        Objects.requireNonNull(cacheManager.getCache("companyApiCache")).clear();
        server = ContractServer.bindTo(restTemplate, COMPANIES, CUMS_INTERNAL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        server.close();
    }

    @Test
    void should_returnCompany_when_cumsAnswersWithSpecExample() {
        server.expect(GET, COMPANY).respondWithExample();

        CompanyResponse company = companyServiceClient.getCompanyByName("swissitsolut");

        assertThat(company.getName()).isEqualTo("swissitsolut");
        assertThat(company.getDisplayName()).isEqualTo("Swiss IT Solutions AG");
        server.verify();
    }

    @Test
    void should_encodeCompanyName_asOnePathSegment() {
        server.expect(GET, COMPANY)
                .andExpect(r -> assertThat(r.getURI().getRawPath())
                        .isEqualTo("/api/v1/companies/Meier%2FPartner%20AG"))
                .respondWithExample();

        assertThat(companyServiceClient.getCompanyByName("Meier/Partner AG")).isNotNull();
        server.verify();
    }

    @Test
    void should_propagateJwt_when_callingCompanyService() {
        server.expect(GET, COMPANY)
                .andExpect(r -> assertThat(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer " + TOKEN))
                .respondWithExample();

        assertThat(companyServiceClient.getCompanyByName("TestCompany")).isNotNull();
        server.verify();
    }

    @Test
    void should_cacheResponse_when_httpCallSucceeds() {
        server.expect(GET, COMPANY).respondWithExample();

        CompanyResponse first = companyServiceClient.getCompanyByName("MicrosoftZH");
        CompanyResponse second = companyServiceClient.getCompanyByName("MicrosoftZH");

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        server.verify();
    }

    @Test
    void should_throwNotFound_when_companyUnknown() {
        server.expect(GET, COMPANY).respondWithStatus(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> companyServiceClient.getCompanyByName("NonExistent"))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining("NonExistent");
        server.verify();
    }

    @Test
    void should_throwServiceException_when_cumsFails() {
        server.expect(GET, COMPANY).respondWithStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThatThrownBy(() -> companyServiceClient.getCompanyByName("GoogleZH"))
                .isInstanceOf(CompanyServiceException.class)
                .hasMessageContaining("Company Service error");
        server.verify();
    }

    @Test
    void should_throwServiceException_when_connectionFails() {
        server.expect(GET, COMPANY).respondWith(request -> {
            throw new ResourceAccessException("Connection refused");
        });

        assertThatThrownBy(() -> companyServiceClient.getCompanyByName("GoogleZH"))
                .isInstanceOf(CompanyServiceException.class)
                .hasMessageContaining("Failed to connect");
        server.verify();
    }

    // Story 17.17: the partner search resolves display names to slugs through CUMS /internal/companies

    @Test
    void should_returnSlugsFromInternalCompanies_when_searching() {
        server.expect(GET, COMPANY_NAMES).withQueryParam("search", "Die Mobiliar").respondWithExample("matches");

        assertThat(companyServiceClient.findCompanyNames("Die Mobiliar"))
                .containsExactly("diemobiliar", "mobiliarag");
        server.verify();
    }

    @Test
    void should_encodeHostileInput_asOneSearchValue() {
        server.expect(GET, COMPANY_NAMES)
                .andExpect(r -> assertThat(r.getURI().getRawQuery())
                        .isEqualTo("search=100%25%20%28test%20%26x"))
                .respondWithExample("none");

        assertThat(companyServiceClient.findCompanyNames("100% (test &x")).isEmpty();
        server.verify();
    }

    @Test
    void should_throwServiceException_when_searchCannotReachCums() {
        server.expect(GET, COMPANY_NAMES).respondWith(request -> {
            throw new ResourceAccessException("Connection refused");
        });

        assertThatThrownBy(() -> companyServiceClient.findCompanyNames("ag"))
                .isInstanceOf(CompanyServiceException.class)
                .hasMessageContaining("company-name search");
        server.verify();
    }
}
