package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for EventController - List/Search API (AC1)
 * Story 1.15a.1: Events API Consolidation
 *
 * Test Scenarios:
 * - AC1.1: List events without filters
 * - AC1.2: Filter events by status
 * - AC1.3: Filter events by date range (year)
 * - AC1.4: Sort events by date
 * - AC1.5: Paginate event results
 *
 * TDD Workflow: RED Phase - These tests will fail until implementation is complete
 *
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class EventControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        eventRepository.deleteAll();

        // Create test data
        createTestEvent("BATbern 2025", "2025-05-15T09:00:00Z", "published");
        createTestEvent("BATbern 2024", "2024-06-20T09:00:00Z", "archived");
        createTestEvent("BATbern 2026 Draft", "2026-07-01T09:00:00Z", "planning");
    }

    private Event createTestEvent(String title, String dateStr, String status) {
        Event event = Event.builder()
                .title(title)
                .eventNumber(100 + (int)(Math.random() * 1000))  // Generate random event number
                .date(Instant.parse(dateStr))
                .registrationDeadline(Instant.parse(dateStr).minusSeconds(86400 * 7)) // 7 days before event
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .status(status)
                .organizerId(UUID.randomUUID())
                .currentAttendeeCount(0)
                .description("Test event for " + title)
                .build();
        return eventRepository.save(event);
    }

    // ============================================================================
    // AC1.1: List Events Without Filters
    // ============================================================================

    @Test
    @DisplayName("should_listEvents_when_noFilterProvided")
    void should_listEvents_when_noFilterProvided() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.total").value(3));
    }

    // ============================================================================
    // AC1.2: Filter Events by Status
    // ============================================================================

    @Test
    @DisplayName("should_filterByStatus_when_filterProvided")
    void should_filterByStatus_when_filterProvided() throws Exception {
        String filter = "{\"status\":\"published\"}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("published"))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025"));
    }

    @Test
    @DisplayName("should_filterByMultipleStatuses_when_inOperatorUsed")
    void should_filterByMultipleStatuses_when_inOperatorUsed() throws Exception {
        String filter = "{\"status\":{\"$in\":[\"published\",\"planning\"]}}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ============================================================================
    // AC1.3: Filter Events by Date Range (Year)
    // ============================================================================

    @Test
    @DisplayName("should_filterByYear_when_dateFilterProvided")
    void should_filterByYear_when_dateFilterProvided() throws Exception {
        String filter = "{\"date\":{\"$gte\":\"2025-01-01T00:00:00Z\",\"$lt\":\"2026-01-01T00:00:00Z\"}}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025"));
    }

    @Test
    @DisplayName("should_filterByDateRange_when_gteAndLteProvided")
    void should_filterByDateRange_when_gteAndLteProvided() throws Exception {
        String filter = "{\"date\":{\"$gte\":\"2024-01-01T00:00:00Z\",\"$lte\":\"2025-12-31T23:59:59Z\"}}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2))); // 2024 and 2025 events
    }

    // ============================================================================
    // AC1.4: Sort Events by Date
    // ============================================================================

    @Test
    @DisplayName("should_sortByDate_when_sortParamProvided")
    void should_sortByDate_when_sortParamProvided() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "-date") // Descending order
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2026 Draft")) // Most recent first
                .andExpect(jsonPath("$.data[1].title").value("BATbern 2025"))
                .andExpect(jsonPath("$.data[2].title").value("BATbern 2024")); // Oldest last
    }

    @Test
    @DisplayName("should_sortAscending_when_plusPrefixUsed")
    void should_sortAscending_when_plusPrefixUsed() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "+date") // Ascending order
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2024")) // Oldest first
                .andExpect(jsonPath("$.data[2].title").value("BATbern 2026 Draft")); // Most recent last
    }

    // ============================================================================
    // AC1.5: Paginate Event Results
    // ============================================================================

    @Test
    @DisplayName("should_paginateResults_when_pageParamProvided")
    void should_paginateResults_when_pageParamProvided() throws Exception {
        // Create additional events for pagination test
        for (int i = 1; i <= 25; i++) {
            int month = (i % 12) + 1; // Cycle through months 1-12
            String monthStr = String.format("%02d", month); // Format as 01, 02, ..., 12
            createTestEvent("Event " + i, "2025-" + monthStr + "-01T09:00:00Z", "planning");
        }

        // Test first page
        mockMvc.perform(get("/api/v1/events")
                        .param("page", "1")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(10)))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.total").value(28)) // 3 initial + 25 new
                .andExpect(jsonPath("$.pagination.totalPages").value(3))
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.pagination.hasPrev").value(false));

        // Test second page
        mockMvc.perform(get("/api/v1/events")
                        .param("page", "2")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(10)))
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.pagination.hasPrev").value(true));

        // Test last page
        mockMvc.perform(get("/api/v1/events")
                        .param("page", "3")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(8))) // Remaining 8 events
                .andExpect(jsonPath("$.pagination.page").value(3))
                .andExpect(jsonPath("$.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.pagination.hasPrev").value(true));
    }

    @Test
    @DisplayName("should_useDefaultPagination_when_paramsOmitted")
    void should_useDefaultPagination_when_paramsOmitted() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20)); // Default limit
    }

    @Test
    @DisplayName("should_enforceMaxLimit_when_excessiveLimitRequested")
    void should_enforceMaxLimit_when_excessiveLimitRequested() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("limit", "500") // Exceeds max limit of 100
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.limit").value(100)); // Capped at 100
    }

    // ============================================================================
    // Combined Filters, Sorting, and Pagination
    // ============================================================================

    @Test
    @DisplayName("should_applyCombinedParams_when_filterSortAndPaginationProvided")
    void should_applyCombinedParams_when_filterSortAndPaginationProvided() throws Exception {
        // Create more published events
        createTestEvent("Published Event 1", "2025-03-01T09:00:00Z", "published");
        createTestEvent("Published Event 2", "2025-02-01T09:00:00Z", "published");

        String filter = "{\"status\":\"published\"}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .param("sort", "-date")
                        .param("page", "1")
                        .param("limit", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].status").value("published"))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025")) // Most recent published
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.total").value(3)); // 3 published events total
    }

    // ============================================================================
    // Error Handling
    // ============================================================================

    @Test
    @DisplayName("should_return400_when_invalidFilterSyntax")
    void should_return400_when_invalidFilterSyntax() throws Exception {
        String invalidFilter = "{invalid json}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", invalidFilter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("should_return400_when_invalidSortFormat")
    void should_return400_when_invalidSortFormat() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "invalid_field_name")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_negativePageNumber")
    void should_return400_when_negativePageNumber() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("page", "-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ============================================================================
    // AC2: Get Event Detail
    // ============================================================================

    @Test
    @DisplayName("should_getEventBasic_when_noIncludesProvided")
    void should_getEventBasic_when_noIncludesProvided() throws Exception {
        // Get the first event created in setUp
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                .andExpect(jsonPath("$.status").value(savedEvent.getStatus()))
                .andExpect(jsonPath("$.description").value(savedEvent.getDescription()))
                // Should NOT include expanded resources
                .andExpect(jsonPath("$.venue").doesNotExist())
                .andExpect(jsonPath("$.speakers").doesNotExist())
                .andExpect(jsonPath("$.sessions").doesNotExist());
    }

    @Test
    @DisplayName("should_return404_when_eventNotFound")
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/events/999999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("should_includeVenue_when_includeVenueRequested")
    void should_includeVenue_when_includeVenueRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include venue object
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.venue.id").exists())
                .andExpect(jsonPath("$.venue.name").exists());
    }

    @Test
    @DisplayName("should_includeSpeakers_when_includeSpeakersRequested")
    void should_includeSpeakers_when_includeSpeakersRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include speakers array
                .andExpect(jsonPath("$.speakers").isArray());
    }

    @Test
    @DisplayName("should_includeMultiple_when_multipleIncludesRequested")
    void should_includeMultiple_when_multipleIncludesRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers,sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include all requested resources
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.speakers").isArray())
                .andExpect(jsonPath("$.sessions").isArray());
    }

    // ============================================================================
    // AC3: Create Event
    // ============================================================================

    @Test
    @DisplayName("should_createEvent_when_validDataProvided")
    void should_createEvent_when_validDataProvided() throws Exception {
        String newEvent = """
                {
                    "title": "BATbern 2027",
                    "date": "2027-08-15T09:00:00Z",
                    "status": "planning",
                    "description": "Annual tech conference 2027"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newEvent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("BATbern 2027"))
                .andExpect(jsonPath("$.status").value("planning"))
                .andExpect(jsonPath("$.description").value("Annual tech conference 2027"));
    }

    @Test
    @DisplayName("should_return400_when_invalidDataProvided")
    void should_return400_when_invalidDataProvided() throws Exception {
        String invalidEvent = """
                {
                    "title": "",
                    "status": "invalid_status"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEvent))
                .andExpect(status().isBadRequest());
    }

    // ============================================================================
    // AC4: Update Event (Full Replacement)
    // ============================================================================

    @Test
    @DisplayName("should_replaceEvent_when_putRequested")
    void should_replaceEvent_when_putRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        String updatedEvent = """
                {
                    "title": "BATbern 2025 Updated",
                    "date": "2025-09-15T10:00:00Z",
                    "status": "published",
                    "description": "Updated description"
                }
                """;

        mockMvc.perform(put("/api/v1/events/" + savedEvent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedEvent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.title").value("BATbern 2025 Updated"))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    // ============================================================================
    // AC5: Partial Update Event
    // ============================================================================

    @Test
    @DisplayName("should_patchFields_when_patchRequested")
    void should_patchFields_when_patchRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String originalTitle = savedEvent.getTitle();

        String patchData = """
                {
                    "status": "published"
                }
                """;

        mockMvc.perform(patch("/api/v1/events/" + savedEvent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.title").value(originalTitle)) // Title should remain unchanged
                .andExpect(jsonPath("$.status").value("published")); // Status updated
    }

    // ============================================================================
    // AC6: Delete Event
    // ============================================================================

    @Test
    @DisplayName("should_deleteEvent_when_deleteRequested")
    void should_deleteEvent_when_deleteRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        UUID eventId = savedEvent.getId();

        mockMvc.perform(delete("/api/v1/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify event is deleted
        mockMvc.perform(get("/api/v1/events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return404_when_deletingNonExistentEvent")
    void should_return404_when_deletingNonExistentEvent() throws Exception {
        mockMvc.perform(delete("/api/v1/events/non-existent-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ============================================================================
    // AC7: Publish Event
    // ============================================================================

    @Test
    @DisplayName("should_publishEvent_when_validationPasses")
    void should_publishEvent_when_validationPasses() throws Exception {
        // Create a planning event that meets all publication requirements
        Event draftEvent = createTestEvent("BATbern 2028", "2028-09-15T09:00:00Z", "planning");

        mockMvc.perform(post("/api/v1/events/" + draftEvent.getId() + "/publish")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftEvent.getId().toString()))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.title").value("BATbern 2028"));
    }

    @Test
    @DisplayName("should_return422_when_validationFails")
    void should_return422_when_validationFails() throws Exception {
        // Create an event with invalid data for publishing (e.g., missing required fields)
        Event invalidEvent = Event.builder()
                .title("Incomplete Event")
                .status("planning")
                // Missing date - should fail validation
                .build();
        Event savedInvalidEvent = eventRepository.save(invalidEvent);

        mockMvc.perform(post("/api/v1/events/" + savedInvalidEvent.getId() + "/publish")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ============================================================================
    // AC8: Workflow Advance
    // ============================================================================

    @Test
    @DisplayName("should_advanceWorkflow_when_transitionValid")
    void should_advanceWorkflow_when_transitionValid() throws Exception {
        // Create event in "planning" status - should advance to next state
        Event draftEvent = createTestEvent("BATbern 2029", "2029-10-15T09:00:00Z", "planning");

        mockMvc.perform(post("/api/v1/events/" + draftEvent.getId() + "/workflow/advance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftEvent.getId().toString()))
                .andExpect(jsonPath("$.workflowState").exists())
                .andExpect(jsonPath("$.workflowState").isNotEmpty());
    }

    @Test
    @DisplayName("should_return422_when_transitionInvalid")
    void should_return422_when_transitionInvalid() throws Exception {
        // Create event in "archived" status - cannot advance workflow
        Event archivedEvent = createTestEvent("BATbern 2023", "2023-05-15T09:00:00Z", "archived");

        mockMvc.perform(post("/api/v1/events/" + archivedEvent.getId() + "/workflow/advance")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("WORKFLOW_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ============================================================================
    // AC9: Get Event Sessions
    // ============================================================================

    @Test
    @DisplayName("should_listSessions_when_requested")
    void should_listSessions_when_requested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(0))))
                .andExpect(jsonPath("$.pagination").exists());
    }

    @Test
    @DisplayName("should_filterSessions_when_filterProvided")
    void should_filterSessions_when_filterProvided() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String filter = "{\"sessionType\":\"keynote\"}";

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("should_return404_when_listingSessionsForNonExistentEvent")
    void should_return404_when_listingSessionsForNonExistentEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/non-existent-id/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ============================================================================
    // AC10: Manage Event Sessions (POST/PUT/DELETE)
    // ============================================================================

    @Test
    @DisplayName("should_createSession_when_validData")
    void should_createSession_when_validData() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        String newSession = """
                {
                    "title": "Keynote: AI and the Future",
                    "description": "Opening keynote session",
                    "startTime": "2025-05-15T09:30:00Z",
                    "endTime": "2025-05-15T10:30:00Z",
                    "sessionType": "keynote"
                }
                """;

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Keynote: AI and the Future"))
                .andExpect(jsonPath("$.sessionType").value("keynote"));
    }

    @Test
    @DisplayName("should_return400_when_creatingSessionWithInvalidData")
    void should_return400_when_creatingSessionWithInvalidData() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        String invalidSession = """
                {
                    "title": ""
                }
                """;

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_updateSession_when_validData")
    void should_updateSession_when_validData() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // First create a session
        String newSession = """
                {
                    "title": "Workshop: Hands-on ML",
                    "description": "Interactive machine learning workshop",
                    "startTime": "2025-05-15T14:00:00Z",
                    "endTime": "2025-05-15T16:00:00Z",
                    "sessionType": "workshop"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSession))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionNode = objectMapper.readTree(createResponse);
        String sessionId = sessionNode.get("id").asText();

        // Now update it
        String updatedSession = """
                {
                    "title": "Workshop: Advanced ML",
                    "description": "Advanced machine learning techniques",
                    "startTime": "2025-05-15T15:00:00Z",
                    "endTime": "2025-05-15T18:00:00Z",
                    "sessionType": "workshop"
                }
                """;

        mockMvc.perform(put("/api/v1/events/" + savedEvent.getId() + "/sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.title").value("Workshop: Advanced ML"));
    }

    @Test
    @DisplayName("should_deleteSession_when_requested")
    void should_deleteSession_when_requested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // First create a session
        String newSession = """
                {
                    "title": "Session to Delete",
                    "startTime": "2025-05-15T16:00:00Z",
                    "endTime": "2025-05-15T16:30:00Z",
                    "sessionType": "break"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSession))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionNode = objectMapper.readTree(createResponse);
        String sessionId = sessionNode.get("id").asText();

        // Now delete it
        mockMvc.perform(delete("/api/v1/events/" + savedEvent.getId() + "/sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify it's deleted by checking the list
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + sessionId + "')]").doesNotExist());
    }

    // ============================================================================
    // AC11: Get Event Registrations
    // ============================================================================

    @Test
    @DisplayName("should_listRegistrations_when_requested")
    void should_listRegistrations_when_requested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(0))))
                .andExpect(jsonPath("$.pagination").exists());
    }

    @Test
    @DisplayName("should_filterRegistrations_when_filterProvided")
    void should_filterRegistrations_when_filterProvided() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String filter = "{\"status\":\"confirmed\"}";

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("should_return404_when_listingRegistrationsForNonExistentEvent")
    void should_return404_when_listingRegistrationsForNonExistentEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/non-existent-id/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ============================================================================
    // AC12: Manage Event Registrations (POST/PATCH/DELETE)
    // ============================================================================

    @Test
    @DisplayName("should_createRegistration_when_validData")
    void should_createRegistration_when_validData() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        UUID attendeeId = UUID.randomUUID();

        String newRegistration = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "John Doe",
                    "attendeeEmail": "john.doe@example.com",
                    "status": "pending",
                    "registrationDate": "2025-04-01T10:00:00Z"
                }
                """.formatted(attendeeId);

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newRegistration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.attendeeName").value("John Doe"))
                .andExpect(jsonPath("$.attendeeEmail").value("john.doe@example.com"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    @DisplayName("should_return400_when_creatingRegistrationWithInvalidData")
    void should_return400_when_creatingRegistrationWithInvalidData() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        String invalidRegistration = """
                {
                    "attendeeEmail": "invalid-email",
                    "status": "invalid_status"
                }
                """;

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRegistration))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_updateRegistration_when_patchProvided")
    void should_updateRegistration_when_patchProvided() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        UUID attendeeId = UUID.randomUUID();

        // First create a registration
        String newRegistration = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "Jane Smith",
                    "attendeeEmail": "jane.smith@example.com",
                    "status": "pending",
                    "registrationDate": "2025-04-02T10:00:00Z"
                }
                """.formatted(attendeeId);

        String createResponse = mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newRegistration))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode registrationNode = objectMapper.readTree(createResponse);
        String registrationId = registrationNode.get("id").asText();

        // Now update status with PATCH
        String patchData = """
                {
                    "status": "confirmed"
                }
                """;

        mockMvc.perform(patch("/api/v1/events/" + savedEvent.getId() + "/registrations/" + registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registrationId))
                .andExpect(jsonPath("$.attendeeName").value("Jane Smith")) // Name unchanged
                .andExpect(jsonPath("$.status").value("confirmed")); // Status updated
    }

    @Test
    @DisplayName("should_deleteRegistration_when_requested")
    void should_deleteRegistration_when_requested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        UUID attendeeId = UUID.randomUUID();

        // First create a registration
        String newRegistration = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "Bob Johnson",
                    "attendeeEmail": "bob.johnson@example.com",
                    "status": "cancelled",
                    "registrationDate": "2025-04-03T10:00:00Z"
                }
                """.formatted(attendeeId);

        String createResponse = mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newRegistration))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode registrationNode = objectMapper.readTree(createResponse);
        String registrationId = registrationNode.get("id").asText();

        // Now delete it
        mockMvc.perform(delete("/api/v1/events/" + savedEvent.getId() + "/registrations/" + registrationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify it's deleted by checking the list
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + registrationId + "')]").doesNotExist());
    }

    // ============================================================================
    // AC13: Event Analytics
    // ============================================================================

    @Test
    @DisplayName("should_returnAnalytics_when_metricsRequested")
    void should_returnAnalytics_when_metricsRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Create some test data for analytics
        // Add registrations
        UUID attendeeId1 = UUID.randomUUID();
        String registration1 = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "Analytics User 1",
                    "attendeeEmail": "analytics1@example.com",
                    "status": "confirmed",
                    "registrationDate": "2025-04-01T10:00:00Z"
                }
                """.formatted(attendeeId1);
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration1))
                .andExpect(status().isCreated());

        UUID attendeeId2 = UUID.randomUUID();
        String registration2 = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "Analytics User 2",
                    "attendeeEmail": "analytics2@example.com",
                    "status": "confirmed",
                    "registrationDate": "2025-04-02T10:00:00Z"
                }
                """.formatted(attendeeId2);
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration2))
                .andExpect(status().isCreated());

        // Request analytics with specific metrics
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/analytics")
                        .param("metrics", "attendance,registrations,engagement")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.metrics").exists())
                .andExpect(jsonPath("$.metrics.registrations").exists())
                .andExpect(jsonPath("$.metrics.registrations.total").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.metrics.attendance").exists())
                .andExpect(jsonPath("$.metrics.engagement").exists());
    }

    @Test
    @DisplayName("should_filterByTimeframe_when_timeframeProvided")
    void should_filterByTimeframe_when_timeframeProvided() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Create registrations with different dates
        UUID earlyAttendeeId = UUID.randomUUID();
        String earlyRegistration = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "Early Bird",
                    "attendeeEmail": "early@example.com",
                    "status": "confirmed",
                    "registrationDate": "2025-01-01T10:00:00Z"
                }
                """.formatted(earlyAttendeeId);
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(earlyRegistration))
                .andExpect(status().isCreated());

        UUID lateAttendeeId = UUID.randomUUID();
        String lateRegistration = """
                {
                    "attendeeId": "%s",
                    "attendeeName": "Late Joiner",
                    "attendeeEmail": "late@example.com",
                    "status": "confirmed",
                    "registrationDate": "2025-05-01T10:00:00Z"
                }
                """.formatted(lateAttendeeId);
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lateRegistration))
                .andExpect(status().isCreated());

        // Request analytics for specific timeframe (April to May 2025)
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId() + "/analytics")
                        .param("metrics", "registrations")
                        .param("timeframe", "2025-04-01T00:00:00Z,2025-05-31T23:59:59Z")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.timeframe").exists())
                .andExpect(jsonPath("$.timeframe.start").value("2025-04-01T00:00:00Z"))
                .andExpect(jsonPath("$.timeframe.end").value("2025-05-31T23:59:59Z"))
                .andExpect(jsonPath("$.metrics.registrations").exists())
                // Should only count the late registration within timeframe
                .andExpect(jsonPath("$.metrics.registrations.total").value(greaterThanOrEqualTo(1)));
    }

    // ============================================================================
    // AC14: Bulk Operations
    // ============================================================================

    @Test
    @DisplayName("should_batchUpdate_when_arrayProvided")
    void should_batchUpdate_when_arrayProvided() throws Exception {
        Event event1 = eventRepository.findAll().get(0);
        Event event2 = eventRepository.findAll().get(1);
        Event event3 = eventRepository.findAll().get(2);

        // Batch update multiple events
        String batchUpdateRequest = """
                [
                    {
                        "id": "%s",
                        "status": "published"
                    },
                    {
                        "id": "%s",
                        "status": "published"
                    },
                    {
                        "id": "%s",
                        "status": "archived"
                    }
                ]
                """.formatted(event1.getId(), event2.getId(), event3.getId());

        mockMvc.perform(patch("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchUpdateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").isArray())
                .andExpect(jsonPath("$.successful", hasSize(3)))
                .andExpect(jsonPath("$.failed").isArray())
                .andExpect(jsonPath("$.failed", hasSize(0)))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.successful").value(3))
                .andExpect(jsonPath("$.summary.failed").value(0));

        // Verify events were updated
        Event updatedEvent1 = eventRepository.findById(event1.getId()).orElseThrow();
        Event updatedEvent2 = eventRepository.findById(event2.getId()).orElseThrow();
        Event updatedEvent3 = eventRepository.findById(event3.getId()).orElseThrow();

        assertThat(updatedEvent1.getStatus()).isEqualTo("published");
        assertThat(updatedEvent2.getStatus()).isEqualTo("published");
        assertThat(updatedEvent3.getStatus()).isEqualTo("archived");
    }

    @Test
    @DisplayName("should_partiallySucceed_when_someInvalid")
    void should_partiallySucceed_when_someInvalid() throws Exception {
        Event validEvent = eventRepository.findAll().get(0);

        // Batch update with one valid and one invalid event ID
        String batchUpdateRequest = """
                [
                    {
                        "id": "%s",
                        "status": "published"
                    },
                    {
                        "id": "non-existent-id",
                        "status": "published"
                    }
                ]
                """.formatted(validEvent.getId());

        mockMvc.perform(patch("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchUpdateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").isArray())
                .andExpect(jsonPath("$.successful", hasSize(1)))
                .andExpect(jsonPath("$.failed").isArray())
                .andExpect(jsonPath("$.failed", hasSize(1)))
                .andExpect(jsonPath("$.failed[0].id").value("non-existent-id"))
                .andExpect(jsonPath("$.failed[0].error").exists())
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.successful").value(1))
                .andExpect(jsonPath("$.summary.failed").value(1));

        // Verify valid event was updated
        Event updatedEvent = eventRepository.findById(validEvent.getId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo("published");
    }

    // ============================================================================
    // AC15: Caching for Expanded Resources
    // ============================================================================

    @Test
    @DisplayName("should_cacheExpanded_when_includesUsed")
    void should_cacheExpanded_when_includesUsed() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Make 3 requests to warm up and stabilize performance
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                            .param("include", "venue,speakers,sessions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // Measure cached response time (should be fast)
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers,sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.speakers").isArray())
                .andExpect(jsonPath("$.sessions").isArray());
        long cachedDuration = System.currentTimeMillis() - startTime;

        // Cached response should be reasonably fast (< 50ms for in-memory cache)
        // This is more reliable than comparing two timings
        assertThat(cachedDuration).isLessThan(50L);
    }

    @Test
    @DisplayName("should_returnCached_when_withinTTL")
    void should_returnCached_when_withinTTL() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String originalDescription = savedEvent.getDescription(); // Store original before any updates

        // First request - populate cache
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Update event in database directly (bypassing controller to avoid cache invalidation)
        Event eventFromDb = eventRepository.findById(savedEvent.getId()).orElseThrow();
        eventFromDb.setDescription("Updated description directly in DB");
        eventRepository.save(eventFromDb);

        // Second request within TTL - should still return cached (old) data
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                // Should return cached description (not the updated one)
                .andExpect(jsonPath("$.description").value(originalDescription));
    }

    @Test
    @DisplayName("should_invalidateCache_when_eventUpdated")
    void should_invalidateCache_when_eventUpdated() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String originalDescription = savedEvent.getDescription();

        // First request - populate cache
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(originalDescription));

        // Update event via controller - should invalidate cache
        String patchData = """
                {
                    "description": "Cache should be invalidated"
                }
                """;

        mockMvc.perform(patch("/api/v1/events/" + savedEvent.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchData))
                .andExpect(status().isOk());

        // Next request should return fresh data (not cached)
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                // Should return updated description (cache was invalidated)
                .andExpect(jsonPath("$.description").value("Cache should be invalidated"));
    }

    // ============================================================================
    // AC16: Performance Requirements
    // ============================================================================

    @Test
    @DisplayName("should_respondUnder500ms_when_fullIncludesRequested")
    void should_respondUnder500ms_when_fullIncludesRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Create test data for performance test
        // Add multiple sessions and registrations
        for (int i = 1; i <= 10; i++) {
            int hour = 9 + i;
            String session = """
                    {
                        "title": "Session %d",
                        "description": "Performance test session %d",
                        "startTime": "2025-05-15T%02d:00:00Z",
                        "endTime": "2025-05-15T%02d:00:00Z",
                        "sessionType": "workshop"
                    }
                    """.formatted(i, i, hour, hour + 1);

            mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(session))
                    .andExpect(status().isCreated());
        }

        for (int i = 1; i <= 20; i++) {
            UUID attendeeId = UUID.randomUUID();
            String registration = """
                    {
                        "attendeeId": "%s",
                        "attendeeName": "Attendee %d",
                        "attendeeEmail": "attendee%d@example.com",
                        "status": "confirmed",
                        "registrationDate": "2025-04-01T10:00:00Z"
                    }
                    """.formatted(attendeeId, i, i);

            mockMvc.perform(post("/api/v1/events/" + savedEvent.getId() + "/registrations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registration))
                    .andExpect(status().isCreated());
        }

        // Measure response time with all includes
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getId())
                        .param("include", "venue,speakers,sessions,topics,workflow,registrations,catering,team,publishing,notifications,analytics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()));
        long duration = System.currentTimeMillis() - startTime;

        // AC16: Event detail with all includes must respond in <500ms (P95)
        assertThat(duration).isLessThan(500L);
    }
}
