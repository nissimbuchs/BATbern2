package ch.batbern.agent.run;

import ch.batbern.shared.security.BatbernJwtDecoder;
import ch.batbern.shared.security.TokenKind;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/agent/runs} through the production chain (R1, R8): an organizer gets a page,
 * anonymous 401, another role 403, an agent token 401. Roles come from {@code custom:role} through
 * the service's real {@link JwtAuthenticationConverter}.
 */
@DisplayName("GET /api/v1/agent/runs")
class AgentRunControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String RUNS = "/api/v1/agent/runs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private AgentRunRepository runs;

    @Autowired
    private Environment environment;

    @BeforeEach
    @AfterEach
    void emptyTable() {
        runs.deleteAll();
    }

    @Test
    @DisplayName("organizer gets an empty first page while no run exists")
    void should_returnEmptyPage_when_organizerListsAndNoRunExists() throws Exception {
        mockMvc.perform(get(RUNS).with(tokenWithRole("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.totalItems").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(0))
                .andExpect(jsonPath("$.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.pagination.hasPrev").value(false));
    }

    @Test
    @DisplayName("organizer reads the stored runs, newest first, one page at a time")
    void should_listNewestFirst_when_runsExist() throws Exception {
        Instant base = Instant.parse("2026-11-20T08:00:00Z");
        AgentRunEntity oldest = AgentRunEntity.started(UUID.randomUUID(), base);
        oldest.end(AgentRunStatus.DONE, base.plus(2, ChronoUnit.MINUTES));
        AgentRunEntity middle = AgentRunEntity.started(UUID.randomUUID(), base.plus(1, ChronoUnit.HOURS));
        middle.end(AgentRunStatus.HANDED_TO_ORGANIZERS, base.plus(61, ChronoUnit.MINUTES));
        AgentRunEntity newest = AgentRunEntity.started(UUID.randomUUID(), base.plus(2, ChronoUnit.HOURS));
        runs.saveAll(List.of(oldest, middle, newest));

        mockMvc.perform(get(RUNS).param("page", "0").param("limit", "2").with(tokenWithRole("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(newest.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].endedAt").doesNotExist())
                .andExpect(jsonPath("$.data[1].id").value(middle.getId().toString()))
                .andExpect(jsonPath("$.data[1].status").value("HANDED_TO_ORGANIZERS"))
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(true));

        mockMvc.perform(get(RUNS).param("page", "1").param("limit", "2").with(tokenWithRole("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(oldest.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].endedAt").value("2026-11-20T08:02:00Z"))
                .andExpect(jsonPath("$.pagination.hasPrev").value(true));
    }

    @Test
    @DisplayName("a limit above 100 answers 400 with the shared error envelope")
    void should_return400_when_limitTooLarge() throws Exception {
        mockMvc.perform(get(RUNS).param("limit", "101").with(tokenWithRole("ORGANIZER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"));
    }

    @Test
    @DisplayName("anonymous answers 401")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(get(RUNS)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("speaker answers 403")
    void should_return403_when_speaker() throws Exception {
        mockMvc.perform(get(RUNS).with(tokenWithRole("SPEAKER"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an agent token answers 401: agent-service accepts Cognito tokens only")
    void should_return401_when_agentToken() throws Exception {
        String agentToken = TestTokens.agentToken("organizer-sub", "organizer.one",
                UUID.randomUUID().toString(), "organizer.one");

        // Positive control: the token is valid, a decoder with the agent kind and this context's key
        // accepts it, so the 401 below can only come from agent-service refusing the kind.
        Jwt accepted = BatbernJwtDecoder.fromEnvironment(environment, EnumSet.of(TokenKind.AGENT))
                .decode(agentToken);
        assertThat(accepted.getClaimAsMap("act")).containsKey("run");

        mockMvc.perform(get(RUNS).header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isUnauthorized());
    }

    private JwtRequestPostProcessor tokenWithRole(String role) {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt()
                .jwt(token -> token.subject("agent-runs-" + role.toLowerCase())
                        .claim("custom:role", role)
                        .claim("custom:username", "agent.runs." + role.toLowerCase()))
                .authorities(realConversion);
    }
}
