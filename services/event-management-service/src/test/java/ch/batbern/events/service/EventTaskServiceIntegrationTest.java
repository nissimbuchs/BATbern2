/**
 * Integration tests for EventTaskService (Story 5.5 Phase 5)
 *
 * Tests cover:
 * - Task auto-creation at event creation with status="pending" (AC21, AC23)
 * - Task activation (pending → todo) when event reaches trigger state
 * - Task idempotency - no duplicate tasks from same template (AC36)
 * - Due date calculation (immediate, relative_to_event, absolute)
 * - Task completion tracking (AC25)
 * - Task assignment and filtering (AC24, AC27)
 *
 * All tests use PostgreSQL via Testcontainers (not H2) for production parity.
 */
package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.domain.TaskTemplate;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.repository.TaskTemplateRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class EventTaskServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventTaskService eventTaskService;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private TaskTemplateRepository taskTemplateRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventWorkflowStateMachine eventWorkflowStateMachine;

    private static final String TEST_ORGANIZER = "test.organizer";
    private static final String TEST_EVENT_CODE = "BAT2025Q1";

    @BeforeEach
    void setUp() {
        // Clean up test data
        eventTaskRepository.deleteAll();
        eventRepository.deleteAll(eventRepository.findAll().stream()
                .filter(e -> e.getEventCode().startsWith("TEST"))
                .toList());
    }

    /**
     * AC21, AC23: Tasks created at event creation with status="pending"
     */
    @Test
    void should_createPendingTasks_when_eventIsCreated() {
        // Given: Event with date in future
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));

        // And: 3 templates for "topic_selection" trigger state
        List<TaskTemplate> templates = taskTemplateRepository.findByTriggerState("topic_selection");
        assertThat(templates).hasSizeGreaterThanOrEqualTo(3); // Venue, Partner Meeting, Moderator, Newsletter:Topic

        // When: Event tasks are created at event creation
        eventTaskService.createTasksForEvent(event.getId(), templates);

        // Then: Tasks created with status="pending"
        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(tasks).allMatch(task -> "pending".equals(task.getStatus()));

        // And: Due dates calculated correctly
        EventTask venueTask = tasks.stream()
                .filter(t -> t.getTaskName().contains("Venue"))
                .findFirst().orElseThrow();
        assertThat(venueTask.getDueDate()).isCloseTo(
                event.getDate().minus(90, ChronoUnit.DAYS),
                within(1, ChronoUnit.SECONDS)
        );
    }

    /**
     * AC23: Tasks transition to "todo" when event reaches trigger state
     */
    @Test
    void should_activatePendingTasks_when_eventReachesTriggerState() {
        // Given: Event in CREATED state with pending tasks
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));
        List<TaskTemplate> templates = taskTemplateRepository.findByTriggerState("topic_selection");
        eventTaskService.createTasksForEvent(event.getId(), templates);

        List<EventTask> pendingTasks = eventTaskRepository.findByEventId(event.getId());
        assertThat(pendingTasks).allMatch(task -> "pending".equals(task.getStatus()));

        // When: Event transitions to TOPIC_SELECTION
        eventWorkflowStateMachine.transitionToState(event.getId().toString(), EventWorkflowState.TOPIC_SELECTION, TEST_ORGANIZER);

        // Then: Tasks activated to status="todo"
        List<EventTask> activatedTasks = eventTaskRepository.findByEventId(event.getId());
        assertThat(activatedTasks).allMatch(task -> "todo".equals(task.getStatus()));
    }

    /**
     * AC36: Idempotency - tasks created only once per template per event
     */
    @Test
    void should_preventDuplicateTasks_when_eventTransitionReplayedMultipleTimes() {
        // Given: Event with tasks already created
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));
        List<TaskTemplate> templates = taskTemplateRepository.findByTriggerState("topic_selection");
        eventTaskService.createTasksForEvent(event.getId(), templates);

        int initialTaskCount = eventTaskRepository.findByEventId(event.getId()).size();

        // When: Event transition event is replayed/retried multiple times
        eventTaskService.createTasksForEvent(event.getId(), templates); // Duplicate call
        eventTaskService.createTasksForEvent(event.getId(), templates); // Another duplicate

        // Then: No duplicate tasks created (same count)
        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasks).hasSize(initialTaskCount);

        // And: Each template has exactly one task
        for (TaskTemplate template : templates) {
            long taskCount = tasks.stream()
                    .filter(task -> task.getTemplateId().equals(template.getId()))
                    .count();
            assertThat(taskCount).isEqualTo(1);
        }
    }

    /**
     * Due date calculation: immediate type
     */
    @Test
    void should_calculateDueDateAsNow_when_dueDateTypeIsImmediate() {
        // Given: Template with immediate due date
        TaskTemplate template = createCustomTemplate("Urgent Task", "topic_selection", "immediate", null);

        // When: Task created from template
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));
        eventTaskService.createTasksForEvent(event.getId(), List.of(template));

        // Then: Due date is approximately now (within 1 minute tolerance)
        EventTask task = eventTaskRepository.findByEventId(event.getId()).get(0);
        assertThat(task.getDueDate()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    }

    /**
     * Due date calculation: relative_to_event type (before event)
     */
    @Test
    void should_calculateDueDateBeforeEvent_when_offsetIsNegative() {
        // Given: Template with -30 days offset (30 days before event)
        TaskTemplate template = createCustomTemplate("Pre-Event Task", "topic_selection", "relative_to_event", -30);

        // When: Task created from template
        Instant eventDate = Instant.now().plus(90, ChronoUnit.DAYS);
        Event event = createEvent(eventDate);
        eventTaskService.createTasksForEvent(event.getId(), List.of(template));

        // Then: Due date is 30 days before event
        EventTask task = eventTaskRepository.findByEventId(event.getId()).get(0);
        assertThat(task.getDueDate()).isCloseTo(eventDate.minus(30, ChronoUnit.DAYS), within(1, ChronoUnit.SECONDS));
    }

    /**
     * Due date calculation: relative_to_event type (after event)
     */
    @Test
    void should_calculateDueDateAfterEvent_when_offsetIsPositive() {
        // Given: Template with +7 days offset (7 days after event)
        TaskTemplate template = createCustomTemplate("Post-Event Task", "topic_selection", "relative_to_event", 7);

        // When: Task created from template
        Instant eventDate = Instant.now().plus(90, ChronoUnit.DAYS);
        Event event = createEvent(eventDate);
        eventTaskService.createTasksForEvent(event.getId(), List.of(template));

        // Then: Due date is 7 days after event
        EventTask task = eventTaskRepository.findByEventId(event.getId()).get(0);
        assertThat(task.getDueDate()).isCloseTo(eventDate.plus(7, ChronoUnit.DAYS), within(1, ChronoUnit.SECONDS));
    }

    /**
     * AC25: Task completion tracking
     */
    @Test
    void should_recordCompletionDetails_when_taskIsCompleted() {
        // Given: Active task (status="todo")
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));
        TaskTemplate template = createCustomTemplate("Test Task", "topic_selection", "immediate", null);
        eventTaskService.createTasksForEvent(event.getId(), List.of(template));

        EventTask task = eventTaskRepository.findByEventId(event.getId()).get(0);
        task.setStatus("todo"); // Activate task
        eventTaskRepository.save(task);

        // When: Task is marked complete
        String completionNotes = "Task completed successfully. Venue booked for 200 attendees.";
        eventTaskService.completeTask(task.getId(), TEST_ORGANIZER, completionNotes);

        // Then: Task status updated to "completed"
        EventTask completedTask = eventTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo("completed");

        // And: Completion details recorded
        assertThat(completedTask.getCompletedByUsername()).isEqualTo(TEST_ORGANIZER);
        assertThat(completedTask.getCompletedDate()).isNotNull();
        assertThat(completedTask.getCompletedDate()).isCloseTo(
                Instant.now(),
                within(1, ChronoUnit.MINUTES)
        );
        assertThat(completedTask.getNotes()).isEqualTo(completionNotes);
    }

    /**
     * AC24: Filter tasks by assigned organizer
     */
    @Test
    void should_returnAssignedTasks_when_filteringByOrganizerUsername() {
        // Given: Event with tasks assigned to different organizers
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));

        TaskTemplate template1 = createCustomTemplate("Task for Organizer A", "topic_selection", "immediate", null);
        TaskTemplate template2 = createCustomTemplate("Task for Organizer B", "topic_selection", "immediate", null);

        eventTaskService.createTasksForEvent(event.getId(), List.of(template1, template2));

        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        tasks.get(0).setAssignedOrganizerUsername("organizer.a");
        tasks.get(1).setAssignedOrganizerUsername("organizer.b");
        eventTaskRepository.saveAll(tasks);

        // When: Filtering tasks by organizer A
        List<EventTask> organizerATasks = eventTaskService.getTasksForOrganizer("organizer.a");

        // Then: Only organizer A's tasks returned
        assertThat(organizerATasks).hasSize(1);
        assertThat(organizerATasks.get(0).getTaskName()).isEqualTo("Task for Organizer A");
    }

    /**
     * AC24: Get overdue and due soon tasks
     */
    @Test
    void should_returnOverdueAndDueSoonTasks_when_gettingCriticalTasks() {
        // Given: Event with tasks having different due dates
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));

        // Overdue task (due 2 days ago)
        TaskTemplate overdueTemplate = createCustomTemplate("Overdue Task", "topic_selection", "relative_to_event", -92);

        // Due soon task (due tomorrow)
        TaskTemplate dueSoonTemplate = createCustomTemplate("Due Soon Task", "topic_selection", "relative_to_event", -89);

        // Not critical task (due in 10 days)
        TaskTemplate futureTemplate = createCustomTemplate("Future Task", "topic_selection", "relative_to_event", -80);

        eventTaskService.createTasksForEvent(event.getId(), List.of(overdueTemplate, dueSoonTemplate, futureTemplate));

        // Activate all tasks
        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        tasks.forEach(task -> {
            task.setStatus("todo");
            task.setAssignedOrganizerUsername(TEST_ORGANIZER);
        });
        eventTaskRepository.saveAll(tasks);

        // When: Getting critical tasks (overdue + due soon < 3 days)
        List<EventTask> criticalTasks = eventTaskService.getCriticalTasksForOrganizer(TEST_ORGANIZER);

        // Then: Only overdue and due soon tasks returned (not the future task)
        assertThat(criticalTasks).hasSize(2);
        assertThat(criticalTasks).extracting("taskName")
                .containsExactlyInAnyOrder("Overdue Task", "Due Soon Task");
    }

    /**
     * AC27: Task assignment flexibility - can be unassigned
     */
    @Test
    void should_allowUnassignedTasks_when_noOrganizerSpecified() {
        // Given: Template without default assignee
        TaskTemplate template = createCustomTemplate("Unassigned Task", "topic_selection", "immediate", null);

        // When: Task created without assigned organizer
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));
        eventTaskService.createTasksForEvent(event.getId(), List.of(template));

        // Then: Task created with null assignee
        EventTask task = eventTaskRepository.findByEventId(event.getId()).get(0);
        assertThat(task.getAssignedOrganizerUsername()).isNull();
    }

    /**
     * AC27: Task assignment can be changed later
     */
    @Test
    void should_allowReassignment_when_taskAssigneeIsUpdated() {
        // Given: Task assigned to organizer A
        Event event = createEvent(Instant.now().plus(90, ChronoUnit.DAYS));
        TaskTemplate template = createCustomTemplate("Reassignable Task", "topic_selection", "immediate", null);
        eventTaskService.createTasksForEvent(event.getId(), List.of(template));

        EventTask task = eventTaskRepository.findByEventId(event.getId()).get(0);
        task.setAssignedOrganizerUsername("organizer.a");
        eventTaskRepository.save(task);

        // When: Task reassigned to organizer B
        eventTaskService.reassignTask(task.getId(), "organizer.b");

        // Then: Task assignee updated
        EventTask reassignedTask = eventTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(reassignedTask.getAssignedOrganizerUsername()).isEqualTo("organizer.b");
    }

    /**
     * Validation: Cannot complete task that doesn't exist
     */
    @Test
    void should_throwException_when_completingNonExistentTask() {
        // When/Then: Attempting to complete non-existent task throws exception
        UUID nonExistentId = UUID.randomUUID();
        assertThatThrownBy(() ->
                eventTaskService.completeTask(nonExistentId, TEST_ORGANIZER, "Notes")
        ).isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
         .hasMessageContaining("Task not found");
    }

    /**
     * Validation: Cannot reassign task that doesn't exist
     */
    @Test
    void should_throwException_when_reassigningNonExistentTask() {
        // When/Then: Attempting to reassign non-existent task throws exception
        UUID nonExistentId = UUID.randomUUID();
        assertThatThrownBy(() ->
                eventTaskService.reassignTask(nonExistentId, "new.organizer")
        ).isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
         .hasMessageContaining("Task not found");
    }

    // === Helper Methods ===

    private Event createEvent(Instant eventDate) {
        Event event = new Event();
        event.setEventCode(TEST_EVENT_CODE + "-" + UUID.randomUUID().toString().substring(0, 8));
        event.setEventNumber(123);
        event.setTitle("Test Event");
        event.setDate(eventDate);
        event.setRegistrationDeadline(eventDate.minus(7, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(200);
        event.setOrganizerUsername(TEST_ORGANIZER);
        event.setWorkflowState(EventWorkflowState.CREATED);
        event.setEventType(EventType.FULL_DAY);
        return eventRepository.save(event);
    }

    private TaskTemplate createCustomTemplate(String name, String triggerState, String dueDateType, Integer offsetDays) {
        TaskTemplate template = new TaskTemplate();
        template.setName(name);
        template.setTriggerState(triggerState);
        template.setDueDateType(dueDateType);
        template.setDueDateOffsetDays(offsetDays);
        template.setIsDefault(false);
        template.setCreatedByUsername(TEST_ORGANIZER);
        return taskTemplateRepository.save(template);
    }
}
