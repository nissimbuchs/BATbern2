package ch.batbern.agent.precedent;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.gateway.PublicApi;
import ch.batbern.agent.identity.AgentCredentialsRefusedException;
import ch.batbern.agent.identity.CumsAgentClient;
import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.identity.RunCredentials;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.contract.OpenApiContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Story 6 (R6, AD-8): the precedents of a proposed call come from the decision log (approvals,
 * denials, undos) and from past events read through {@code findPastEvents} with the run's token,
 * matched by {@link PrecedentMatchRule}, each naming its source. The decision log is seeded on
 * Testcontainers PostgreSQL; CUMS and the gateway are answered at the wire ({@link CumsAtTheWire},
 * every request validated against the CUMS internal and events-core specs).
 */
@DisplayName("Precedents come from the decision log and past events, each with its source")
class PrecedentRetrievalIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENTS_CORE = "docs/api/events-core-api.openapi.yml";
    private static final String TOOL = "confirmSpeakerSlot";
    private static final String OTHER_TOOL = "createCompany";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private PrecedentRetrieval retrieval;

    @Autowired
    private CumsAgentClient cumsClient;

    @Autowired
    private AgentProperties properties;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private CumsAtTheWire cums;
    private final Map<UUID, Integer> stepNumbers = new HashMap<>();
    private final List<String> gatewayAuthorizations = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE agent_runs CASCADE");
        stepNumbers.clear();
        gatewayAuthorizations.clear();
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate, EVENTS_CORE);
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("two approvals, one denial, one undo and two past events come back, decoys do not, each naming its source")
    void should_returnExactlyTheMatchingPrecedents_when_logAndHistorySeeded() throws IOException {
        cums.grants().mints();
        List<ContractServer.Call> calls = Collections.synchronizedList(new ArrayList<>());
        cums.server().expect(HttpMethod.GET, "/api/v1/events").andExpect(request -> gatewayAuthorizations
                .add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .respondPerRequest(HttpStatus.OK, call -> {
                    calls.add(call);
                    return pastEventsPage("BATbern58", "BATbern57");
                });
        Seeded seeded = seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);

        Precedents precedents = retrieval.retrieve(call(proposer, JSON.createObjectNode().put("topic", "Cloud Native")),
                proposer.api());

        // Exactly the matching rows, newest decision first, each with its source row
        assertThat(precedents.approvals()).extracting(DecisionPrecedent::sourceId)
                .containsExactly("agent_approvals:" + seeded.approvalNewer(), "agent_approvals:" + seeded.approvalOlder());
        assertThat(precedents.denials()).extracting(DecisionPrecedent::sourceId)
                .containsExactly("agent_approvals:" + seeded.denial());
        assertThat(precedents.undos()).extracting(DecisionPrecedent::sourceId)
                .containsExactly("agent_undos:" + seeded.undo());
        assertThat(precedents.undos().getFirst().undoOutcome()).isEqualTo("SUCCEEDED");
        assertThat(precedents.approvals()).allSatisfy(precedent -> {
            assertThat(precedent.kind()).isEqualTo(PrecedentKind.APPROVAL);
            assertThat(precedent.toolName()).isEqualTo(TOOL);
            assertThat(precedent.principalKind()).isEqualTo(PrincipalKind.ORGANIZER);
        });
        assertThat(precedents.approvals().getFirst().toolCallId()).isEqualTo(seeded.approvedCallNewer());
        assertThat(precedents.approvals().getFirst().at()).isEqualTo(T0.plus(Duration.ofDays(2)));
        // Past events, each with its event as source, in the API's order
        assertThat(precedents.historyStatus()).isEqualTo(Precedents.HistoryStatus.READ);
        assertThat(precedents.pastEvents()).extracting(PastEventPrecedent::sourceId)
                .containsExactly("event:BATbern58", "event:BATbern57");
        assertThat(precedents.pastEvents().getFirst().title()).isEqualTo("BATbern58 title");
        assertThat(precedents.sourceIds()).containsExactly(
                "agent_approvals:" + seeded.approvalNewer(), "agent_approvals:" + seeded.approvalOlder(),
                "agent_approvals:" + seeded.denial(), "agent_undos:" + seeded.undo(),
                "event:BATbern58", "event:BATbern57");
        // Positive control: the decoys are in the log (another kind, another tool, pending, expired, its undo)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_approvals", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_undos", Integer.class)).isEqualTo(2);
        // The history read: findPastEvents' query with the tool's topic, with the run's first token
        cums.verify();
        ContractServer.Call call = calls.getFirst();
        JsonNode filter = JSON.readTree(call.queryParam("filter"));
        assertThat(filter.path("title").path("$contains").asText()).isEqualTo("Cloud Native");
        assertThat(filter.path("date").has("$lt")).isTrue();
        assertThat(call.queryParam("limit")).isEqualTo(String.valueOf(PrecedentFixtureTool.PAST_EVENT_LIMIT));
        assertThat(call.queryParam("includeArchived")).isEqualTo("true");
        assertThat(gatewayAuthorizations).containsExactly("Bearer " + cums.tokens().getFirst());
        assertThat(cums.mintRequests()).hasSize(1);
    }

    @Test
    @DisplayName("a requester's call matches only the requester's decided calls, an undo whatever its outcome")
    void should_matchByPrincipalKind_when_requesterProposes() {
        cums.mints();
        Seeded seeded = seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.HandedOver(UUID.randomUUID(), CumsAtTheWire.grantSecret()),
                PrincipalKind.REQUESTER);

        Precedents precedents = retrieval.retrieve(call(proposer, JSON.createObjectNode()), proposer.api());

        assertThat(precedents.approvals()).extracting(DecisionPrecedent::sourceId)
                .containsExactly("agent_approvals:" + seeded.requesterApproval());
        assertThat(precedents.denials()).isEmpty();
        assertThat(precedents.undos()).extracting(DecisionPrecedent::sourceId, DecisionPrecedent::undoOutcome)
                .containsExactly(tuple("agent_undos:" + seeded.requesterUndo(), "FAILED"));
    }

    @Test
    @DisplayName("a tool that asks for no past events: decision precedents only, no read")
    void should_notReadHistory_when_toolAsksForNoPastEvents() {
        cums.grants().mints();
        // No gateway expectation: any GET fails the test
        Seeded seeded = seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);

        Precedents precedents = retrieval.retrieve(call(proposer, JSON.createObjectNode()), proposer.api());

        assertThat(precedents.historyStatus()).isEqualTo(Precedents.HistoryStatus.NOT_ASKED);
        assertThat(precedents.pastEvents()).isEmpty();
        // Positive control: the decision log was still read
        assertThat(precedents.approvals()).hasSize(2);
        assertThat(precedents.denials()).extracting(DecisionPrecedent::sourceId)
                .containsExactly("agent_approvals:" + seeded.denial());
        cums.verify();
    }

    @Test
    @DisplayName("the API answers 500: decision precedents back, history UNAVAILABLE and empty")
    void should_markHistoryUnavailable_when_apiAnswersError() {
        cums.grants().mints();
        cums.server().expect(HttpMethod.GET, "/api/v1/events")
                .respondPerRequest(call -> new ContractServer.Reply(HttpStatus.INTERNAL_SERVER_ERROR, null));
        seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);

        Precedents precedents = retrieval.retrieve(call(proposer, JSON.createObjectNode().put("topic", "Cloud")),
                proposer.api());

        cums.verify();
        assertThat(precedents.historyStatus()).isEqualTo(Precedents.HistoryStatus.UNAVAILABLE);
        assertThat(precedents.pastEvents()).isEmpty();
        assertThat(precedents.approvals()).hasSize(2);
        assertThat(precedents.denials()).hasSize(1);
    }

    @Test
    @DisplayName("the gateway cannot be reached: decision precedents back, history UNAVAILABLE and empty")
    void should_markHistoryUnavailable_when_gatewayUnreachable() {
        cums.grants().mints();
        cums.server().expect(HttpMethod.GET, "/api/v1/events").respondWith(request -> {
            throw new IOException("Connection refused");
        });
        seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);

        Precedents precedents = retrieval.retrieve(call(proposer, JSON.createObjectNode().put("topic", "Cloud")),
                proposer.api());

        cums.verify();
        assertThat(precedents.historyStatus()).isEqualTo(Precedents.HistoryStatus.UNAVAILABLE);
        assertThat(precedents.pastEvents()).isEmpty();
        assertThat(precedents.approvals()).hasSize(2);
        assertThat(precedents.denials()).hasSize(1);
    }

    @Test
    @DisplayName("a past-event query the tool cannot build from the model's input asks for none")
    void should_notReadHistory_when_toolBuildsUnusableQuery() {
        cums.grants().mints();
        // No gateway expectation: any GET fails the test
        seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);

        Precedents blank = retrieval.retrieve(call(proposer, JSON.createObjectNode().put("topic", " ")),
                proposer.api());
        Precedents tooLong = retrieval.retrieve(call(proposer, JSON.createObjectNode().put("topic", "x".repeat(101))),
                proposer.api());

        assertThat(blank.historyStatus()).isEqualTo(Precedents.HistoryStatus.NOT_ASKED);
        assertThat(tooLong.historyStatus()).isEqualTo(Precedents.HistoryStatus.NOT_ASKED);
        // Positive control: the decision log was still read
        assertThat(blank.approvals()).hasSize(2);
        cums.verify();
    }

    @Test
    @DisplayName("CUMS refuses the mint of the history read: the refusal goes up, nothing is read")
    void should_propagateRefusal_when_mintForHistoryRefused() {
        // The first token lives 30 s, so the read mints again, and CUMS refuses
        cums.grants().mintsThenRefuses(1, HttpStatus.FORBIDDEN, "GRANT_INVALID", Duration.ofSeconds(30));
        seedDecisionLog();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);
        ProposedCall call = call(proposer, JSON.createObjectNode().put("topic", "Cloud"));

        assertThatThrownBy(() -> retrieval.retrieve(call, proposer.api()))
                .isInstanceOfSatisfying(AgentCredentialsRefusedException.class, e -> {
                    assertThat(e.reason()).isEqualTo(RunEndReason.TOKEN_REFUSED);
                    assertThat(e.code()).isEqualTo("GRANT_INVALID");
                });
        assertThat(cums.mintRequests()).hasSize(2);
        cums.verify();
    }

    @Test
    @DisplayName("a run without a grant has no principal kind: refused, nothing read")
    void should_refuse_when_runHasNoPrincipalKind() {
        cums.grants().mints();
        Proposer proposer = proposer(new GrantSource.OrganizerLogin("cognito-organizer-jwt"), PrincipalKind.ORGANIZER);
        UUID withoutGrant = run(null);

        assertThatThrownBy(() -> retrieval.retrieve(new ProposedCall(withoutGrant, new PrecedentFixtureTool(TOOL),
                JSON.createObjectNode().put("topic", "Cloud")), proposer.api()))
                .isInstanceOf(IllegalStateException.class);
        cums.verify();
    }

    // ── Seeding ──────────────────────────────────────────────────────────────────────────────────

    /** The ids the matching tests expect back. */
    private record Seeded(UUID approvalOlder, UUID approvalNewer, UUID approvedCallNewer, UUID denial, UUID undo,
                          UUID requesterApproval, UUID requesterUndo) {
    }

    /**
     * Matching for an organizer's {@link #TOOL}: two approvals (two runs), one denial, one undo.
     * Decoys for an organizer: the same tool approved and undone (FAILED) for a requester, another
     * tool approved, a pending and an expired approval of the tool.
     */
    private Seeded seedDecisionLog() {
        UUID organizerRun = run(PrincipalKind.ORGANIZER);
        UUID otherOrganizerRun = run(PrincipalKind.ORGANIZER);
        UUID requesterRun = run(PrincipalKind.REQUESTER);

        UUID approvedCallOlder = toolCall(organizerRun, TOOL);
        UUID approvalOlder = approval(organizerRun, approvedCallOlder, "APPROVED", T0.plus(Duration.ofDays(1)));
        UUID approvedCallNewer = toolCall(otherOrganizerRun, TOOL);
        UUID approvalNewer = approval(otherOrganizerRun, approvedCallNewer, "APPROVED", T0.plus(Duration.ofDays(2)));
        UUID denial = approval(organizerRun, toolCall(organizerRun, TOOL), "DENIED", T0.plus(Duration.ofDays(3)));
        UUID undo = undo(organizerRun, approvedCallOlder, "SUCCEEDED", T0.plus(Duration.ofDays(4)));

        UUID requesterCall = toolCall(requesterRun, TOOL);
        UUID requesterApproval = approval(requesterRun, requesterCall, "APPROVED", T0.plus(Duration.ofDays(5)));
        UUID requesterUndo = undo(requesterRun, requesterCall, "FAILED", T0.plus(Duration.ofDays(6)));
        approval(organizerRun, toolCall(organizerRun, OTHER_TOOL), "APPROVED", T0.plus(Duration.ofDays(7)));
        approval(organizerRun, toolCall(organizerRun, TOOL), "PENDING", null);
        approval(organizerRun, toolCall(organizerRun, TOOL), "EXPIRED", T0.plus(Duration.ofDays(8)));
        return new Seeded(approvalOlder, approvalNewer, approvedCallNewer, denial, undo, requesterApproval,
                requesterUndo);
    }

    private UUID run(PrincipalKind kind) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, end_reason, grant_run_id, "
                        + "principal_kind) VALUES (?, 'DONE', ?, ?, ?, 'COMPLETED', ?, ?)", id, ts(T0), ts(T0),
                kind == PrincipalKind.REQUESTER ? "INBOUND_MAIL" : "ORGANIZER",
                kind == null ? null : UUID.randomUUID(), kind == null ? null : kind.name());
        return id;
    }

    private UUID toolCall(UUID runId, String toolName) {
        int number = stepNumbers.merge(runId, 1, Integer::sum);
        UUID step = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at) VALUES (?, ?, ?, ?)",
                step, runId, number, ts(T0));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_tool_calls (id, run_id, step_id, tool_use_id, tool_name, input, status, "
                + "started_at) VALUES (?, ?, ?, ?, ?, '{}'::jsonb, 'STARTED', ?)", id, runId, step,
                "toolu_" + number, toolName, ts(T0));
        return id;
    }

    private UUID approval(UUID runId, UUID toolCallId, String state, Instant decidedAt) {
        UUID verdict = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_verdicts (id, run_id, tool_call_id, operation_class, outcome, inputs, "
                + "cited_precedents, decided_at) VALUES (?, ?, ?, 'BINDING', 'PENDING_APPROVAL', '{}'::jsonb, "
                + "'[]'::jsonb, ?)", verdict, runId, toolCallId, ts(T0));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_approvals (id, run_id, verdict_id, state, decided_by, created_at, decided_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, runId, verdict, state,
                decidedAt == null ? null : "organizer.test", ts(T0), decidedAt == null ? null : ts(decidedAt));
        return id;
    }

    private UUID undo(UUID runId, UUID toolCallId, String outcome, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_undos (id, run_id, tool_call_id, undo_operation, outcome, performed_by, "
                + "performed_at) VALUES (?, ?, ?, 'DELETE /companies/{companyName}', ?, 'organizer.test', ?)",
                id, runId, toolCallId, outcome, ts(at));
        return id;
    }

    // ── The proposing run ───────────────────────────────────────────────────────────────────────

    /** The proposing run: its row (with the grant's principal kind) and its public API. */
    private record Proposer(UUID runId, PublicApi api) {
    }

    private Proposer proposer(GrantSource source, PrincipalKind expected) {
        RunCredentials credentials = RunCredentials.obtain(cumsClient, source, clock);
        assertThat(credentials.principalKind()).isEqualTo(expected);
        credentials.start();
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, grant_run_id, principal_kind) "
                        + "VALUES (?, 'RUNNING', ?, ?, ?, ?)", runId, ts(clock.instant()), source.trigger().name(),
                credentials.grantRunId(), credentials.principalKind().name());
        return new Proposer(runId, new PublicApi(restTemplate, properties.gateway().baseUrl(), credentials));
    }

    private static ProposedCall call(Proposer proposer, JsonNode input) {
        return new ProposedCall(proposer.runId(), new PrecedentFixtureTool(TOOL), input);
    }

    /** One page of past events: the spec's example event per code, newest first. */
    private static String pastEventsPage(String... codes) {
        try {
            JsonNode example = JSON.readTree(OpenApiContract.load(EVENTS_CORE).example("GET", "/api/v1/events", 200,
                    null)).path("data").get(0);
            ObjectNode page = JSON.createObjectNode();
            ArrayNode data = page.putArray("data");
            for (String code : codes) {
                ObjectNode event = example.deepCopy();
                event.put("eventCode", code).put("eventNumber", Integer.parseInt(code.substring("BATbern".length())))
                        .put("title", code + " title");
                data.add(event);
            }
            page.putObject("pagination").put("page", 1).put("limit", PrecedentFixtureTool.PAST_EVENT_LIMIT)
                    .put("totalItems", codes.length).put("totalPages", 1).put("hasNext", false).put("hasPrev", false);
            return page.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
