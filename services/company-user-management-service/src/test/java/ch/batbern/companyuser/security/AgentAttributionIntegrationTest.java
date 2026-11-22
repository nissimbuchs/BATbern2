package ch.batbern.companyuser.security;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agentic interaction 0.2: an agent token is recorded as {@code agent:<run> for <username>} in a
 * who-acted column, while ownership still reads the username. Real agent tokens ({@link TestTokens})
 * go through the production chain and the real decoder; the agent's roles come from
 * {@code role_assignments} by its {@code sub} ({@code JwtRolesConverter}), as in production.
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS: agent actions are recorded as agent:<run> for <username>")
class AgentAttributionIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "agentattr.organizer";
    private static final String ORGANIZER_SUB = "a0000000-0000-4000-8000-0000000000a1";
    private static final String ATTENDEE = "agentattr.attendee";
    private static final String ATTENDEE_SUB = "a0000000-0000-4000-8000-0000000000a2";
    private static final String RUN = "run-cums-1";
    private static final String DISPLAY_AGENT = "Agent Attribution Brünnimann AG";
    private static final String DISPLAY_PERSON = "Agent Attribution Person AG";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @BeforeEach
    void seed() {
        cleanUp();
        userRepository.saveAndFlush(user(ORGANIZER, ORGANIZER_SUB, "Hông", "Ân", Role.ORGANIZER));
        userRepository.saveAndFlush(user(ATTENDEE, ATTENDEE_SUB, "Sean", "O’Neill", Role.ATTENDEE));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM companies WHERE display_name IN (?, ?)", DISPLAY_AGENT, DISPLAY_PERSON);
        userRepository.findByUsername(ORGANIZER).ifPresent(userRepository::delete);
        userRepository.findByUsername(ATTENDEE).ifPresent(userRepository::delete);
        userRepository.flush();
    }

    private static User user(String username, String sub, String first, String last, Role role) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName(first)
                .lastName(last)
                .cognitoUserId(sub)
                .roles(new HashSet<>(Set.of(role)))
                .build();
    }

    private static String bearer(String sub, String username) {
        return "Bearer " + TestTokens.agentToken(sub, username, RUN, "organizer");
    }

    private String createdByOf(String displayName) {
        return jdbc.queryForObject("SELECT created_by FROM companies WHERE display_name = ?", String.class,
                displayName);
    }

    @Test
    @DisplayName("an organizer's agent creates a company: created_by = agent:<run> for <username>")
    void should_recordAgentActor_when_agentCreatesCompany() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "AgentAttr", "displayName", DISPLAY_AGENT));

        String response = mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER_SUB, ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(createdByOf(DISPLAY_AGENT)).isEqualTo("agent:" + RUN + " for " + ORGANIZER);
        JsonNode json = objectMapper.readTree(response);
        assertThat(json.path("createdBy").asText()).isEqualTo("agent:" + RUN + " for " + ORGANIZER);
    }

    @Test
    @DisplayName("positive control: the same organizer with a Cognito token is recorded by username")
    void should_recordUsername_when_personCreatesCompany() throws Exception {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        String body = objectMapper.writeValueAsString(
                Map.of("name", "PersonAttr", "displayName", DISPLAY_PERSON));

        mockMvc.perform(post("/api/v1/companies")
                        .with(jwt().jwt(t -> t.subject(ORGANIZER_SUB)
                                        .claim("custom:username", ORGANIZER)
                                        .claim("custom:role", "ORGANIZER"))
                                .authorities(realConversion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(createdByOf(DISPLAY_PERSON)).isEqualTo(ORGANIZER);
    }

    @Test
    @DisplayName("ownership reads the username: an attendee's agent edits its person's profile, not another's")
    void should_passOwnership_when_agentEditsOwnProfile() throws Exception {
        String body = """
                {"bio": "Updated by an agent"}
                """;

        mockMvc.perform(patch("/api/v1/users/" + ATTENDEE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ATTENDEE_SUB, ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT bio FROM user_profiles WHERE username = ?", String.class, ATTENDEE))
                .isEqualTo("Updated by an agent");

        mockMvc.perform(patch("/api/v1/users/" + ORGANIZER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ATTENDEE_SUB, ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an organizer's agent passes ownership of its person's own profile")
    void should_passOwnership_when_organizerAgentEditsOwnProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + ORGANIZER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER_SUB, ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio": "Organizer bio by an agent"}
                                """))
                .andExpect(status().isOk());
    }
}
