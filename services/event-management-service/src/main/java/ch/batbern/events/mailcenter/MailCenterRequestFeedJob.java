package ch.batbern.events.mailcenter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * ADR-016 decision 4: the Mail Center pulls merge requests (#1237) and company
 * logo requests (#1246) from company-user-management every minute, instead of being called by it.
 * An OPEN request gets its conversation ({@link RequestConversations#open}); a decided one has its
 * conversation set DONE by the organizer who decided ({@link RequestConversations#close}), unless the
 * requester wrote after the decision (that answer reopened it and stays open). A request
 * opened and decided between two polls arrives decided without a conversation: it is opened, then
 * closed, so the Mail Center keeps the record.
 *
 * <p>Both operations are idempotent, so a page read twice changes nothing. The cursor
 * ({@code feed_cursors}, V153) is saved after each page. Any failure (CUMS down, a bad item) stops
 * the run with the cursor where it was: an ERROR line and the counter
 * {@value #FAILURES} ({@code ScheduledJobFailures} is not used because a failed poll skips no item;
 * the next poll retries it). No exception leaves the job. It sends no mail: opening a request
 * conversation stores an inbound message, nothing goes out.
 *
 * <p>Without a stored cursor the first call goes without {@code since}: CUMS then starts just before
 * the oldest open request, so decided history is not opened again.
 */
@Component
@Slf4j
public class MailCenterRequestFeedJob {

    static final String FEED = "cums.mail-center-requests";
    static final String FAILURES = "mailcenter.request_feed.failures";
    static final int PAGE_SIZE = 100;
    /** Upper bound per run, so a stuck cursor cannot loop forever; the next poll goes on. */
    static final int MAX_PAGES = 50;
    private static final String SYSTEM = "system:mail-center-request-feed";

    private final MailCenterRequestFeedClient client;
    private final RequestConversations conversations;
    private final NamedParameterJdbcTemplate jdbc;
    private final Counter failures;

    public MailCenterRequestFeedJob(MailCenterRequestFeedClient client, RequestConversations conversations,
            NamedParameterJdbcTemplate jdbc, ObjectProvider<MeterRegistry> meterRegistry) {
        this.client = client;
        this.conversations = conversations;
        this.jdbc = jdbc;
        this.failures = Counter.builder(FAILURES)
                .description("Polls of the CUMS Mail Center request feed that failed; the cursor stayed")
                .register(meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    @Scheduled(cron = "${batbern.mail-center-request-feed.cron:0 * * * * *}")
    @SchedulerLock(name = "MailCenterRequestFeedJob_poll", lockAtLeastFor = "PT10S", lockAtMostFor = "PT5M")
    public void poll() {
        pull();
    }

    /** One run: pages until CUMS has nothing new. Returns the number of items applied. */
    public int pull() {
        int applied = 0;
        try {
            String cursor = cursor();
            for (int page = 0; page < MAX_PAGES; page++) {
                MailCenterRequestFeedClient.Page next = client.fetch(cursor, PAGE_SIZE);
                for (MailCenterRequestFeedClient.Item item : next.items()) {
                    apply(item);
                    applied++;
                }
                if (!next.nextCursor().equals(cursor)) {
                    save(next.nextCursor());
                    cursor = next.nextCursor();
                }
                if (next.items().size() < PAGE_SIZE) {
                    break;
                }
            }
            if (applied > 0) {
                log.info("MAIL_CENTER_REQUEST_FEED applied={}", applied);
            }
        } catch (Exception e) {
            failures.increment();
            log.error("MAIL_CENTER_REQUEST_FEED failed after {} item(s), cursor kept: {}", applied, e.toString(), e);
        }
        return applied;
    }

    private void apply(MailCenterRequestFeedClient.Item item) {
        RequestConversations.Kind kind = RequestConversations.Kind.valueOf(item.kind().name());
        boolean existed = conversations.exists(kind, item.requestId());
        // open() returns the existing conversation when there is one, so a decided request is only
        // opened here when it was decided before any poll saw it open. Without an address (requester
        // profile gone) there is nobody to answer: nothing is opened, a close still applies.
        if (!existed && item.requesterEmail() != null && !item.requesterEmail().isBlank()) {
            conversations.open(kind, item.requestId(), item.requesterName(), item.requesterEmail(), item.subject(),
                    item.note());
        }
        if (!item.open()) {
            // A conversation opened before this pass closes only if no mail came in after the
            // decision; one opened in this pass closes regardless (its opening mail is newer).
            conversations.close(kind, item.requestId(),
                    item.decidedBy() == null || item.decidedBy().isBlank() ? SYSTEM : item.decidedBy(),
                    existed ? Instant.parse(item.changedAt()) : null);
        }
    }

    private String cursor() {
        List<String> found = jdbc.queryForList("SELECT cursor FROM feed_cursors WHERE feed = :feed",
                new MapSqlParameterSource("feed", FEED), String.class);
        return found.isEmpty() ? null : found.get(0);
    }

    private void save(String cursor) {
        jdbc.update("""
                INSERT INTO feed_cursors (feed, cursor, updated_at) VALUES (:feed, :cursor, now())
                ON CONFLICT (feed) DO UPDATE SET cursor = EXCLUDED.cursor, updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource("feed", FEED).addValue("cursor", cursor));
    }
}
