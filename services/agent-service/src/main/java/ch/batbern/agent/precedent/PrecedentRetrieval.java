package ch.batbern.agent.precedent;

import ch.batbern.agent.gateway.PublicApi;
import ch.batbern.agent.identity.AgentCredentialsRefusedException;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.tools.PastEventQuery;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolOutcome;
import ch.batbern.agent.tools.read.FindPastEventsTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Retrieves the precedents of a proposed call (story 6, R6, AD-8) from the two sources AD-8 allows:
 * the decision log (organizer approvals, denials and undos) and the history of past events, read
 * through the {@code findPastEvents} read tool on the public API with the run's token. No vector
 * store, no embeddings, no model call. Which precedents match is {@link PrecedentMatchRule}'s
 * alone. The gate (story 7) weighs what comes back and cites {@link Precedents#sourceIds()}.
 *
 * <p>The application log carries the run id, the tool name and counts only (#1202).
 */
@Service
public class PrecedentRetrieval {

    private static final Logger LOG = LoggerFactory.getLogger(PrecedentRetrieval.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DecisionLogPrecedents decisionLog;
    private final FindPastEventsTool pastEvents;

    public PrecedentRetrieval(DecisionLogPrecedents decisionLog, Clock clock) {
        this.decisionLog = decisionLog;
        this.pastEvents = new FindPastEventsTool(clock);
    }

    /**
     * The precedents of {@code call}.
     *
     * @param call the proposed call; the principal kind is read from its run
     * @param api  the run's public API (its token); limited here to {@code findPastEvents}' operations
     * @throws java.util.NoSuchElementException when the call's run does not exist
     * @throws IllegalStateException            when the call's run has no grant
     * @throws AgentCredentialsRefusedException when CUMS refuses the mint of the history read; an
     *                                          unreachable gateway or an HTTP error of the API gives
     *                                          history UNAVAILABLE instead
     */
    public Precedents retrieve(ProposedCall call, PublicApi api) {
        DecisionLogPrecedents.Candidates candidates =
                decisionLog.candidates(call.runId(), PrecedentMatchRule.candidateToolName(call));
        PrincipalKind kind = candidates.principalKind();
        List<DecisionPrecedent> approvals = new ArrayList<>();
        List<DecisionPrecedent> denials = new ArrayList<>();
        for (DecisionPrecedent candidate : candidates.decidedApprovals()) {
            if (PrecedentMatchRule.matches(call, kind, candidate)) {
                (candidate.kind() == PrecedentKind.APPROVAL ? approvals : denials).add(candidate);
            }
        }
        List<DecisionPrecedent> undos = candidates.undos().stream()
                .filter(candidate -> PrecedentMatchRule.matches(call, kind, candidate))
                .toList();

        Optional<PastEventQuery> query = pastEventQuery(call);
        Precedents precedents = query.isEmpty()
                ? new Precedents(approvals, denials, undos, List.of(), Precedents.HistoryStatus.NOT_ASKED)
                : withHistory(call, api, query.get(), approvals, denials, undos);
        LOG.info("Precedents for run {} tool {}: approvals={} denials={} undos={} past_events={} history={}",
                call.runId(), call.tool().name(), approvals.size(), denials.size(), undos.size(),
                precedents.pastEvents().size(), precedents.historyStatus());
        return precedents;
    }

    private Precedents withHistory(ProposedCall call, PublicApi api, PastEventQuery query,
                                   List<DecisionPrecedent> approvals, List<DecisionPrecedent> denials,
                                   List<DecisionPrecedent> undos) {
        ToolOutcome outcome;
        try {
            outcome = pastEvents.execute(query.toInput(),
                    new ToolContext(call.runId(), api.limitedTo(pastEvents.operations())));
        } catch (RestClientException e) {
            // The gateway could not be reached: as an HTTP error, the history says nothing. A
            // refused mint is not a RestClientException and goes up
            LOG.warn("Precedents for run {}: past events unreadable ({})", call.runId(), e.getClass().getName());
            outcome = ToolOutcome.error("unreachable");
        }
        Optional<List<PastEventPrecedent>> events = outcome.error() ? Optional.empty() : parse(outcome.content());
        return events
                .map(found -> new Precedents(approvals, denials, undos, found, Precedents.HistoryStatus.READ))
                .orElseGet(() -> new Precedents(approvals, denials, undos, List.of(),
                        Precedents.HistoryStatus.UNAVAILABLE));
    }

    /**
     * The tool's past-event query; input the tool cannot use (a query out of {@link PastEventQuery}'s
     * bounds, built from model input) asks for none, as {@code AgentTool.pastEventQuery} promises.
     */
    private static Optional<PastEventQuery> pastEventQuery(ProposedCall call) {
        try {
            return PrecedentMatchRule.pastEventQuery(call);
        } catch (IllegalArgumentException e) {
            LOG.warn("Precedents for run {} tool {}: unusable past-event query, none asked", call.runId(),
                    call.tool().name());
            return Optional.empty();
        }
    }

    /** The events of {@code findPastEvents}' answer; empty when it is not the tool's shape. */
    private static Optional<List<PastEventPrecedent>> parse(String content) {
        try {
            JsonNode events = JSON.readTree(content).path("events");
            if (!events.isArray()) {
                return Optional.empty();
            }
            List<PastEventPrecedent> found = new ArrayList<>();
            for (JsonNode event : events) {
                JsonNode code = event.path("eventCode");
                if (code.isTextual() && !code.asText().isBlank()) {
                    found.add(PastEventPrecedent.of(code.asText(), text(event, "title"), text(event, "date")));
                }
            }
            return Optional.of(found);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
