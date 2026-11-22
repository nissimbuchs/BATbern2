package ch.batbern.agent.gate;

import ch.batbern.agent.MiddayClock;
import ch.batbern.agent.precedent.Precedents;
import ch.batbern.agent.tools.MailRecipient;
import ch.batbern.agent.tools.OutboundMail;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 7 deferral, closed by the refactor sweep: the advisory lock {@link GateLedger#MAIL_LOCK_KEY}
 * serializes mail verdicts across concurrent runs. Two runs each send a first reply to a different
 * person at the same moment; without the lock both would count zero recipients today and both run
 * alone. The test holds the lock itself, starts both verdicts, waits until PostgreSQL shows both
 * waiting for it in {@code pg_locks}, then releases it: one verdict runs, the other is held as
 * audience (RECIPIENTS_ABOVE_ONE) with a PENDING approval.
 */
@DisplayName("The mail lock serializes two concurrent first mails to different people")
@TestPropertySource(properties = {"agent.enabled=true", "agent.limits.run-steps=4"})
@Import(MiddayClock.class)
class GateLedgerConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private GateLedger ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Clock clock;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    @Test
    @DisplayName("two runs, two addresses, the same moment: one RUN, one PENDING_APPROVAL (RECIPIENTS_ABOVE_ONE)")
    void should_holdTheSecondMail_when_twoFirstMailsRace() throws Exception {
        MiddayClock.assertAnchored(clock);
        GateFixtureTool tool = GateFixtureTool.reply();
        UUID annaRun = inboundRun();
        UUID benRun = inboundRun();
        UUID annaCall = toolCall(annaRun, tool.name());
        UUID benCall = toolCall(benRun, tool.name());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement lock = holder.createStatement()) {
                lock.execute("SELECT pg_advisory_xact_lock(" + GateLedger.MAIL_LOCK_KEY + ")");
            }

            Future<Verdict> anna = pool.submit(() -> decide(annaRun, annaCall, tool, "anna@example.com"));
            Future<Verdict> ben = pool.submit(() -> decide(benRun, benCall, tool, "ben@example.com"));

            awaitWaitersOnMailLock(2);
            assertThat(anna.isDone()).as("anna's verdict waits for the lock").isFalse();
            assertThat(ben.isDone()).as("ben's verdict waits for the lock").isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_verdicts", Integer.class))
                    .as("no verdict written while the lock is held").isZero();

            holder.commit(); // releases the transaction-level advisory lock

            List<Verdict> verdicts = List.of(anna.get(WAIT.toSeconds(), TimeUnit.SECONDS),
                    ben.get(WAIT.toSeconds(), TimeUnit.SECONDS));

            assertThat(verdicts).extracting(Verdict::outcome)
                    .containsExactlyInAnyOrder(VerdictOutcome.RUN, VerdictOutcome.PENDING_APPROVAL);
            Verdict ran = verdicts.stream().filter(v -> v.outcome() == VerdictOutcome.RUN).findFirst().orElseThrow();
            Verdict held = verdicts.stream().filter(v -> v.outcome() == VerdictOutcome.PENDING_APPROVAL).findFirst()
                    .orElseThrow();
            assertThat(ran.reason()).isEqualTo(GateReason.RUNS_ALONE);
            assertThat(held.reason()).isEqualTo(GateReason.RECIPIENTS_ABOVE_ONE);
            assertThat(held.effectiveClass().wireName()).isEqualTo("audience");

            Map<String, Object> heldRow = jdbc.queryForMap("SELECT outcome, (inputs->>'recipientsToday')::int AS today "
                    + "FROM agent_verdicts WHERE id = ?", held.id());
            assertThat(heldRow).containsEntry("outcome", "PENDING_APPROVAL").containsEntry("today", 2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_approvals WHERE verdict_id = ? "
                    + "AND state = 'PENDING'", Integer.class, held.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_approvals", Integer.class))
                    .as("only the held mail waits for an organizer").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private Verdict decide(UUID runId, UUID toolCallId, GateFixtureTool tool, String address) {
        OutboundMail mail = OutboundMail.to(MailRecipient.external(address));
        return ledger.decideAndRecord(runId, toolCallId, tool, GateLedger.MailResolution.of(Optional.of(mail)),
                new Precedents(List.of(), List.of(), List.of(), List.of(), Precedents.HistoryStatus.NOT_ASKED),
                GateCaps.NONE);
    }

    /** Polls {@code pg_locks} until {@code count} sessions wait for the mail lock. */
    private void awaitWaitersOnMailLock(int count) throws InterruptedException {
        long key = GateLedger.MAIL_LOCK_KEY;
        Instant deadline = Instant.now().plus(WAIT);
        int waiting = 0;
        while (Instant.now().isBefore(deadline)) {
            waiting = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted "
                    + "AND classid = ? AND objid = ? AND objsubid = 1", Integer.class,
                    key >>> 32, key & 0xFFFFFFFFL);
            if (waiting == count) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("expected " + count + " sessions waiting for the mail lock, saw " + waiting);
    }

    private UUID inboundRun() {
        UUID id = UUID.randomUUID();
        Timestamp t = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, trigger, grant_run_id, principal_kind) "
                + "VALUES (?, 'RUNNING', ?, 'INBOUND_MAIL', ?, 'REQUESTER')", id, t, UUID.randomUUID());
        return id;
    }

    private UUID toolCall(UUID runId, String toolName) {
        Timestamp t = Timestamp.from(clock.instant());
        UUID step = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at) VALUES (?, ?, 1, ?)", step, runId, t);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_tool_calls (id, run_id, step_id, tool_use_id, tool_name, input, status, "
                + "started_at) VALUES (?, ?, ?, 'toolu_race', ?, '{}'::jsonb, 'STARTED', ?)", id, runId, step,
                toolName, t);
        return id;
    }
}
