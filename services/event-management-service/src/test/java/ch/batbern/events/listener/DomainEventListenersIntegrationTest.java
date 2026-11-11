package ch.batbern.events.listener;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.notification.Notification;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionQnaWindowRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.TaskTemplateRepository;
import ch.batbern.events.service.EventTaskService;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.2 part B: every in-process listener still receives its domain event through the real
 * {@code SpringDomainEventPublisher} (no event bus, no mocked publisher), shown by its effect in the
 * database.
 *
 * <p>Not {@code @Transactional}: {@link AutoEnrollmentListener} runs in {@code REQUIRES_NEW} and
 * {@code OrganizerNotificationListener} on an async thread, so both must see committed rows. Every
 * test cleans up what it created.
 *
 * <p>CUMS is the S2S contract harness on the wire (Story 17.0b2): the real {@code UserApiClient}
 * asks {@code GET /internal/users?role=...}, and {@link CumsContractHarness} answers the
 * stakeholder lists, checked against the CUMS internal spec. The publisher and every listener run
 * for real.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class DomainEventListenersIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "listenerit.organizer";
    private static final String PARTNER = "listenerit.partner";
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(2);

    @Autowired
    private MockMvc mockMvc;

    /** CUMS on the wire (see the class comment). */
    @Autowired
    private CumsContractHarness cums;

    @Autowired
    private EventTaskService eventTaskService;

    @Autowired
    private TaskTemplateRepository taskTemplateRepository;

    @Autowired
    private EventWorkflowStateMachine eventWorkflowStateMachine;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionQnaWindowRepository windowRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private String eventCode;

    @BeforeEach
    void setUp() {
        cums.usersWithRole("ORGANIZER", ORGANIZER);
        cums.usersWithRole("PARTNER", PARTNER);
    }

    @AfterEach
    void tearDown() {
        try {
            // First back to the defaults, so other tests sharing this context see no
            // stakeholders, and a late async listener run finds no organizer to notify.
            cums.reset();
        } finally {
            if (eventCode != null) {
                // Every published domain event also reaches the @Async OrganizerNotificationListener;
                // let it go quiet for this event before deleting, so no late row outlives the test.
                awaitNotificationsQuiet(eventCode);
                deleteTestData(eventCode);
            }
        }
    }

    private void deleteTestData(String code) {
        notificationRepository.deleteAll(organizerNotifications(code));
        eventRepository.findByEventCode(code).ifPresent(event -> {
            windowRepository.deleteAll(windowRepository.findByEventCode(code));
            sessionRepository.deleteAll(sessionRepository.findByEventId(event.getId()));
            eventTaskRepository.deleteAll(eventTaskRepository.findByEventId(event.getId()));
            registrationRepository.deleteAll(registrationRepository.findByEventId(event.getId()));
            eventRepository.delete(event);
        });
    }

    /**
     * Waits until the notification count for {@code code} has not changed for {@link #QUIET_PERIOD}
     * (bounded by {@link #ASYNC_TIMEOUT}). Cleanup only: it never fails the test.
     */
    private void awaitNotificationsQuiet(String code) {
        Instant deadline = Instant.now().plus(ASYNC_TIMEOUT);
        int last = organizerNotifications(code).size();
        Instant stableSince = Instant.now();
        while (Instant.now().isBefore(deadline)
                && Duration.between(stableSince, Instant.now()).compareTo(QUIET_PERIOD) < 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int now = organizerNotifications(code).size();
            if (now != last) {
                last = now;
                stableSince = Instant.now();
            }
        }
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    @DisplayName("AutoEnrollmentListener: POST /events enrols organizers and partners as confirmed participants")
    void should_enrolStakeholders_when_eventCreatedViaApi() throws Exception {
        eventCode = "BATbern9171";

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Listener IT event",
                                    "eventNumber": 9171,
                                    "date": "2030-08-15T09:00:00Z",
                                    "registrationDeadline": "2030-08-08T23:59:59Z",
                                    "venueName": "Test Venue",
                                    "venueAddress": "Test Address",
                                    "venueCapacity": 100,
                                    "organizerUsername": "listenerit.organizer",
                                    "eventType": "EVENING"
                                }
                                """))
                .andExpect(status().isCreated());

        Event event = eventRepository.findByEventCode(eventCode).orElseThrow();
        assertThat(registrationRepository.findByEventId(event.getId()))
                .extracting(Registration::getAttendeeUsername, Registration::getStatus)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(ORGANIZER, "confirmed"),
                        Tuple.tuple(PARTNER, "confirmed"));
    }

    @Test
    @DisplayName("EventWorkflowTransitionListener: a workflow transition activates the pending tasks of the new state")
    void should_activatePendingTasks_when_workflowTransitions() {
        Event event = saveEvent("BATbern9172", EventWorkflowState.CREATED, Instant.now().plus(90, ChronoUnit.DAYS));
        List<TaskTemplate> templates = taskTemplateRepository.findByTriggerState("topic_selection");
        eventTaskService.createTasksForEvent(event.getId(), templates);
        assertThat(eventTaskRepository.findByEventId(event.getId()))
                .isNotEmpty()
                .allSatisfy(task -> assertThat(task.getStatus()).isEqualTo("pending")); // control

        eventWorkflowStateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, ORGANIZER);

        assertThat(eventTaskRepository.findByEventId(event.getId()))
                .isNotEmpty()
                .allSatisfy(task -> assertThat(task.getStatus()).isEqualTo("todo"));
    }

    @Test
    @DisplayName("SessionQnaWindowListener: the transition to EVENT_COMPLETED opens a Q&A window per session")
    void should_openQnaWindows_when_eventCompleted() {
        Event event = saveEvent("BATbern9173", EventWorkflowState.EVENT_LIVE, Instant.now().minus(1, ChronoUnit.DAYS));
        Session session = saveSession(event, "listener-it-session");
        assertThat(windowRepository.findByEventCode(eventCode)).isEmpty(); // control

        eventWorkflowStateMachine.transitionToState(
                eventCode, EventWorkflowState.EVENT_COMPLETED, ORGANIZER, true, "listener IT");

        assertThat(windowRepository.findByEventCode(eventCode))
                .singleElement()
                .satisfies(window -> {
                    assertThat(window.getSessionId()).isEqualTo(session.getId());
                    assertThat(window.getStatus()).isEqualTo(QnaWindowStatus.OPEN);
                });
    }

    @Test
    @DisplayName("OrganizerNotificationListener: a domain event creates an in-app notification after the async hand-off")
    void should_notifyOrganizers_when_domainEventPublished() {
        saveEvent("BATbern9174", EventWorkflowState.CREATED, Instant.now().plus(90, ChronoUnit.DAYS));
        assertThat(organizerNotifications(eventCode)).isEmpty(); // control

        eventWorkflowStateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, ORGANIZER);

        awaitTrue(() -> !organizerNotifications(eventCode).isEmpty());
        assertThat(organizerNotifications(eventCode))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getNotificationType()).isEqualTo("EventWorkflowTransition");
                    assertThat(n.getChannel()).isEqualTo("IN_APP");
                    assertThat(n.getStatus()).isEqualTo("UNREAD");
                });
    }

    // ------ helpers ------

    private List<Notification> organizerNotifications(String code) {
        return notificationRepository.findByRecipientUsername(ORGANIZER, Pageable.unpaged())
                .stream()
                .filter(n -> "IN_APP".equals(n.getChannel()) && code.equals(n.getEventCode()))
                .toList();
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(ASYNC_TIMEOUT);
        while (!condition.get()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within " + ASYNC_TIMEOUT);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", e);
            }
        }
    }

    private Event saveEvent(String code, EventWorkflowState state, Instant date) {
        eventCode = code;
        Event event = new Event();
        event.setEventCode(code);
        event.setEventNumber(Integer.parseInt(code.replaceAll("\\D", "")));
        event.setTitle("Listener IT event");
        event.setDate(date);
        event.setRegistrationDeadline(date.minus(7, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(100);
        event.setOrganizerUsername(ORGANIZER);
        event.setEventType(EventType.EVENING);
        event.setWorkflowState(state);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy(ORGANIZER);
        event.setUpdatedBy(ORGANIZER);
        return eventRepository.save(event);
    }

    private Session saveSession(Event event, String slug) {
        return sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug(slug)
                .title("Session " + slug)
                .sessionType("presentation")
                .startTime(event.getDate())
                .endTime(event.getDate().plus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }
}
