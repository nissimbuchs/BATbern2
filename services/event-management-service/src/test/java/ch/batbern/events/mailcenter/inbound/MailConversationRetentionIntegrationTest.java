package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.scheduler.MailMessageRetentionScheduler;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Story 16.2a AC14: the nightly retention removes conversations, their inbound mail and the
 * automatic counts by the same rule as recorded mail (event date and own age both past 12 months;
 * without an event, own age alone), and nothing else.
 */
@TestPropertySource(properties = "aws.inbound-email.bucket-name=batbern-inbound-emails-test")
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Stories 16.2a, 16.2b: retention of conversations and automatic counts")
class MailConversationRetentionIntegrationTest extends AbstractIntegrationTest {

    private static final String OLD_EVENT = "BATbern918";
    private static final String FUTURE_EVENT = "BATbern919";

    @Autowired
    private MailMessageRetentionScheduler scheduler;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private S3Client s3;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        cleanUp();
        eventRepository.saveAndFlush(event(OLD_EVENT, 918, now.minus(400, ChronoUnit.DAYS)));
        eventRepository.saveAndFlush(event(FUTURE_EVENT, 919, now.plus(30, ChronoUnit.DAYS)));
        jdbc.update("DELETE FROM shedlock WHERE name = 'MailMessageRetentionScheduler_purgeExpired'");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_conversation WHERE conversation_key LIKE 'conv-ret-%'");
        jdbc.update("DELETE FROM mail_automatic_event WHERE dedupe_key LIKE 'ret-%'");
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", OLD_EVENT, FUTURE_EVENT);
    }

    @Test
    @DisplayName("deletes what is past 12 months on both counts, keeps everything else")
    void should_deleteOnlyExpired_when_purgeRuns() {
        UUID oldEventOld = conversation("conv-ret-1", OLD_EVENT, 420);
        UUID oldEventRecent = conversation("conv-ret-2", OLD_EVENT, 10);
        UUID futureEventOld = conversation("conv-ret-3", FUTURE_EVENT, 420);
        UUID noEventOld = conversation("conv-ret-4", null, 420);
        UUID noEventRecent = conversation("conv-ret-5", null, 10);
        // 16.2b AC10: an info@ conversation (PUBLIC, no event), same rule. One purge per test class:
        // ShedLock keeps the lock in-process for lockAtLeastFor, so a second run would be skipped.
        UUID infoOld = conversation("conv-ret-6", null, 420);
        UUID infoRecent = conversation("conv-ret-7", null, 30);
        jdbc.update("UPDATE mail_conversation SET audience = 'PUBLIC' WHERE id IN (?, ?)", infoOld, infoRecent);
        jdbc.update("UPDATE mail_message SET audience = 'PUBLIC', alias_local_part = 'info' "
                + "WHERE conversation_id IN (?, ?)", infoOld, infoRecent);
        // 16.6: kept attachment files go with their conversation
        jdbc.update("UPDATE mail_message SET attachments = CAST(? AS JSONB) WHERE conversation_id = ?",
                "[{\"name\":\"a.pdf\",\"size\":1,\"storageKey\":\"mail-attachments/in_old/0\"},"
                        + "{\"name\":\"big.mp4\",\"size\":9}]", infoOld);
        jdbc.update("UPDATE mail_message SET attachments = CAST(? AS JSONB) WHERE conversation_id = ?",
                "[{\"name\":\"b.pdf\",\"size\":1,\"storageKey\":\"mail-attachments/in_new/0\"}]",
                infoRecent);
        automatic("ret-1", OLD_EVENT, 420);
        automatic("ret-2", OLD_EVENT, 10);
        automatic("ret-3", FUTURE_EVENT, 420);
        automatic("ret-4", null, 420);

        scheduler.purgeExpired();

        assertThat(exists(oldEventOld)).isFalse();
        assertThat(exists(noEventOld)).isFalse();
        assertThat(exists(oldEventRecent)).isTrue();
        assertThat(exists(futureEventOld)).isTrue();
        assertThat(exists(noEventRecent)).isTrue();
        assertThat(exists(infoOld)).isFalse();
        assertThat(exists(infoRecent)).isTrue();
        ArgumentCaptor<DeleteObjectsRequest> deleted = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3).deleteObjects(deleted.capture());
        assertThat(deleted.getValue().delete().objects()).extracting(ObjectIdentifier::key)
                .containsExactly("mail-attachments/in_old/0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE conversation_id IN (?, ?)",
                Integer.class, oldEventOld, noEventOld)).isZero();
        assertThat(jdbc.queryForList("SELECT dedupe_key FROM mail_automatic_event WHERE dedupe_key LIKE 'ret-%'"
                + " ORDER BY dedupe_key", String.class)).containsExactly("ret-2", "ret-3");
    }

    private UUID conversation(String key, String eventCode, int ageDays) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(now.minus(ageDays, ChronoUnit.DAYS));
        jdbc.update("INSERT INTO mail_conversation (id, conversation_key, event_code, audience, subject, "
                + "counterpart_address, status, last_message_at, created_at) "
                + "VALUES (?, ?, ?, 'SPEAKERS', 'Frage', 'success+s@simulator.amazonses.com', 'OPEN', ?, ?)",
                id, key, eventCode, at, at);
        jdbc.update("INSERT INTO mail_message (mail_key, send_group_id, event_code, direction, audience, "
                + "content_source, subject, body_html, conversation_id, created_at) "
                + "VALUES (?, ?, ?, 'IN', 'SPEAKERS', 'OWN_TEXT', 'Frage', '<p>x</p>', ?, ?)",
                key + "-m", "in_" + key, eventCode, id, at);
        return id;
    }

    private void automatic(String key, String eventCode, int ageDays) {
        jdbc.update("INSERT INTO mail_automatic_event (dedupe_key, event_code, kind, received_at) "
                + "VALUES (?, ?, 'OUT_OF_OFFICE', ?)", key, eventCode, Timestamp.from(now.minus(ageDays, ChronoUnit.DAYS)));
    }

    private boolean exists(UUID conversation) {
        return jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE id = ?", Integer.class,
                conversation) > 0;
    }

    private static Event event(String code, int number, Instant date) {
        return Event.builder()
                .eventCode(code).title("BATbern Test Event #" + number).eventNumber(number)
                .date(date).registrationDeadline(date.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("organizer").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.ARCHIVED)
                .build();
    }
}
