package ch.batbern.events.migration;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner decision 2026-11-09 (17.0 deferred work): topic usage engagement was never measured and
 * {@code attendee_count} was written by nothing (attendance comes from registrations), so V148
 * drops both columns from {@code topic_usage_history}.
 */
class TopicUsageHistoryUnmeasuredColumnsDroppedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_notHaveEngagementScoreOrAttendeeCount_when_migrated() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'topic_usage_history'",
                String.class);

        assertThat(columns).contains("topic_id", "event_id", "used_date")
                .doesNotContain("engagement_score", "attendee_count");
    }
}
