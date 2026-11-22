package ch.batbern.agent.gate;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.tools.OperationClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The per-run cap on {@code reply} calls (story 8, R9, {@code agent.limits.run-replies}, 2): the gate
 * lets that many {@code reply} calls of a run run alone; the next one reaches {@link RunLimit#REPLIES}
 * and stops the run. Counted are the run's verdicts that let a call of effective class {@code reply}
 * run, whether the tool then succeeded or not (the mail may have left). Asked inside the gate's
 * transaction; a run's calls are decided one after the other.
 */
@Component
public class ReplyCap implements GateCaps {

    private final JdbcTemplate jdbc;
    private final int runReplies;

    public ReplyCap(JdbcTemplate jdbc, AgentProperties properties) {
        this.jdbc = jdbc;
        this.runReplies = properties.limits().runReplies();
    }

    @Override
    public Optional<RunLimit> reached(UUID runId, OperationClass effectiveClass, boolean mail) {
        if (effectiveClass != OperationClass.REPLY) {
            return Optional.empty();
        }
        Integer ran = jdbc.queryForObject("SELECT count(*) FROM agent_verdicts WHERE run_id = ? "
                + "AND outcome = 'RUN' AND operation_class = 'REPLY'", Integer.class, runId);
        return ran != null && ran >= runReplies ? Optional.of(RunLimit.REPLIES) : Optional.empty();
    }
}
