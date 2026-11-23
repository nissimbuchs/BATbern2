package ch.batbern.agent.precedent;

import ch.batbern.agent.run.PrincipalKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 6: the one matching rule, same tool and same principal kind (epic Assumption). */
@DisplayName("A precedent matches on the same tool and the same principal kind")
class PrecedentMatchRuleTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ProposedCall CALL = new ProposedCall(UUID.randomUUID(),
            new PrecedentFixtureTool("confirmSpeakerSlot"), JSON.createObjectNode().put("topic", "Cloud"));

    @Test
    @DisplayName("same tool, same kind: a precedent")
    void should_match_when_sameToolAndKind() {
        assertThat(PrecedentMatchRule.matches(CALL, PrincipalKind.ORGANIZER,
                approval("confirmSpeakerSlot", PrincipalKind.ORGANIZER))).isTrue();
    }

    @Test
    @DisplayName("another tool, another kind, or a run without a kind: none")
    void should_notMatch_when_toolOrKindDiffers() {
        assertThat(PrecedentMatchRule.matches(CALL, PrincipalKind.ORGANIZER,
                approval("createCompany", PrincipalKind.ORGANIZER))).isFalse();
        assertThat(PrecedentMatchRule.matches(CALL, PrincipalKind.ORGANIZER,
                approval("confirmSpeakerSlot", PrincipalKind.REQUESTER))).isFalse();
        assertThat(PrecedentMatchRule.matches(CALL, PrincipalKind.ORGANIZER,
                approval("confirmSpeakerSlot", null))).isFalse();
    }

    @Test
    @DisplayName("the decision log is narrowed by the proposed tool; past events come from the tool")
    void should_narrowByTool_and_askToolForPastEvents() {
        assertThat(PrecedentMatchRule.candidateToolName(CALL)).isEqualTo("confirmSpeakerSlot");
        assertThat(PrecedentMatchRule.pastEventQuery(CALL)).hasValueSatisfying(query ->
                assertThat(query.toInput().toString()).isEqualTo("{\"searchText\":\"Cloud\",\"limit\":5}"));
        assertThat(PrecedentMatchRule.pastEventQuery(new ProposedCall(CALL.runId(), CALL.tool(),
                JSON.createObjectNode()))).isEmpty();
    }

    private static DecisionPrecedent approval(String tool, PrincipalKind kind) {
        UUID id = UUID.randomUUID();
        return new DecisionPrecedent(PrecedentKind.APPROVAL, DecisionPrecedent.APPROVALS + id, UUID.randomUUID(),
                UUID.randomUUID(), tool, kind, Instant.EPOCH, null);
    }
}
