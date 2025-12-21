package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for EventTaskController (Story 5.5 Phase 5).
 *
 * Tests REST API endpoints for event task management (AC21-27).
 *
 * Endpoints tested:
 * - GET  /api/v1/events/{eventCode}/tasks - List tasks for event
 * - POST /api/v1/events/{eventCode}/tasks - Create ad-hoc task
 * - GET  /api/v1/tasks/my-tasks - Get tasks assigned to current organizer
 * - PUT  /api/v1/tasks/{taskId}/complete - Mark task complete
 * - PUT  /api/v1/tasks/{taskId}/reassign - Reassign task to different organizer
 */
@Transactional
class EventTaskControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        eventTaskRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        testEvent = new Event();
        testEvent.setEventCode("TEST-001");
        testEvent.setEventNumber(1);
        testEvent.setDate(Instant.now().plus(90, ChronoUnit.DAYS));
        testEvent.setTitle("Test Event");
        testEvent.setRegistrationDeadline(Instant.now().plus(60, ChronoUnit.DAYS));
        testEvent.setVenueName("Test Venue");
        testEvent.setVenueAddress("Test Address");
        testEvent.setVenueCapacity(100);
        testEvent.setOrganizerUsername("alice.organizer");
        testEvent.setEventType(EventType.FULL_DAY);
        testEvent.setWorkflowState(EventWorkflowState.TOPIC_SELECTION);
        testEvent = eventRepository.save(testEvent);
    }

    /**
     * Test: GET /api/v1/events/{eventCode}/tasks returns all tasks for event (AC24)
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_listEventTasks_when_requestedByOrganizer() throws Exception {
        // Given: Event has tasks
        EventTask task1 = createTask(testEvent.getId(), "Task 1", "alice.organizer", "todo");
        EventTask task2 = createTask(testEvent.getId(), "Task 2", "bob.organizer", "completed");

        // When: GET /api/v1/events/{eventCode}/tasks
        mockMvc.perform(get("/api/v1/events/" + testEvent.getEventCode() + "/tasks"))
                // Then: Returns 200 OK with all tasks
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].taskName", containsInAnyOrder("Task 1", "Task 2")))
                .andExpect(jsonPath("$[*].status", containsInAnyOrder("todo", "completed")));
    }

    /**
     * Test: POST /api/v1/events/{eventCode}/tasks creates ad-hoc task (AC22)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_createAdHocTask_when_validRequestProvided() throws Exception {
        // Given: Valid ad-hoc task request
        Instant dueDate = Instant.now().plus(14, ChronoUnit.DAYS);
        String requestBody = String.format("""
                {
                    "taskName": "Custom: Special Budget Review",
                    "triggerState": "topic_selection",
                    "dueDate": "%s",
                    "assignedOrganizerUsername": "bob.organizer",
                    "notes": "Need CFO approval"
                }
                """, dueDate.toString());

        // When: POST /api/v1/events/{eventCode}/tasks
        mockMvc.perform(post("/api/v1/events/" + testEvent.getEventCode() + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 201 Created
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskName").value("Custom: Special Budget Review"))
                .andExpect(jsonPath("$.triggerState").value("topic_selection"))
                .andExpect(jsonPath("$.assignedOrganizerUsername").value("bob.organizer"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.notes").value("Need CFO approval"))
                .andExpect(jsonPath("$.templateId").value(nullValue()));
    }

    /**
     * Test: GET /api/v1/tasks/my-tasks returns tasks assigned to current organizer (AC24)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_returnMyTasks_when_requestedByOrganizer() throws Exception {
        // Given: Multiple tasks with different assignees
        createTask(testEvent.getId(), "Alice Task 1", "alice.organizer", "todo");
        createTask(testEvent.getId(), "Alice Task 2", "alice.organizer", "todo");
        createTask(testEvent.getId(), "Bob Task", "bob.organizer", "todo");

        // When: GET /api/v1/tasks/my-tasks
        mockMvc.perform(get("/api/v1/tasks/my-tasks"))
                // Then: Returns only tasks assigned to alice.organizer
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].taskName", containsInAnyOrder("Alice Task 1", "Alice Task 2")))
                .andExpect(jsonPath("$[*].assignedOrganizerUsername", everyItem(is("alice.organizer"))));
    }

    /**
     * Test: PUT /api/v1/tasks/{taskId}/complete marks task as complete (AC25)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_completeTask_when_requestedByOrganizer() throws Exception {
        // Given: Active task
        EventTask task = createTask(testEvent.getId(), "Task to Complete", "alice.organizer", "todo");

        // And: Completion request
        String requestBody = """
                {
                    "notes": "Budget approved by CFO"
                }
                """;

        // When: PUT /api/v1/tasks/{taskId}/complete
        mockMvc.perform(put("/api/v1/tasks/" + task.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 200 OK
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.completedByUsername").value("alice.organizer"))
                .andExpect(jsonPath("$.completedDate").exists())
                .andExpect(jsonPath("$.notes").value("Budget approved by CFO"));
    }

    /**
     * Test: PUT /api/v1/tasks/{taskId}/reassign reassigns task to different organizer (AC27)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_reassignTask_when_requestedByOrganizer() throws Exception {
        // Given: Task assigned to alice
        EventTask task = createTask(testEvent.getId(), "Task to Reassign", "alice.organizer", "todo");

        // And: Reassignment request
        String requestBody = """
                {
                    "newOrganizerUsername": "bob.organizer"
                }
                """;

        // When: PUT /api/v1/tasks/{taskId}/reassign
        mockMvc.perform(put("/api/v1/tasks/" + task.getId() + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 200 OK
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOrganizerUsername").value("bob.organizer"));
    }

    /**
     * Test: GET /api/v1/tasks/my-tasks returns critical tasks (overdue + due soon)
     */
    @Test
    @WithMockUser(username = "alice.organizer", roles = "ORGANIZER")
    void should_returnCriticalTasks_when_queryingMyTasks() throws Exception {
        // Given: Tasks with different due dates
        EventTask overdue = createTask(testEvent.getId(), "Overdue Task", "alice.organizer", "todo");
        overdue.setDueDate(Instant.now().minus(2, ChronoUnit.DAYS));
        eventTaskRepository.save(overdue);

        EventTask dueSoon = createTask(testEvent.getId(), "Due Soon Task", "alice.organizer", "todo");
        dueSoon.setDueDate(Instant.now().plus(2, ChronoUnit.DAYS));
        eventTaskRepository.save(dueSoon);

        EventTask farFuture = createTask(testEvent.getId(), "Future Task", "alice.organizer", "todo");
        farFuture.setDueDate(Instant.now().plus(30, ChronoUnit.DAYS));
        eventTaskRepository.save(farFuture);

        // When: GET /api/v1/tasks/my-tasks?critical=true
        mockMvc.perform(get("/api/v1/tasks/my-tasks?critical=true"))
                // Then: Returns only overdue and due soon tasks
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].taskName", containsInAnyOrder("Overdue Task", "Due Soon Task")));
    }

    /**
     * Test: POST validates required fields
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_createTaskWithMissingName() throws Exception {
        // Given: Request missing taskName
        String requestBody = """
                {
                    "triggerState": "topic_selection"
                }
                """;

        // When: POST /api/v1/events/{eventCode}/tasks
        mockMvc.perform(post("/api/v1/events/" + testEvent.getEventCode() + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Returns 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    // === Helper Methods ===

    private EventTask createTask(UUID eventId, String taskName, String assignedTo, String status) {
        EventTask task = new EventTask();
        task.setEventId(eventId);
        task.setTaskName(taskName);
        task.setTriggerState("topic_selection");
        task.setDueDate(Instant.now().plus(30, ChronoUnit.DAYS));
        task.setAssignedOrganizerUsername(assignedTo);
        task.setStatus(status);
        return eventTaskRepository.save(task);
    }
}
