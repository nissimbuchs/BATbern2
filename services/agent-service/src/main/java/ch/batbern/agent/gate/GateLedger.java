package ch.batbern.agent.gate;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.precedent.Precedents;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.OutboundMail;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides and records a verdict in one transaction (story 7, AD-7, AD-8, AD-9): reads the run row
 * and the recipients already mailed, applies {@link GateRules} and {@link GateCaps}, and writes the
 * {@code agent_verdicts} row with its inputs and cited precedents, plus a PENDING
 * {@code agent_approvals} row for a pending verdict. For a mail the transaction first takes one
 * advisory lock, so two runs cannot both pass on a count of one. A {@code @Component}, not a
 * {@code @Repository}: its exceptions reach the caller untranslated.
 */
@Component
public class GateLedger {

    /** The day of the per-day recipient count: the daily budget's day. */
    public static final ZoneId DAY_ZONE = RunLimit.DAY_ZONE;

    /** The advisory lock key that serializes mail verdicts across runs and tasks. */
    static final long MAIL_LOCK_KEY = 0x6167656E74_6D61L; // "agentma"

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Non-organizer addresses of mail verdicts that were let go out: RUN (counted on the verdict's
     * day, even if the tool then failed: the mail may have left), or a pending one APPROVED (counted
     * on the day of the approval, when it goes out). {@code %s} narrows by run or by day on
     * {@code sent_at}.
     */
    private static final String COUNTED_RECIPIENTS = """
            SELECT DISTINCT c.address FROM (
                SELECT r.address, v.run_id,
                       CASE WHEN v.outcome = 'RUN' THEN v.decided_at ELSE a.decided_at END AS sent_at
                  FROM agent_verdicts v
                  LEFT JOIN agent_approvals a ON a.verdict_id = v.id AND a.state = 'APPROVED'
                 CROSS JOIN LATERAL jsonb_array_elements_text(
                       COALESCE(v.inputs -> 'mail' -> 'nonOrganizerRecipients', '[]'::jsonb)) AS r(address)
                 WHERE v.outcome = 'RUN' OR a.id IS NOT NULL
            ) c
             WHERE %s
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final GateRules.Thresholds thresholds;

    public GateLedger(JdbcTemplate jdbc, Clock clock, AgentProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        AgentProperties.Limits limits = properties.limits();
        this.thresholds = new GateRules.Thresholds(limits.bindingApprovals(), limits.nonOrganizerRecipients());
    }

    /**
     * The call's mail as the tool resolved it.
     *
     * @param mail       the mail; {@code null} when the call sends none or it is unresolved
     * @param unresolved whether the tool could not resolve it
     */
    public record MailResolution(OutboundMail mail, boolean unresolved) {

        /** The call sends no mail. */
        public static final MailResolution NONE = new MailResolution(null, false);
        /** The tool could not say whom the mail would reach. */
        public static final MailResolution UNRESOLVED = new MailResolution(null, true);

        public static MailResolution of(Optional<OutboundMail> mail) {
            return mail.map(found -> new MailResolution(found, false)).orElse(NONE);
        }
    }

    /**
     * Decides the call of {@code tool} logged as {@code toolCallId} in {@code runId} and writes the
     * verdict.
     *
     * @throws NoSuchElementException when there is no such run
     * @throws IllegalStateException  when the run has no grant
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Verdict decideAndRecord(UUID runId, UUID toolCallId, AgentTool tool, MailResolution mail,
                                   Precedents precedents, GateCaps caps) {
        boolean sendsMail = mail.mail() != null || mail.unresolved();
        if (sendsMail) {
            jdbc.execute("SELECT pg_advisory_xact_lock(" + MAIL_LOCK_KEY + ")");
        }
        RunRow run = runRow(runId);
        Instant now = clock.instant();
        LocalDate day = LocalDate.ofInstant(now, DAY_ZONE);
        Set<String> runBefore = sendsMail ? countedRecipientsOfRun(runId) : Set.of();
        Set<String> dayBefore = sendsMail ? countedRecipientsOfDay(day) : Set.of();

        GateFacts.Mail mailFacts = mail.mail() == null ? null : new GateFacts.Mail(mail.mail().distinctAddressCount(),
                mail.mail().organizerCount(), mail.mail().nonOrganizerAddresses());
        GateFacts facts = new GateFacts(tool.operationClass(), run.trigger(), mailFacts, mail.unresolved(), runBefore,
                dayBefore, precedents.approvals().size(), precedents.denials().size());
        GateRules.Decision decision = GateRules.decide(facts, thresholds);

        RunLimit cap = null;
        if (decision.outcome() == VerdictOutcome.RUN) {
            cap = caps.reached(runId, decision.effectiveClass(), sendsMail).orElse(null);
            if (cap != null) {
                decision = new GateRules.Decision(VerdictOutcome.REFUSED, decision.effectiveClass(),
                        GateReason.CAP_REACHED, decision.confidence(), decision.recipientsThisRun(),
                        decision.recipientsToday());
            }
        }

        UUID verdictId = UUID.randomUUID();
        List<String> cited = precedents.sourceIds();
        ObjectNode inputs = inputs(tool, run, mail, day, precedents, decision, cap);
        jdbc.update("INSERT INTO agent_verdicts (id, run_id, tool_call_id, operation_class, outcome, confidence, "
                        + "inputs, cited_precedents, decided_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)",
                verdictId, runId, toolCallId, decision.effectiveClass().name(), decision.outcome().name(),
                decision.confidence(), inputs.toString(), JSON.valueToTree(cited).toString(), Timestamp.from(now));
        UUID approvalId = null;
        if (decision.outcome() == VerdictOutcome.PENDING_APPROVAL) {
            approvalId = UUID.randomUUID();
            jdbc.update("INSERT INTO agent_approvals (id, run_id, verdict_id, state, created_at) "
                    + "VALUES (?, ?, ?, 'PENDING', ?)", approvalId, runId, verdictId, Timestamp.from(now));
        }
        return new Verdict(verdictId, decision.outcome(), decision.effectiveClass(), decision.reason(), approvalId,
                cap, cited);
    }

    /** What the gate reads of the run: never taken from the caller. */
    private record RunRow(RunTrigger trigger, PrincipalKind principalKind) {
    }

    private RunRow runRow(UUID runId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT trigger, principal_kind FROM agent_runs WHERE id = ?", runId);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("No run " + runId);
        }
        Object kind = rows.getFirst().get("principal_kind");
        if (kind == null) {
            throw new IllegalStateException("Run " + runId + " has no grant, so no principal kind");
        }
        return new RunRow(RunTrigger.valueOf((String) rows.getFirst().get("trigger")),
                PrincipalKind.valueOf((String) kind));
    }

    private Set<String> countedRecipientsOfRun(UUID runId) {
        return new LinkedHashSet<>(jdbc.queryForList(COUNTED_RECIPIENTS.formatted("c.run_id = ?"), String.class,
                runId));
    }

    private Set<String> countedRecipientsOfDay(LocalDate day) {
        Instant from = day.atStartOfDay(DAY_ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(DAY_ZONE).toInstant();
        return new LinkedHashSet<>(jdbc.queryForList(
                COUNTED_RECIPIENTS.formatted("c.sent_at >= ? AND c.sent_at < ?"), String.class,
                Timestamp.from(from), Timestamp.from(to)));
    }

    /** The verdict's inputs: what the rules saw and why they decided, never model or inbound text. */
    private ObjectNode inputs(AgentTool tool, RunRow run, MailResolution mail, LocalDate day,
                              Precedents precedents, GateRules.Decision decision, RunLimit cap) {
        ObjectNode inputs = JSON.createObjectNode()
                .put("tool", tool.name())
                .put("toolClass", tool.operationClass().wireName())
                .put("effectiveClass", decision.effectiveClass().wireName())
                .put("trigger", run.trigger().name())
                .put("principalKind", run.principalKind().name());
        if (mail.unresolved()) {
            inputs.putObject("mail").put("unresolved", true);
        } else if (mail.mail() != null) {
            ObjectNode mailNode = inputs.putObject("mail")
                    .put("recipients", mail.mail().distinctAddressCount())
                    .put("organizerRecipients", mail.mail().organizerCount());
            ArrayNode addresses = mailNode.putArray("nonOrganizerRecipients");
            mail.mail().nonOrganizerAddresses().forEach(addresses::add);
        } else {
            inputs.putNull("mail");
        }
        inputs.put("recipientsThisRun", decision.recipientsThisRun())
                .put("recipientsToday", decision.recipientsToday())
                .put("day", day.toString());
        inputs.putObject("precedents")
                .put("approvals", precedents.approvals().size())
                .put("denials", precedents.denials().size())
                .put("undos", precedents.undos().size())
                .put("pastEvents", precedents.pastEvents().size())
                .put("history", precedents.historyStatus().name());
        inputs.put("requiredApprovals", thresholds.bindingApprovals())
                .put("maxNonOrganizerRecipients", thresholds.nonOrganizerRecipients())
                .put("reason", decision.reason().name());
        if (cap == null) {
            inputs.putNull("cap");
        } else {
            inputs.put("cap", cap.name());
        }
        return inputs;
    }
}
