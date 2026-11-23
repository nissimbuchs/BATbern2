package ch.batbern.agent.loop;

import ch.batbern.agent.run.RunLimit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The model tokens already spent (story 8, R9), read from {@code agent_model_calls}: input, output,
 * cache-creation and cache-read tokens, all four, so prompt caching does not change what counts. A
 * call without usage (failed, or still running) counts 0.
 */
@Component
public class TokenLedger {

    private static final String SUM = "SELECT COALESCE(SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0) "
            + "+ COALESCE(cache_creation_input_tokens, 0) + COALESCE(cache_read_input_tokens, 0)), 0) "
            + "FROM agent_model_calls WHERE ";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TokenLedger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Tokens of every model call of {@code runId}. */
    public long ofRun(UUID runId) {
        Long sum = jdbc.queryForObject(SUM + "run_id = ?", Long.class, runId);
        return sum == null ? 0 : sum;
    }

    /** Tokens of every model call started today ({@link RunLimit#DAY_ZONE}), of all runs. */
    public long ofToday() {
        LocalDate day = LocalDate.ofInstant(clock.instant(), RunLimit.DAY_ZONE);
        Long sum = jdbc.queryForObject(SUM + "started_at >= ? AND started_at < ?", Long.class,
                Timestamp.from(day.atStartOfDay(RunLimit.DAY_ZONE).toInstant()),
                Timestamp.from(day.plusDays(1).atStartOfDay(RunLimit.DAY_ZONE).toInstant()));
        return sum == null ? 0 : sum;
    }
}
