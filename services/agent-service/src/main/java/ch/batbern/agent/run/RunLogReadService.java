package ch.batbern.agent.run;

import ch.batbern.agent.dto.generated.AgentApproval;
import ch.batbern.agent.dto.generated.AgentChosenTool;
import ch.batbern.agent.dto.generated.AgentCitedPrecedent;
import ch.batbern.agent.dto.generated.AgentInboundMessage;
import ch.batbern.agent.dto.generated.AgentModelCall;
import ch.batbern.agent.dto.generated.AgentModelCallDetail;
import ch.batbern.agent.dto.generated.AgentRunTotals;
import ch.batbern.agent.dto.generated.AgentStep;
import ch.batbern.agent.dto.generated.AgentTokenUsage;
import ch.batbern.agent.dto.generated.AgentToolCall;
import ch.batbern.agent.dto.generated.AgentVerdict;
import ch.batbern.agent.precedent.DecisionPrecedent;
import ch.batbern.agent.precedent.PastEventPrecedent;
import ch.batbern.shared.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the decision log of one run for the organizers' read API (story 9, R8, AD-9): steps, model
 * calls, tool calls and verdicts, each page ordered as the run happened (oldest first, id as
 * tie-breaker), and the totals and inbound messages of the run detail. Read only; the JSON columns
 * are handed out as stored. Each public method is one REPEATABLE READ transaction, so a page and its
 * total, or a run and its totals, come from one snapshot while the loop is still writing.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RunLogReadService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> OBJECTS = new TypeReference<>() { };

    private static final String MODEL_CALL_COLUMNS = """
            SELECT mc.id, s.step_number, mc.status, mc.model, mc.stop_reason, mc.chosen_tools,
                   mc.input_tokens, mc.output_tokens, mc.cache_creation_input_tokens,
                   mc.cache_read_input_tokens, mc.error, mc.started_at, mc.ended_at
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final AgentRunRepository runs;

    public RunLogReadService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, AgentRunRepository runs) {
        this.jdbc = jdbc;
        this.named = named;
        this.runs = runs;
    }

    /** A run with its totals and inbound messages, read from one snapshot. */
    public record RunDetail(AgentRunEntity run, AgentRunTotals totals, List<AgentInboundMessage> inboundMessages) {
    }

    /** The run detail, or 404. */
    public RunDetail detail(UUID runId) {
        return new RunDetail(run(runId), totals(runId), inboundMessages(runId));
    }

    /** One page of a run's sub-collection and its total. */
    public record Slice<T>(List<T> items, long total, int page, int limit) {
    }

    /** The run, or 404. */
    public AgentRunEntity run(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("Agent run", runId.toString()));
    }

    /** Counts over the run's log; tokens summed over its model calls. */
    public AgentRunTotals totals(UUID runId) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM agent_steps WHERE run_id = ?) AS steps,
                       (SELECT count(*) FROM agent_tool_calls WHERE run_id = ?) AS tool_calls,
                       (SELECT count(*) FROM agent_verdicts WHERE run_id = ?) AS verdicts,
                       (SELECT count(*) FROM agent_approvals WHERE run_id = ? AND state = 'PENDING') AS pending,
                       count(mc.id) AS model_calls,
                       COALESCE(sum(mc.input_tokens), 0) AS input_tokens,
                       COALESCE(sum(mc.output_tokens), 0) AS output_tokens,
                       COALESCE(sum(mc.cache_creation_input_tokens), 0) AS cache_creation,
                       COALESCE(sum(mc.cache_read_input_tokens), 0) AS cache_read
                  FROM agent_model_calls mc WHERE mc.run_id = ?
                """, (rs, n) -> new AgentRunTotals(rs.getLong("steps"), rs.getLong("model_calls"),
                        rs.getLong("tool_calls"), rs.getLong("verdicts"), rs.getLong("pending"),
                        rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("cache_creation"),
                        rs.getLong("cache_read")),
                runId, runId, runId, runId, runId);
    }

    /** The messages the run was given, in the order received. */
    public List<AgentInboundMessage> inboundMessages(UUID runId) {
        return jdbc.query("""
                SELECT id, channel, external_id, received_at, content
                  FROM agent_inbound_messages WHERE run_id = ? ORDER BY received_at, id
                """, (rs, n) -> {
                    AgentInboundMessage message = new AgentInboundMessage(rs.getObject("id", UUID.class),
                            AgentInboundMessage.ChannelEnum.fromValue(rs.getString("channel")),
                            time(rs, "received_at"), anyJson(rs.getString("content")));
                    message.setExternalId(rs.getString("external_id"));
                    return message;
                }, runId);
    }

    /** The run's steps by step number, each with its model and tool call ids. */
    public Slice<AgentStep> steps(UUID runId, int page, int limit) {
        run(runId);
        long total = count("agent_steps", runId);
        List<AgentStep> items = jdbc.query("""
                SELECT s.id, s.step_number, s.started_at, s.ended_at,
                       ARRAY(SELECT mc.id FROM agent_model_calls mc WHERE mc.step_id = s.id
                              ORDER BY mc.started_at, mc.id) AS model_call_ids,
                       ARRAY(SELECT tc.id FROM agent_tool_calls tc WHERE tc.step_id = s.id
                              ORDER BY tc.started_at, tc.id) AS tool_call_ids
                  FROM agent_steps s WHERE s.run_id = ?
                 ORDER BY s.step_number, s.id LIMIT ? OFFSET ?
                """, (rs, n) -> {
                    AgentStep step = new AgentStep(rs.getObject("id", UUID.class), rs.getInt("step_number"),
                            time(rs, "started_at"), uuids(rs.getArray("model_call_ids")),
                            uuids(rs.getArray("tool_call_ids")));
                    step.setEndedAt(time(rs, "ended_at"));
                    return step;
                }, runId, limit, offset(page, limit));
        return new Slice<>(items, total, page, limit);
    }

    /** The run's model calls, oldest first, without prompt and answer. */
    public Slice<AgentModelCall> modelCalls(UUID runId, int page, int limit) {
        run(runId);
        long total = count("agent_model_calls", runId);
        List<AgentModelCall> items = jdbc.query(MODEL_CALL_COLUMNS + """
                  FROM agent_model_calls mc JOIN agent_steps s ON s.id = mc.step_id
                 WHERE mc.run_id = ? ORDER BY mc.started_at, mc.id LIMIT ? OFFSET ?
                """, (rs, n) -> modelCall(rs), runId, limit, offset(page, limit));
        return new Slice<>(items, total, page, limit);
    }

    /** One model call of the run with its full prompt and answer, or 404. */
    public AgentModelCallDetail modelCall(UUID runId, UUID modelCallId) {
        run(runId);
        List<AgentModelCallDetail> found = jdbc.query(MODEL_CALL_COLUMNS + """
                       , mc.prompt, mc.response
                  FROM agent_model_calls mc JOIN agent_steps s ON s.id = mc.step_id
                 WHERE mc.run_id = ? AND mc.id = ?
                """, (rs, n) -> {
                    AgentModelCallDetail detail = new AgentModelCallDetail(modelCall(rs),
                            object(rs.getString("prompt")));
                    String response = rs.getString("response");
                    detail.setResponse(response == null ? null : object(response));
                    return detail;
                }, runId, modelCallId);
        if (found.isEmpty()) {
            throw new NotFoundException("Agent model call", modelCallId.toString());
        }
        return found.getFirst();
    }

    /** The run's tool calls, oldest first, with the verdict id of a gated call. */
    public Slice<AgentToolCall> toolCalls(UUID runId, int page, int limit) {
        run(runId);
        long total = count("agent_tool_calls", runId);
        List<AgentToolCall> items = jdbc.query("""
                SELECT tc.id, s.step_number, tc.tool_use_id, tc.tool_name, tc.status, tc.input, tc.output,
                       tc.error, tc.started_at, tc.ended_at, v.id AS verdict_id
                  FROM agent_tool_calls tc
                  JOIN agent_steps s ON s.id = tc.step_id
                  LEFT JOIN agent_verdicts v ON v.tool_call_id = tc.id
                 WHERE tc.run_id = ? ORDER BY tc.started_at, tc.id LIMIT ? OFFSET ?
                """, (rs, n) -> {
                    AgentToolCall call = new AgentToolCall(rs.getObject("id", UUID.class), rs.getInt("step_number"),
                            rs.getString("tool_use_id"), rs.getString("tool_name"),
                            AgentToolCall.StatusEnum.fromValue(rs.getString("status")),
                            anyJson(rs.getString("input")), time(rs, "started_at"));
                    call.setOutput(rs.getString("output"));
                    call.setError(rs.getString("error"));
                    call.setEndedAt(time(rs, "ended_at"));
                    call.setVerdictId(rs.getObject("verdict_id", UUID.class));
                    return call;
                }, runId, limit, offset(page, limit));
        return new Slice<>(items, total, page, limit);
    }

    /** The run's verdicts, oldest first, with their approval and their cited precedents resolved. */
    public Slice<AgentVerdict> verdicts(UUID runId, int page, int limit) {
        run(runId);
        long total = count("agent_verdicts", runId);
        Map<UUID, List<String>> cited = new HashMap<>();
        List<AgentVerdict> items = jdbc.query("""
                SELECT v.id, v.tool_call_id, s.step_number, tc.tool_name, v.operation_class, v.outcome,
                       v.confidence, v.inputs, v.cited_precedents, v.decided_at,
                       a.id AS approval_id, a.state AS approval_state, a.created_at AS approval_created_at,
                       a.decided_at AS approval_decided_at, a.decided_by AS approval_decided_by
                  FROM agent_verdicts v
                  JOIN agent_tool_calls tc ON tc.id = v.tool_call_id
                  JOIN agent_steps s ON s.id = tc.step_id
                  LEFT JOIN LATERAL (SELECT * FROM agent_approvals ap WHERE ap.verdict_id = v.id
                                      ORDER BY ap.created_at DESC, ap.id LIMIT 1) a ON true
                 WHERE v.run_id = ? ORDER BY v.decided_at, v.id LIMIT ? OFFSET ?
                """, (rs, n) -> {
                    Map<String, Object> inputs = object(rs.getString("inputs"));
                    AgentVerdict verdict = new AgentVerdict(rs.getObject("id", UUID.class),
                            rs.getObject("tool_call_id", UUID.class), rs.getInt("step_number"),
                            rs.getString("tool_name"),
                            AgentVerdict.OperationClassEnum.fromValue(rs.getString("operation_class")),
                            AgentVerdict.OutcomeEnum.fromValue(rs.getString("outcome")), inputs, new ArrayList<>(),
                            time(rs, "decided_at"));
                    verdict.setReason(text(inputs.get("reason")));
                    verdict.setCap(text(inputs.get("cap")));
                    BigDecimal confidence = rs.getBigDecimal("confidence");
                    verdict.setConfidence(confidence);
                    UUID approvalId = rs.getObject("approval_id", UUID.class);
                    if (approvalId != null) {
                        AgentApproval approval = new AgentApproval(approvalId,
                                AgentApproval.StateEnum.fromValue(rs.getString("approval_state")),
                                time(rs, "approval_created_at"));
                        approval.setDecidedAt(time(rs, "approval_decided_at"));
                        approval.setDecidedBy(rs.getString("approval_decided_by"));
                        verdict.setApproval(approval);
                    }
                    cited.put(verdict.getId(), strings(rs.getString("cited_precedents")));
                    return verdict;
                }, runId, limit, offset(page, limit));
        Resolution resolution = resolve(cited.values());
        items.forEach(verdict -> cited.get(verdict.getId())
                .forEach(sourceId -> verdict.getCitedPrecedents().add(resolution.precedent(sourceId))));
        return new Slice<>(items, total, page, limit);
    }

    /** The decision-log rows behind the cited source ids of one page, read in two queries. */
    private record Resolution(Map<UUID, AgentCitedPrecedent> approvals, Map<UUID, AgentCitedPrecedent> undos) {

        AgentCitedPrecedent precedent(String sourceId) {
            if (sourceId.startsWith(PastEventPrecedent.EVENTS)) {
                AgentCitedPrecedent event = new AgentCitedPrecedent(sourceId,
                        AgentCitedPrecedent.SourceEnum.PAST_EVENT, true);
                event.setEventCode(sourceId.substring(PastEventPrecedent.EVENTS.length()));
                return event;
            }
            boolean undo = sourceId.startsWith(DecisionPrecedent.UNDOS);
            Optional<UUID> id = idOf(sourceId);
            AgentCitedPrecedent found = id.map(key -> (undo ? undos : approvals).get(key)).orElse(null);
            if (found != null) {
                return copy(found, sourceId);
            }
            return new AgentCitedPrecedent(sourceId,
                    undo ? AgentCitedPrecedent.SourceEnum.UNDO : AgentCitedPrecedent.SourceEnum.APPROVAL, false);
        }

        private static AgentCitedPrecedent copy(AgentCitedPrecedent found, String sourceId) {
            AgentCitedPrecedent copy = new AgentCitedPrecedent(sourceId, found.getSource(), true);
            copy.setToolCallId(found.getToolCallId());
            copy.setRunId(found.getRunId());
            copy.setToolName(found.getToolName());
            copy.setApprovalState(found.getApprovalState());
            copy.setUndoOutcome(found.getUndoOutcome());
            copy.setAt(found.getAt());
            return copy;
        }
    }

    private Resolution resolve(Collection<List<String>> citedLists) {
        Set<UUID> approvalIds = new LinkedHashSet<>();
        Set<UUID> undoIds = new LinkedHashSet<>();
        citedLists.forEach(list -> list.forEach(sourceId -> {
            if (sourceId.startsWith(DecisionPrecedent.APPROVALS)) {
                idOf(sourceId).ifPresent(approvalIds::add);
            } else if (sourceId.startsWith(DecisionPrecedent.UNDOS)) {
                idOf(sourceId).ifPresent(undoIds::add);
            }
        }));
        Map<UUID, AgentCitedPrecedent> approvals = new HashMap<>();
        if (!approvalIds.isEmpty()) {
            named.query("""
                    SELECT a.id, a.state, a.decided_at, tc.id AS tool_call_id, tc.run_id, tc.tool_name
                      FROM agent_approvals a
                      JOIN agent_verdicts v ON v.id = a.verdict_id
                      JOIN agent_tool_calls tc ON tc.id = v.tool_call_id
                     WHERE a.id IN (:ids)
                    """, new MapSqlParameterSource("ids", approvalIds), (ResultSet rs) -> {
                        AgentCitedPrecedent precedent = decision(rs, AgentCitedPrecedent.SourceEnum.APPROVAL);
                        precedent.setApprovalState(AgentCitedPrecedent.ApprovalStateEnum.fromValue(
                                rs.getString("state")));
                        precedent.setAt(time(rs, "decided_at"));
                        approvals.put(rs.getObject("id", UUID.class), precedent);
                    });
        }
        Map<UUID, AgentCitedPrecedent> undos = new HashMap<>();
        if (!undoIds.isEmpty()) {
            named.query("""
                    SELECT u.id, u.outcome, u.performed_at, tc.id AS tool_call_id, tc.run_id, tc.tool_name
                      FROM agent_undos u JOIN agent_tool_calls tc ON tc.id = u.tool_call_id
                     WHERE u.id IN (:ids)
                    """, new MapSqlParameterSource("ids", undoIds), (ResultSet rs) -> {
                        AgentCitedPrecedent precedent = decision(rs, AgentCitedPrecedent.SourceEnum.UNDO);
                        precedent.setUndoOutcome(AgentCitedPrecedent.UndoOutcomeEnum.fromValue(
                                rs.getString("outcome")));
                        precedent.setAt(time(rs, "performed_at"));
                        undos.put(rs.getObject("id", UUID.class), precedent);
                    });
        }
        return new Resolution(approvals, undos);
    }

    private static AgentCitedPrecedent decision(ResultSet rs, AgentCitedPrecedent.SourceEnum source)
            throws SQLException {
        AgentCitedPrecedent precedent = new AgentCitedPrecedent("", source, true);
        precedent.setToolCallId(rs.getObject("tool_call_id", UUID.class));
        precedent.setRunId(rs.getObject("run_id", UUID.class));
        precedent.setToolName(rs.getString("tool_name"));
        return precedent;
    }

    private static Optional<UUID> idOf(String sourceId) {
        int colon = sourceId.indexOf(':');
        try {
            return Optional.of(UUID.fromString(sourceId.substring(colon + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static AgentModelCall modelCall(ResultSet rs) throws SQLException {
        AgentTokenUsage usage = new AgentTokenUsage();
        usage.setInputTokens(nullableLong(rs, "input_tokens"));
        usage.setOutputTokens(nullableLong(rs, "output_tokens"));
        usage.setCacheCreationInputTokens(nullableLong(rs, "cache_creation_input_tokens"));
        usage.setCacheReadInputTokens(nullableLong(rs, "cache_read_input_tokens"));
        List<AgentChosenTool> chosen = new ArrayList<>();
        String chosenJson = rs.getString("chosen_tools");
        if (chosenJson != null) {
            objects(chosenJson).forEach(tool -> chosen.add(new AgentChosenTool(text(tool.get("id")),
                    text(tool.get("name")))));
        }
        AgentModelCall call = new AgentModelCall(rs.getObject("id", UUID.class), rs.getInt("step_number"),
                AgentModelCall.StatusEnum.fromValue(rs.getString("status")), chosen, usage, time(rs, "started_at"));
        call.setModel(rs.getString("model"));
        String stopReason = rs.getString("stop_reason");
        call.setStopReason(stopReason == null ? null : AgentModelCall.StopReasonEnum.fromValue(stopReason));
        call.setError(rs.getString("error"));
        call.setEndedAt(time(rs, "ended_at"));
        return call;
    }

    private long count(String table, UUID runId) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE run_id = ?", Long.class, runId);
        return total == null ? 0 : total;
    }

    private static long offset(int page, int limit) {
        return (long) page * limit;
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp at = rs.getTimestamp(column);
        return at == null ? null : at.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static List<UUID> uuids(Array array) throws SQLException {
        return array == null ? List.of() : new ArrayList<>(Arrays.asList((UUID[]) array.getArray()));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Object anyJson(String json) {
        return read(json, new TypeReference<Object>() { });
    }

    private static Map<String, Object> object(String json) {
        return read(json, OBJECT);
    }

    private static List<Map<String, Object>> objects(String json) {
        return read(json, OBJECTS);
    }

    private static List<String> strings(String json) {
        return read(json, new TypeReference<List<String>>() { });
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            // A column the log wrote as JSON is not JSON: a fault of agent-service. The cause is not
            // chained: its message may quote the stored text (#1202)
            throw new IllegalStateException("Stored decision-log JSON is unreadable: " + e.getClass().getName());
        }
    }
}
