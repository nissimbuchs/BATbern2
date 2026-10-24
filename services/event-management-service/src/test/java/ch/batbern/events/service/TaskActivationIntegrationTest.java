package ch.batbern.events.service;

import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.TaskTemplateRepository;
import ch.batbern.events.scheduled.PublishingScheduledService;
import ch.batbern.events.service.publishing.PublishingService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task activation, timing and supersession (docs/plans/organizer-cockpit-attention-cards.md,
 * Issue A: T1, T4, T5, T7).
 *
 * <p>Regression anchor: on 2026-10-23 the BATbern60 agenda was auto-published by
 * {@link PublishingScheduledService}, which set {@code AGENDA_PUBLISHED} directly. No
 * {@code EventWorkflowTransitionEvent} fired, so the Catering / Speaker Lineup / Final Agenda
 * tasks stayed {@code pending} although the event had reached their trigger state.
 */
@Transactional
class TaskActivationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublishingScheduledService publishingScheduledService;

    @Autowired
    private PublishingService publishingService;

    @Autowired
    private EventTaskService eventTaskService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private TaskTemplateRepository taskTemplateRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private MockMvc mockMvc;

    private int eventNumberSequence = (int) (System.currentTimeMillis() % 40000) + 100000;

    @BeforeEach
    void setUp() {
        eventNumberSequence += 1;
    }

    // ---------------------------------------------------------------- T1

    @Test
    void should_activateAgendaPublishedTasks_when_agendaIsAutoPublished() {
        Instant eventDate = LocalDate.now().plusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Event event = createEvent("TA-AUTO-AGENDA", eventDate, EventWorkflowState.SLOT_ASSIGNMENT, "speakers");
        createTimedSession(event, eventDate);
        EventTask finalAgenda = createPendingTask(event, "Final Agenda", "agenda_published", eventDate, null);

        publishingScheduledService.autoPublishAgenda();

        assertThat(eventRepository.findById(event.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
        assertThat(reload(finalAgenda).getStatus()).isEqualTo("todo");
    }

    @Test
    void should_notMoveWorkflowBackwards_when_agendaIsPublishedForLiveEvent() {
        Instant eventDate = Instant.now();
        Event event = createEvent("TA-LIVE-REPUB", eventDate, EventWorkflowState.EVENT_LIVE, "agenda");
        createTimedSession(event, eventDate);

        publishingService.publishPhase(event.getEventCode(), "agenda");

        assertThat(eventRepository.findById(event.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.EVENT_LIVE);
    }

    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_activateTasks_when_workflowStateIsChangedViaPatch() throws Exception {
        Instant eventDate = Instant.now().plus(40, ChronoUnit.DAYS);
        Event event = createEvent("TA-PATCH", eventDate, EventWorkflowState.TOPIC_SELECTION, null);
        EventTask slotTask = createPendingTask(event, "Slot work", "slot_assignment", eventDate, null);

        mockMvc.perform(patch("/api/v1/events/" + event.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowState\":\"SLOT_ASSIGNMENT\"}"))
                .andExpect(status().isOk());

        assertThat(eventRepository.findById(event.getId()).orElseThrow().getWorkflowState())
                .isEqualTo(EventWorkflowState.SLOT_ASSIGNMENT);
        assertThat(reload(slotTask).getStatus()).isEqualTo("todo");
    }

    @Test
    void should_createTaskAsTodo_when_eventAlreadyPastTriggerState() {
        Instant eventDate = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = createEvent("TA-LATE-ADD", eventDate, EventWorkflowState.AGENDA_PUBLISHED, "agenda");
        TaskTemplate template = createTemplate("Late template", "agenda_published", -2, 14);

        List<EventTask> created = eventTaskService.createTasksFromTemplatesWithAssignees(
                event.getId(), List.of(new EventTaskService.TemplateAssignmentConfig(template.getId(), null)));

        assertThat(created).singleElement().extracting(EventTask::getStatus).isEqualTo("todo");
    }

    // ---------------------------------------------------------------- T4

    @Test
    void should_activatePendingTask_when_withinLeadDaysOfDueDate() {
        Instant eventDate = Instant.now().plus(20, ChronoUnit.DAYS);
        Event event = createEvent("TA-LEAD", eventDate, EventWorkflowState.SPEAKER_IDENTIFICATION, null);
        EventTask dueIn10 = createPendingTask(event, "Due in 10", "agenda_published",
                Instant.now().plus(10, ChronoUnit.DAYS), 14);
        EventTask dueIn20 = createPendingTask(event, "Due in 20", "agenda_published",
                Instant.now().plus(20, ChronoUnit.DAYS), 14);
        EventTask stateOnly = createPendingTask(event, "State only", "event_completed",
                Instant.now().plus(1, ChronoUnit.DAYS), null);

        eventTaskService.reconcileOpenTasks(Instant.now());

        assertThat(reload(dueIn10).getStatus()).isEqualTo("todo");
        assertThat(reload(dueIn20).getStatus()).isEqualTo("pending");
        assertThat(reload(stateOnly).getStatus()).isEqualTo("pending");
    }

    @Test
    void should_activatePendingTask_when_eventStateAlreadyReachedButNoTransitionFired() {
        Instant eventDate = Instant.now().plus(60, ChronoUnit.DAYS);
        Event event = createEvent("TA-LEVEL", eventDate, EventWorkflowState.AGENDA_PUBLISHED, "agenda");
        EventTask missed = createPendingTask(event, "Missed", "agenda_published", eventDate, null);

        eventTaskService.reconcileOpenTasks(Instant.now());

        assertThat(reload(missed).getStatus()).isEqualTo("todo");
    }

    @Test
    void should_copyActivationLeadDays_when_creatingTaskFromTemplate() {
        Instant eventDate = Instant.now().plus(100, ChronoUnit.DAYS);
        Event event = createEvent("TA-COPY-LEAD", eventDate, EventWorkflowState.CREATED, null);
        TaskTemplate template = createTemplate("Lead 21", "agenda_published", -30, 21);

        EventTask task = eventTaskService.createTasksForEvent(event.getId(), List.of(template)).getFirst();

        assertThat(task.getActivationLeadDays()).isEqualTo(21);
        assertThat(task.getStatus()).isEqualTo("pending");
    }

    // ---------------------------------------------------------------- T5 + T7

    @Test
    void should_activateSpeakersPublishedTasks_and_cancelTopicAnnouncement_when_speakersPublished() {
        Instant eventDate = Instant.now().plus(30, ChronoUnit.DAYS);
        Event event = createEvent("TA-SPEAKERS", eventDate, EventWorkflowState.SLOT_ASSIGNMENT, "topic");
        EventTask lineup = createPendingTaskFromSystemTemplate(event, "newsletter_speakers");
        EventTask topic = createTodoTaskFromSystemTemplate(event, "newsletter_topic");

        publishingService.publishPhase(event.getEventCode(), "speakers");

        assertThat(reload(lineup).getStatus()).isEqualTo("todo");
        EventTask cancelledTopic = reload(topic);
        assertThat(cancelledTopic.getStatus()).isEqualTo("cancelled");
        assertThat(cancelledTopic.getCancelledReason()).contains("superseded");
    }

    @Test
    void should_cancelSpeakerLineup_but_keepCompletedTasks_when_agendaPublished() {
        Instant eventDate = LocalDate.now().plusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Event event = createEvent("TA-AGENDA-SUPERSEDE", eventDate, EventWorkflowState.SLOT_ASSIGNMENT, "speakers");
        createTimedSession(event, eventDate);
        EventTask lineup = createTodoTaskFromSystemTemplate(event, "newsletter_speakers");
        EventTask topic = createTodoTaskFromSystemTemplate(event, "newsletter_topic");
        topic.setStatus("completed");
        eventTaskRepository.save(topic);

        publishingScheduledService.autoPublishAgenda();

        assertThat(reload(lineup).getStatus()).isEqualTo("cancelled");
        assertThat(reload(topic).getStatus()).isEqualTo("completed");
    }

    @Test
    void should_cancelSupersededNewsletter_when_reconcilingAlreadyPublishedEvent() {
        Instant eventDate = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = createEvent("TA-RECONCILE-SUPERSEDE", eventDate, EventWorkflowState.AGENDA_PUBLISHED, "agenda");
        EventTask lineup = createPendingTaskFromSystemTemplate(event, "newsletter_speakers");

        eventTaskService.reconcileOpenTasks(Instant.now());

        assertThat(reload(lineup).getStatus()).isEqualTo("cancelled");
    }

    // ---------------------------------------------------------------- seeded defaults (V125)

    @Test
    void should_haveUpdatedDefaultTemplates_after_migration() {
        TaskTemplate lineup = taskTemplateRepository.findBySystemKey("newsletter_speakers").orElseThrow();
        assertThat(lineup.getTriggerState()).isEqualTo("speakers_published");

        assertThat(taskTemplateRepository.findBySystemKey("newsletter_topic")).isPresent();

        List<TaskTemplate> defaults = taskTemplateRepository.findByIsDefaultTrue();
        assertThat(defaults).filteredOn(t -> t.getName().equals("Catering Coordination"))
                .singleElement().extracting(TaskTemplate::getDueDateOffsetDays).isEqualTo(-5);
        assertThat(defaults).filteredOn(t -> t.getName().equals("Namensschilder"))
                .singleElement().satisfies(t -> {
                    assertThat(t.getTriggerState()).isEqualTo("agenda_published");
                    assertThat(t.getDueDateOffsetDays()).isEqualTo(-2);
                });
        assertThat(defaults).filteredOn(t -> t.getName().equals("Wein"))
                .singleElement().extracting(TaskTemplate::getDueDateOffsetDays).isEqualTo(0);
        assertThat(defaults).filteredOn(t -> t.getName().equals("Newsletter: Slides Are Online"))
                .singleElement().extracting(TaskTemplate::getActivationLeadDays).isNull();
        assertThat(defaults).filteredOn(t -> t.getName().equals("Venue Booking"))
                .singleElement().extracting(TaskTemplate::getActivationLeadDays).isEqualTo(14);
    }

    // ---------------------------------------------------------------- helpers

    private Event createEvent(String code, Instant date, EventWorkflowState state, String phase) {
        int number = eventNumberSequence++;
        Event event = Event.builder()
                .eventCode("BATbern" + number) // the API validates ^BATbern[0-9]+$
                .title("Task activation " + code)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.FULL_DAY)
                .workflowState(state)
                .topicCode("cloud-architecture")
                .currentPublishedPhase(phase)
                .lastPublishedAt(phase != null ? Instant.now().minus(1, ChronoUnit.DAYS) : null)
                .build();
        return eventRepository.save(event);
    }

    private void createTimedSession(Event event, Instant eventDate) {
        sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug("ta-session-" + UUID.randomUUID().toString().substring(0, 6))
                .title("Timed Session")
                .sessionType("presentation")
                .startTime(eventDate.plus(2, ChronoUnit.HOURS))
                .endTime(eventDate.plus(3, ChronoUnit.HOURS))
                .build());
    }

    private TaskTemplate createTemplate(String name, String trigger, int offset, Integer leadDays) {
        TaskTemplate template = new TaskTemplate();
        template.setName(name);
        template.setTriggerState(trigger);
        template.setDueDateType("relative_to_event");
        template.setDueDateOffsetDays(offset);
        template.setActivationLeadDays(leadDays);
        template.setIsDefault(false);
        template.setCreatedByUsername("test.organizer");
        return taskTemplateRepository.save(template);
    }

    private EventTask createPendingTask(Event event, String name, String trigger, Instant due, Integer leadDays) {
        EventTask task = new EventTask();
        task.setEventId(event.getId());
        task.setTaskName(name);
        task.setTriggerState(trigger);
        task.setDueDate(due);
        task.setActivationLeadDays(leadDays);
        task.setStatus("pending");
        return eventTaskRepository.save(task);
    }

    private EventTask createPendingTaskFromSystemTemplate(Event event, String systemKey) {
        TaskTemplate template = taskTemplateRepository.findBySystemKey(systemKey).orElseThrow();
        EventTask task = createPendingTask(event, template.getName(), template.getTriggerState(),
                event.getDate().plus(template.getDueDateOffsetDays(), ChronoUnit.DAYS), null);
        task.setTemplateId(template.getId());
        return eventTaskRepository.save(task);
    }

    private EventTask createTodoTaskFromSystemTemplate(Event event, String systemKey) {
        EventTask task = createPendingTaskFromSystemTemplate(event, systemKey);
        task.setStatus("todo");
        return eventTaskRepository.save(task);
    }

    private EventTask reload(EventTask task) {
        return eventTaskRepository.findById(task.getId()).orElseThrow();
    }
}
