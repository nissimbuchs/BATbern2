package ch.batbern.agent.identity;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunEndReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Story 6: the principal kind of a grant comes from CUMS's answer. An answer outside the contract
 * (a kind the spec does not list, or none) is refused as INVALID_CUMS_RESPONSE. CUMS is answered at
 * the wire; {@code ContractServer} is not used here because it refuses to send an answer that breaks
 * the spec, which is exactly what this test sends. The in-contract answers are proven in
 * {@code AgentCredentialsIntegrationTest} against the spec.
 */
@DisplayName("A grant answer carries the principal kind")
class CumsAgentClientGrantAnswerTest {

    private static final String BASE = "http://cums.test";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final CumsAgentClient client = new CumsAgentClient(restTemplate, new AgentProperties(true,
            new AgentProperties.Loop(25, 4096),
            new AgentProperties.Anthropic("none", "claude-sonnet-5-5", "medium", Duration.ofMinutes(5), 2, ""),
            new AgentProperties.Cums(BASE, "test-only-secret"), new AgentProperties.Gateway("http://gw.test")));

    @Test
    @DisplayName("a listed kind is taken over")
    void should_takePrincipalKind_when_answerListsIt() {
        answer("ORGANIZER_DELEGATE");

        RunGrant grant = client.grant(new GrantSource.SchedulerJob("morning-brief"));

        assertThat(grant.principalKind()).isEqualTo(PrincipalKind.ORGANIZER_DELEGATE);
        server.verify();
    }

    @Test
    @DisplayName("a kind the contract does not list is refused")
    void should_refuseGrant_when_principalKindUnknown() {
        answer("SPEAKER");

        assertThatThrownBy(() -> client.grant(new GrantSource.SchedulerJob("morning-brief")))
                .isInstanceOfSatisfying(AgentCredentialsRefusedException.class, e -> {
                    assertThat(e.reason()).isEqualTo(RunEndReason.GRANT_REFUSED);
                    assertThat(e.code()).isEqualTo(CumsAgentClient.INVALID_CUMS_RESPONSE);
                });
    }

    @Test
    @DisplayName("an answer without a kind is refused")
    void should_refuseGrant_when_principalKindMissing() {
        answer(null);

        assertThatThrownBy(() -> client.grant(new GrantSource.SchedulerJob("morning-brief")))
                .isInstanceOfSatisfying(AgentCredentialsRefusedException.class,
                        e -> assertThat(e.code()).isEqualTo(CumsAgentClient.INVALID_CUMS_RESPONSE));
    }

    private void answer(String principalKind) {
        ObjectNode body = JSON.createObjectNode()
                .put("runId", UUID.randomUUID().toString())
                .put("grantSecret", CumsAtTheWire.grantSecret());
        if (principalKind != null) {
            body.put("principalKind", principalKind);
        }
        server.expect(requestTo(BASE + CumsAgentClient.GRANTS)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(body.toString()));
    }
}
