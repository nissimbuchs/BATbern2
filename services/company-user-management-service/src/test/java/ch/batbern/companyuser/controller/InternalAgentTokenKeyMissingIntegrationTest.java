package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.UpdateAgentSettingsRequest;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.AgentSettingsService;
import ch.batbern.shared.security.Actors;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent identity, story 6: with {@code agent.jwt.private-key} at the deploy placeholder {@code none},
 * CUMS starts normally and answers a valid, allowed mint request with 503
 * {@code AGENT_SIGNING_KEY_MISSING}; every earlier check still answers first. Own context: the key is
 * overridden here, after {@link AbstractIntegrationTest} registered the test key.
 */
@Import(TestAwsConfig.class)
@ContextConfiguration(initializers = InternalAgentTokenKeyMissingIntegrationTest.NoSigningKey.class)
@DisplayName("CUMS: agent tokens without a signing key")
class InternalAgentTokenKeyMissingIntegrationTest extends AbstractIntegrationTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String HEADER = "X-Agent-Caller-Secret";
    private static final String EMS_SECRET = "test-only-ems-caller-secret";
    private static final String AGENT_SERVICE_SECRET = "test-only-agent-service-caller-secret";
    private static final String ORGANIZER = "anna.agenttokenkey";
    private static final String ORGANIZER_SUB = "a0000000-0000-4000-8000-0000000006b1";

    /**
     * Puts the placeholder in front of every other property source, the test key that
     * {@link AbstractIntegrationTest} registers included ({@code @TestPropertySource} and a second
     * {@code @DynamicPropertySource} lose to it).
     */
    static class NoSigningKey implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("agent-token-key-missing", Map.of("agent.jwt.private-key", "none")));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AgentSettingsService agentSettingsService;
    @Autowired
    private Environment environment;

    @BeforeEach
    void seed() {
        cleanUp();
        userRepository.saveAndFlush(User.builder()
                .username(ORGANIZER)
                .email(ORGANIZER + "@example.com")
                .firstName("Anna")
                .lastName("Brünnimann")
                .cognitoUserId(ORGANIZER_SUB)
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build());
        agentSettingsService.update(new UpdateAgentSettingsRequest()
                .enabled(true)
                .allowlist(List.of(ORGANIZER)), Actors.system("agent-token-key-test"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM agent_grants");
        jdbc.update("DELETE FROM agent_allowlist");
        jdbc.update("INSERT INTO agent_settings (id, enabled) VALUES (1, false) "
                + "ON CONFLICT (id) DO UPDATE SET enabled = false, delegate_username = NULL, "
                + "updated_at = NULL, updated_by = NULL");
        userRepository.findByUsername(ORGANIZER).ifPresent(userRepository::delete);
        userRepository.flush();
    }

    private String perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String mintBody() throws Exception {
        String grant = perform(post("/internal/agent-grants").header(HEADER, EMS_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trigger\": \"EMS_HANDOFF\", \"username\": \"" + ORGANIZER + "\"}"), 201);
        return "{\"runId\": \"" + JsonPath.read(grant, "$.runId") + "\", \"grantSecret\": \""
                + JsonPath.read(grant, "$.grantSecret") + "\"}";
    }

    @Test
    @DisplayName("the override took effect: the context holds the placeholder, not the test key")
    void should_holdPlaceholder_when_overridden() {
        assertThat(environment.getProperty("agent.jwt.private-key")).isEqualTo("none");
    }

    @Test
    @DisplayName("a valid, allowed request: 503 AGENT_SIGNING_KEY_MISSING, no token")
    void should_answer503_when_signingKeyMissing() throws Exception {
        String body = perform(post("/internal/agent-tokens").header(HEADER, AGENT_SERVICE_SECRET)
                .contentType(MediaType.APPLICATION_JSON).content(mintBody()), 503);

        assertThat(JsonPath.<String>read(body, "$.details.code")).isEqualTo("AGENT_SIGNING_KEY_MISSING");
        assertThat(body).doesNotContain("\"token\"");
    }

    @Test
    @DisplayName("a bad caller secret still answers 401 first")
    void should_answer401_when_callerSecretBad() throws Exception {
        String body = perform(post("/internal/agent-tokens").header(HEADER, "not-the-secret")
                .contentType(MediaType.APPLICATION_JSON).content(mintBody()), 401);

        assertThat(JsonPath.<String>read(body, "$.details.code")).isEqualTo("CALLER_SECRET_INVALID");
    }

    @Test
    @DisplayName("an invalid grant still answers 403 GRANT_INVALID before the key check")
    void should_answer403_when_grantInvalid() throws Exception {
        String body = perform(post("/internal/agent-tokens").header(HEADER, AGENT_SERVICE_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\": \"unknown-run\", \"grantSecret\": \"whatever\"}"), 403);

        assertThat(JsonPath.<String>read(body, "$.details.code")).isEqualTo("GRANT_INVALID");
    }
}
