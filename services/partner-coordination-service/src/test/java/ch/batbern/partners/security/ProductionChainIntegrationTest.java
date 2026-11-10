package ch.batbern.partners.security;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production security chain runs in every integration test (17.0b3). Until then the `test`
 * profile replaced it with a permit-all chain, so an anonymous or wrong-role caller reached the
 * controller in tests while production answered 401 (#1012, #1134, #1145).
 *
 * <p>Roles come from the {@code custom:role} claim through the service's real
 * {@link JwtAuthenticationConverter}, the same path a Cognito token takes in production.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Production security chain in integration tests")
class ProductionChainIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER_ENDPOINT = "/api/v1/partners";

    /** A request body the service accepts and stores (201), so a rejection can only come from security. */
    private static final String VALID_BODY = """
            {"companyName": "ChainTestCo", "partnershipLevel": "GOLD", "partnershipStartDate": "2027-01-01"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private RestTemplate restTemplate;

    /** CUMS behind the real CompanyServiceClient, answering per companies-api. */
    private ContractServer cums;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();
        cums = ContractServer.bindTo(restTemplate, "docs/api/companies-api.openapi.yml")
                .ignoringMissingAuthorization();
        cums.expect(HttpMethod.GET, "/api/v1/companies/{name}").times(ExpectedCount.manyTimes())
                .respondWith(HttpStatus.OK, """
                        {"name": "ChainTestCo", "displayName": "Chain Test Co", "isVerified": true,
                         "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z", "createdBy": "lead.organizer"}""");
    }

    @AfterEach
    void cleanUp() {
        cums.close();
        partnerRepository.deleteAll();
    }

    @Test
    @DisplayName("anonymous write answers 401")
    void should_return401_when_anonymousWrites() throws Exception {
        mockMvc.perform(post(ORGANIZER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("attendee on an organizer write answers 403")
    void should_return403_when_attendeeWritesToOrganizerEndpoint() throws Exception {
        mockMvc.perform(post(ORGANIZER_ENDPOINT)
                        .with(tokenWithRole("ATTENDEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("organizer passes the chain and reaches the controller")
    void should_reachController_when_organizerWrites() throws Exception {
        // The same body as above: 201 proves the chain and @PreAuthorize let the organizer through
        // (argument validation runs before method security, so a 400 would prove nothing).
        mockMvc.perform(post(ORGANIZER_ENDPOINT)
                        .with(tokenWithRole("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    private JwtRequestPostProcessor tokenWithRole(String role) {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt()
                .jwt(token -> token.subject("chain-test-" + role.toLowerCase())
                        .claim("custom:role", role)
                        .claim("custom:username", "chain.test." + role.toLowerCase()))
                .authorities(realConversion);
    }
}
