package ch.batbern.events.service;

import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.publishing.PublishingService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1108: the public agenda shows only stored sessions with a start time, while the organizer's
 * slot view computes moderation, breaks and apéro from the event-type config. Until someone pressed
 * "Generate structure", the published agenda (BATbern60) listed the talks only. Publishing the
 * agenda now generates the structure when none is stored, and never overwrites it.
 */
@Transactional
class AgendaStructuralSessionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublishingService publishingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private int eventNumber = (int) (System.currentTimeMillis() % 40000) + 150000;

    @Test
    void should_generateStructuralSessions_when_agendaPublishedWithoutThem() {
        Event event = createEvent();
        createTalk(event);

        publishingService.publishPhase(event.getEventCode(), "agenda");

        List<Session> structural = sessionRepository.findByEventIdAndSessionTypeIn(
                event.getId(), StructuralSessionService.GENERATED_TYPES);
        assertThat(structural).isNotEmpty();
        assertThat(structural).allSatisfy(s -> assertThat(s.getStartTime()).isNotNull());
    }

    @Test
    void should_keepExistingStructure_when_agendaPublishedAgain() {
        Event event = createEvent();
        createTalk(event);
        Session edited = sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug("edited-break-" + UUID.randomUUID().toString().substring(0, 6))
                .title("Kaffeepause (verschoben)")
                .sessionType("break")
                .startTime(event.getDate().plus(4, ChronoUnit.HOURS))
                .endTime(event.getDate().plus(5, ChronoUnit.HOURS))
                .build());

        publishingService.publishPhase(event.getEventCode(), "agenda");

        List<Session> structural = sessionRepository.findByEventIdAndSessionTypeIn(
                event.getId(), StructuralSessionService.GENERATED_TYPES);
        assertThat(structural).extracting(Session::getId).containsExactly(edited.getId());
    }

    private Event createEvent() {
        int number = eventNumber++;
        Instant date = LocalDate.now().plusDays(14).atStartOfDay(ZoneId.of("Europe/Zurich")).toInstant();
        return eventRepository.save(Event.builder()
                .eventCode("BATbern" + number)
                .title("Structure " + number)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .topicCode("cloud-architecture")
                .currentPublishedPhase("speakers")
                .lastPublishedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());
    }

    private void createTalk(Event event) {
        sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug("talk-" + UUID.randomUUID().toString().substring(0, 6))
                .title("Talk")
                .sessionType("presentation")
                .startTime(event.getDate().plus(16, ChronoUnit.HOURS))
                .endTime(event.getDate().plus(17, ChronoUnit.HOURS))
                .build());
    }
}
