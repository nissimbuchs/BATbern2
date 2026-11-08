package ch.batbern.companyuser.security;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

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
@Import({TestAwsConfig.class})
@DisplayName("Production security chain in integration tests")
class ProductionChainIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER_ENDPOINT = "/api/v1/companies";

    /**
     * A complete body the service accepts and stores (201 for an organizer), so a rejection of the
     * same body can only come from security.
     */
    private static final String VALID_BODY = """
            {"name": "ChainTestCo", "displayName": "Chain Test Company"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void removeCreated() {
        jdbc.update("DELETE FROM companies WHERE name = ?", "ChainTestCo");
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
    @DisplayName("organizer passes the chain and method security: the write succeeds (201)")
    void should_create_when_organizerWrites() throws Exception {
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
