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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 9 (R8): every operation of the read API, through the production chain. An organizer reads
 * (positive control); a speaker or partner gets 403; an agent token minted for an organizer gets 401
 * (agent-service accepts Cognito tokens only); an organizer token carrying {@code act} gets 403
 * (method security refuses the agent whatever its issuer); anonymous gets 401. Every refusal
 * carries the shared ErrorResponse.
 */
@DisplayName("Only an organizer, as a person, reads the decision log")
class RunLogAccessIntegrationTest extends AbstractIntegrationTest {

    private static final Timestamp AT = Timestamp.from(Instant.parse("2026-11-20T08:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID runId;
    private UUID modelCallId;

    @BeforeEach
    void seedRun() {
        jdbc.execute("TRUNCATE agent_runs CASCADE");
        runId = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, end_reason) "
                + "VALUES (?, 'DONE', ?, ?, 'ORGANIZER', 'COMPLETED')", runId, AT, AT);
        UUID step = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at) VALUES (?, ?, 1, ?)",
                step, runId, AT);
        modelCallId = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_model_calls (id, run_id, step_id, status, prompt, started_at) "
                + "VALUES (?, ?, ?, 'STARTED', '{\"system\":\"s\"}'::jsonb, ?)", modelCallId, runId, step, AT);
    }

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    /** Every GET of agent-api.openapi.yml, on a run (and model call) that exists. */
    private List<String> paths() {
        String run = "/api/v1/agent/runs/" + runId;
        return List.of("/api/v1/agent/runs", run, run + "/steps", run + "/model-calls",
                run + "/model-calls/" + modelCallId, run + "/tool-calls", run + "/verdicts");
    }

    @Test
    @DisplayName("an organizer reads every operation (positive control for the refusals below)")
    void should_return200_when_organizer() throws Exception {
        for (String path : paths()) {
            mockMvc.perform(get(path).with(cognito("ORGANIZER", Map.of()))).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a speaker or a partner gets 403 on every operation")
    void should_return403_when_otherRole() throws Exception {
        for (String role : List.of("SPEAKER", "PARTNER")) {
            for (String path : paths()) {
                mockMvc.perform(get(path).with(cognito(role, Map.of()))).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.errorCode").value("ERR_FORBIDDEN"))
                        .andExpect(jsonPath("$.path").value(path));
            }
        }
    }

    @Test
    @DisplayName("an agent token minted for an organizer gets 401 on every operation")
    void should_return401_when_agentTokenForOrganizer() throws Exception {
        String agentToken = TestTokens.agentToken("organizer-sub", "organizer.one", UUID.randomUUID().toString(),
                "organizer.one");
        // Positive control: the token is valid for a decoder that accepts the agent kind with this
        // context's key, so the 401 can only come from agent-service refusing the kind
        Jwt accepted = BatbernJwtDecoder.fromEnvironment(environment, EnumSet.of(TokenKind.AGENT)).decode(agentToken);
        assertThat(accepted.getClaimAsMap("act")).containsKey("run");

        for (String path : paths()) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
        }
    }

    @Test
    @DisplayName("an organizer token carrying act gets 403 on every operation, whoever issued it")
    void should_return403_when_organizerTokenCarriesAct() throws Exception {
        Map<String, Object> act = Map.of("act", Map.of("sub", "agent-service", "run", UUID.randomUUID().toString()));
        for (String path : paths()) {
            mockMvc.perform(get(path).with(cognito("ORGANIZER", act))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.errorCode").value("ERR_FORBIDDEN"));
        }
    }

    @Test
    @DisplayName("anonymous gets 401 on every operation")
    void should_return401_when_anonymous() throws Exception {
        for (String path : paths()) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHORIZED"));
        }
    }

    private JwtRequestPostProcessor cognito(String role, Map<String, Object> extraClaims) {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt()
                .jwt(token -> {
                    token.subject("run-log-" + role.toLowerCase())
                            .claim("custom:role", role)
                            .claim("custom:username", "run.log." + role.toLowerCase());
                    extraClaims.forEach(token::claim);
                })
                .authorities(realConversion);
    }
}
