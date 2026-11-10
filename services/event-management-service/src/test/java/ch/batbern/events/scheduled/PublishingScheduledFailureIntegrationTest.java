package ch.batbern.events.scheduled;

import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An auto-publish that fails for one event surfaces (assertion rule "no silent catch in
 * schedulers without a metric"): the job counts it in {@link ScheduledJobFailures#METRIC} and logs
 * the {@link ScheduledJobFailures#LOG_MARKER} the CloudWatch alarm matches, and goes on with the
 * next event.
 *
 * <p>The failure is real, at the database (the process boundary): a trigger refuses the update of
 * one event. No class-level {@code @Transactional}: each publish runs in its own transaction as in
 * production, so the refused one does not abort the others. The events and the trigger are removed
 * after each test.
 */
@SpringBootTest
class PublishingScheduledFailureIntegrationTest extends AbstractIntegrationTest {

    private static final String FAILING = "BATbern97101";
    private static final String HEALTHY = "BATbern97102";

    @Autowired
    private PublishingScheduledService publishingScheduledService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        tearDown();
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION test_refuse_publish() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'test: publishing of % refused', NEW.event_code;
                END;
                $$ LANGUAGE plpgsql""");
        jdbcTemplate.execute("CREATE TRIGGER test_refuse_publish BEFORE UPDATE ON events FOR EACH ROW "
                + "WHEN (NEW.event_code = '" + FAILING + "') EXECUTE FUNCTION test_refuse_publish()");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_refuse_publish ON events");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_refuse_publish()");
        for (String code : List.of(FAILING, HEALTHY)) {
            eventRepository.findByEventCode(code).ifPresent(eventRepository::delete);
        }
    }

    @Test
    void should_countTheFailureAndGoOn_when_oneEventCannotBePublished() {
        Instant in30Days = LocalDate.now().plusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Event failing = createEvent(FAILING, 97101, in30Days);
        Event healthy = createEvent(HEALTHY, 97102, in30Days);
        double before = failures();

        publishingScheduledService.autoPublishSpeakers();

        assertThat(failures() - before).isEqualTo(1.0);
        assertThat(eventRepository.findById(failing.getId()).orElseThrow().getCurrentPublishedPhase()).isNull();
        assertThat(eventRepository.findById(healthy.getId()).orElseThrow().getCurrentPublishedPhase())
                .isEqualTo("speakers");
    }

    private double failures() {
        Counter counter = meterRegistry.find(ScheduledJobFailures.METRIC)
                .tag("job", "auto-publish-speakers")
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private Event createEvent(String code, int number, Instant date) {
        return eventRepository.save(Event.builder()
                .eventCode(code)
                .title("Auto-publish Brünnimann " + code)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(120)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .topicCode("cloud-architecture")
                .build());
    }
}
