package ch.batbern.events.scheduler;

import ch.batbern.events.forwarding.EmailForwardingLogService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** #1138: email_forwarding_log is pruned after 90 days. */
class EmailForwardingLogRetentionSchedulerTest {

    private final EmailForwardingLogService logService = mock(EmailForwardingLogService.class);
    private final EmailForwardingLogRetentionScheduler scheduler =
            new EmailForwardingLogRetentionScheduler(logService);

    @Test
    void should_purgeRowsOlderThan90Days_when_jobRuns() {
        scheduler.purgeOldEntries();

        verify(logService).purgeOlderThanDays(90);
    }

    @Test
    void should_notThrow_when_purgeFails() {
        doThrow(new IllegalStateException("DB down")).when(logService).purgeOlderThanDays(90);

        scheduler.purgeOldEntries();
    }
}
