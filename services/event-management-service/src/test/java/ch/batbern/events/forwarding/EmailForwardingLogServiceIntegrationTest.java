package ch.batbern.events.forwarding;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.EmailForwardingLog;
import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.events.repository.EmailForwardingLogRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Idempotency table against real PostgreSQL (V128). Not {@code @Transactional}: the service
 * commits in its own transactions on purpose, which is exactly what is under test.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EmailForwardingLogService (PostgreSQL)")
class EmailForwardingLogServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String KEY = "forwarding/it-" + System.nanoTime();

    @Autowired
    private EmailForwardingLogService service;
    @Autowired
    private EmailForwardingLogRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM email_forwarding_log WHERE s3_key LIKE 'forwarding/it-%'");
    }

    @Test
    @DisplayName("should_claimOnce_when_sameKeyClaimedTwice")
    void should_claimOnce_when_sameKeyClaimedTwice() {
        assertThat(service.claim(KEY)).isTrue();
        assertThat(service.claim(KEY)).isFalse();

        EmailForwardingLog entry = repository.findByS3Key(KEY).orElseThrow();
        assertThat(entry.getOutcome()).isEqualTo(EmailForwardingOutcome.PROCESSING);
        assertThat(entry.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_allowReclaim_when_released")
    void should_allowReclaim_when_released() {
        assertThat(service.claim(KEY)).isTrue();
        service.release(KEY);

        assertThat(service.claim(KEY)).isTrue();
    }

    @Test
    @DisplayName("should_recordOutcomeAndCounts_when_completed")
    void should_recordOutcomeAndCounts_when_completed() {
        service.claim(KEY);
        service.complete(KEY, EmailForwardingOutcome.PARTIAL, "info,batbern60-participants", 155, 1);

        EmailForwardingLog entry = repository.findByS3Key(KEY).orElseThrow();
        assertThat(entry.getOutcome()).isEqualTo(EmailForwardingOutcome.PARTIAL);
        assertThat(entry.getAliases()).isEqualTo("info,batbern60-participants");
        assertThat(entry.getRecipientCount()).isEqualTo(155);
        assertThat(entry.getFailedCount()).isEqualTo(1);
        assertThat(entry.getCompletedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT outcome FROM email_forwarding_log WHERE s3_key = ?", String.class, KEY)).isEqualTo("partial");
    }

    @Test
    @DisplayName("should_reclaim_when_claimIsStaleAndNothingWasSent")
    void should_reclaim_when_claimIsStaleAndNothingWasSent() {
        // A task that died between claim and first send (Fargate Spot, deploy, OOM) must not
        // turn the SQS redelivery into a silent DUPLICATE: nothing went out, a retry is safe.
        service.claim(KEY);
        ageClaim(600);

        assertThat(service.claim(KEY)).isTrue();
    }

    @Test
    @DisplayName("should_notReclaim_when_claimIsRecent")
    void should_notReclaim_when_claimIsRecent() {
        service.claim(KEY);

        assertThat(service.claim(KEY)).isFalse();
    }

    @Test
    @DisplayName("should_neverReclaim_when_sendingHadStarted")
    void should_neverReclaim_when_sendingHadStarted() {
        service.claim(KEY);
        service.markSending(KEY);
        ageClaim(600);

        assertThat(service.claim(KEY)).isFalse();
        assertThat(service.isStuckAfterSending(KEY)).isTrue();
    }

    @Test
    @DisplayName("should_notReportStuck_when_completedOrRecent")
    void should_notReportStuck_when_completedOrRecent() {
        service.claim(KEY);
        service.markSending(KEY);
        assertThat(service.isStuckAfterSending(KEY)).isFalse();

        ageClaim(600);
        service.complete(KEY, EmailForwardingOutcome.FORWARDED, "info", 2, 0);
        assertThat(service.isStuckAfterSending(KEY)).isFalse();
    }

    /** #1139: a stall is recorded once; later redeliveries then see a finished row, not a stall. */
    @Test
    @DisplayName("should_notReportStuckAgain_when_stallWasRecorded")
    void should_notReportStuckAgain_when_stallWasRecorded() {
        service.claim(KEY);
        service.markSending(KEY);
        ageClaim(600);
        assertThat(service.isStuckAfterSending(KEY)).isTrue();

        service.complete(KEY, EmailForwardingOutcome.STALLED, null, 0, 0);

        assertThat(service.isStuckAfterSending(KEY)).isFalse();
        assertThat(service.claim(KEY)).isFalse();
    }

    /** #1138: rows past the retention go; the S3 object behind them is gone after 7 days anyway. */
    @Test
    @DisplayName("should_purgeOnlyOldRows_when_retentionApplied")
    void should_purgeOnlyOldRows_when_retentionApplied() {
        service.claim(KEY);
        service.complete(KEY, EmailForwardingOutcome.FORWARDED, "info", 1, 0);
        jdbcTemplate.update("UPDATE email_forwarding_log SET received_at = now() - interval '91 days'"
                + " WHERE s3_key = ?", KEY);
        String recent = KEY + "-recent";
        service.claim(recent);

        assertThat(service.purgeOlderThanDays(90)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_forwarding_log WHERE s3_key IN (?, ?)", Integer.class, KEY, recent))
                .isEqualTo(1);
        jdbcTemplate.update("DELETE FROM email_forwarding_log WHERE s3_key = ?", recent);
    }

    private void ageClaim(int seconds) {
        jdbcTemplate.update("UPDATE email_forwarding_log SET received_at = now() - make_interval(secs => ?),"
                + " sending_started_at = CASE WHEN sending_started_at IS NULL THEN NULL"
                + " ELSE now() - make_interval(secs => ?) END WHERE s3_key = ?", seconds, seconds, KEY);
    }

    @Test
    @DisplayName("should_rejectUnknownOutcome_atDatabaseLevel")
    void should_rejectUnknownOutcome_atDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO email_forwarding_log (s3_key, outcome) VALUES (?, 'sent')", KEY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
