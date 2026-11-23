package ch.batbern.agent.precedent;

import ch.batbern.agent.run.PrincipalKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Reads the decision log for precedents (story 6, AD-8): the decided approvals and the undos of the
 * calls of one tool, each with the principal kind of the run that made the call. Plain reads; the
 * writers of {@code agent_approvals} and {@code agent_undos} are the gate (story 7) and the
 * oversight console. A {@code @Component}, not a {@code @Repository}: its own exceptions (a run without
 * a grant) must reach the caller untranslated.
 */
@Component
public class DecisionLogPrecedents {

    /**
     * The run's principal kind and the candidate rows of one tool, read in one transaction.
     *
     * @param principalKind    whom the proposing run acts for
     * @param decidedApprovals approvals APPROVED or DENIED of calls of the tool, newest decision first
     * @param undos            undos of calls of the tool, newest first
     */
    public record Candidates(PrincipalKind principalKind, List<DecisionPrecedent> decidedApprovals,
                             List<DecisionPrecedent> undos) {
    }

    private static final String DECIDED_APPROVALS = """
            SELECT a.id, a.state, a.decided_at, tc.id AS tool_call_id, tc.run_id, tc.tool_name, r.principal_kind
              FROM agent_approvals a
              JOIN agent_verdicts v ON v.id = a.verdict_id
              JOIN agent_tool_calls tc ON tc.id = v.tool_call_id
              JOIN agent_runs r ON r.id = tc.run_id
             WHERE tc.tool_name = ? AND a.state IN ('APPROVED', 'DENIED')
             ORDER BY a.decided_at DESC, a.id
            """;

    private static final String UNDOS = """
            SELECT u.id, u.outcome, u.performed_at, tc.id AS tool_call_id, tc.run_id, tc.tool_name, r.principal_kind
              FROM agent_undos u
              JOIN agent_tool_calls tc ON tc.id = u.tool_call_id
              JOIN agent_runs r ON r.id = tc.run_id
             WHERE tc.tool_name = ?
             ORDER BY u.performed_at DESC, u.id
            """;

    private final JdbcTemplate jdbc;

    public DecisionLogPrecedents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The candidates for a call of {@code toolName} proposed by {@code runId}, from one snapshot
     * (REPEATABLE READ), so approvals and undos agree with each other.
     *
     * @throws NoSuchElementException when there is no such run
     * @throws IllegalStateException  when the run has no grant, so no principal kind
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Candidates candidates(UUID runId, String toolName) {
        return new Candidates(principalKindOf(runId), decidedApprovals(toolName), undos(toolName));
    }

    private PrincipalKind principalKindOf(UUID runId) {
        List<String> kinds = jdbc.query("SELECT principal_kind FROM agent_runs WHERE id = ?",
                (rs, row) -> rs.getString(1), runId);
        if (kinds.isEmpty()) {
            throw new NoSuchElementException("No run " + runId);
        }
        if (kinds.getFirst() == null) {
            throw new IllegalStateException("Run " + runId + " has no grant, so no principal kind");
        }
        return PrincipalKind.valueOf(kinds.getFirst());
    }

    private List<DecisionPrecedent> decidedApprovals(String toolName) {
        return jdbc.query(DECIDED_APPROVALS, (rs, row) -> precedent(rs,
                "APPROVED".equals(rs.getString("state")) ? PrecedentKind.APPROVAL : PrecedentKind.DENIAL,
                DecisionPrecedent.APPROVALS, "decided_at", null), toolName);
    }

    private List<DecisionPrecedent> undos(String toolName) {
        return jdbc.query(UNDOS, (rs, row) -> precedent(rs, PrecedentKind.UNDO, DecisionPrecedent.UNDOS,
                "performed_at", rs.getString("outcome")), toolName);
    }

    private static DecisionPrecedent precedent(ResultSet rs, PrecedentKind kind, String prefix, String atColumn,
                                               String undoOutcome) throws SQLException {
        String kindOfRun = rs.getString("principal_kind");
        Timestamp at = rs.getTimestamp(atColumn);
        return new DecisionPrecedent(kind, prefix + rs.getObject("id", UUID.class),
                rs.getObject("tool_call_id", UUID.class), rs.getObject("run_id", UUID.class),
                rs.getString("tool_name"), kindOfRun == null ? null : PrincipalKind.valueOf(kindOfRun),
                at.toInstant(), undoOutcome);
    }
}
