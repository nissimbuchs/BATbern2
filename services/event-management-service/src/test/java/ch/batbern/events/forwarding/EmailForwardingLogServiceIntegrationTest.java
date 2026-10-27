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
    @DisplayName("should_rejectUnknownOutcome_atDatabaseLevel")
    void should_rejectUnknownOutcome_atDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO email_forwarding_log (s3_key, outcome) VALUES (?, 'sent')", KEY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
