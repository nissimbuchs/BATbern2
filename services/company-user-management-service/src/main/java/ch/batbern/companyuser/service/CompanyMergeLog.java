package ch.batbern.companyuser.service;

import ch.batbern.shared.security.Actors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Writes {@code company_merge_log} rows (#1251), the only undo record of a company merge: the holders
 * keep none, and a retry gets nothing from a holder that already moved. A completed merge writes its
 * row in the merge's transaction; an attempt that failed after holders moved writes its row in a
 * transaction of its own, so it survives the merge's rollback.
 */
@Component
@RequiredArgsConstructor
public class CompanyMergeLog {

    /** {@code company_merge_log.outcome}. */
    public enum Outcome { COMPLETED, INCOMPLETE }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    /** One row: the pair, who merged, the moved and removed row ids per {@code table.column}, and more. */
    public record Entry(String sourceKey, String targetKey, String performedBy, Map<String, List<String>> movedKeys,
            Map<String, List<String>> removedKeys, List<String> filled, Map<String, Object> snapshot) {
    }

    /** The row of a completed merge, in the merge's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(Entry entry) {
        insert(Outcome.COMPLETED, entry);
    }

    /** The row of an attempt that failed after holders moved rows: committed whatever the merge does. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incomplete(Entry entry) {
        insert(Outcome.INCOMPLETE, entry);
    }

    private void insert(Outcome outcome, Entry e) {
        jdbc.update("INSERT INTO company_merge_log (source_key, target_key, performed_by, outcome, moved_keys,"
                + " removed_keys, filled_fields, source_snapshot)"
                + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)",
                e.sourceKey(), e.targetKey(), Actors.forUsername(e.performedBy()), outcome.name(), json(e.movedKeys()),
                json(e.removedKeys()), json(e.filled()), json(e.snapshot()));
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Company merge log entry not serializable", e);
        }
    }
}
