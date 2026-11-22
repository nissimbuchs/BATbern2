package ch.batbern.agent.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The run ends an alarm watches (story 10, R10, Operational Envelope "Observability"): one line per
 * marker, written once when a run ends. CloudWatch metric filters on agent-service's log group match
 * the markers literally ({@code infrastructure/lib/constructs/agent-run-alarms.ts}); the CDK test
 * reads them from this file, so a rename here fails the infrastructure build instead of silencing an
 * alarm. No marker contains another.
 *
 * <p>Not alarmed: COMPLETED, and the model being unreachable, cut off or stopping (MODEL_*). Those
 * runs are handed to the organizers as expected; a missing Anthropic key or an outage of the model is
 * not agent-service's fault (decision of story 10).
 *
 * <p>A line carries the marker, the run id, the end reason and the end detail (a {@link RunLimit}
 * name or a CUMS code), nothing else (#1202).
 */
public final class RunAlarms {

    /** A run ended FAILED: a fault in agent-service itself. */
    public static final String RUN_FAILED = "AGENT_RUN_FAILED";
    /** A run was stopped by any limit of {@code agent.limits} (CAP_REACHED). */
    public static final String RUN_CAP_REACHED = "AGENT_RUN_CAP_REACHED";
    /** A run was stopped by the daily token budget (CAP_REACHED, DAILY_TOKENS): every run stops until midnight. */
    public static final String DAILY_BUDGET_BREACHED = "AGENT_DAILY_BUDGET_BREACHED";
    /** CUMS refused the run grant or a mint, or could not be asked (GRANT_REFUSED, TOKEN_REFUSED). */
    public static final String CREDENTIALS_REFUSED = "AGENT_CREDENTIALS_REFUSED";

    private static final Logger LOG = LoggerFactory.getLogger(RunAlarms.class);

    private RunAlarms() {
    }

    /** The markers a run end carries, in a fixed order; empty for an end no alarm watches. */
    public static List<String> markers(RunEndReason reason, String detail) {
        List<String> markers = new ArrayList<>(2);
        switch (reason) {
            case INTERNAL_ERROR -> markers.add(RUN_FAILED);
            case CAP_REACHED -> {
                markers.add(RUN_CAP_REACHED);
                if (RunLimit.DAILY_TOKENS.name().equals(detail)) {
                    markers.add(DAILY_BUDGET_BREACHED);
                }
            }
            case GRANT_REFUSED, TOKEN_REFUSED -> markers.add(CREDENTIALS_REFUSED);
            default -> {
                // COMPLETED and MODEL_*: no alarm
            }
        }
        return markers;
    }

    /** Writes one line per marker of the run's end. */
    public static void record(UUID runId, RunEndReason reason, String detail) {
        for (String marker : markers(reason, detail)) {
            if (reason.status() == AgentRunStatus.FAILED) {
                LOG.error("{} Agent run {} ended {} ({})", marker, runId, reason, detail);
            } else {
                LOG.warn("{} Agent run {} ended {} ({})", marker, runId, reason, detail);
            }
        }
    }
}
