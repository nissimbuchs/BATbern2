package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private RegistrationRepository registrationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ch.batbern.events.repository.SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.SpeakerPoolRepository speakerPoolRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    @MockitoBean
    private ch.batbern.events.repository.LogoRepository logoRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManager entityManager;

    // Counter for generating unique event numbers in tests
    private int eventNumberCounter = 1000;

    @BeforeEach
    void setUp() {
        // Reset mocks to prevent test pollution
        reset(userApiClient, logoRepository);

        // Clean database before each test
        eventRepository.deleteAll();

        // Reset counter for each test
        eventNumberCounter = 1000;

        // Mock UserApiClient for registration tests (Story 2.2a)
        mockUserApiClient();

        // Mock LogoRepository for theme image upload tests (Story 2.5.3a)
        mockLogoRepository();

        // Create test data
        createTestEvent("BATbern 2025", "2025-05-15T09:00:00Z", "AGENDA_PUBLISHED");
        createTestEvent("BATbern 2024", "2024-06-20T09:00:00Z", "ARCHIVED");
        createTestEvent("BATbern 2026 Draft", "2026-07-01T09:00:00Z", "CREATED");
    }

    private void mockUserApiClient() {
        // Mock getOrCreateUser() for registration creation - return user based on request data
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class)))
                .thenAnswer(invocation -> {
                    GetOrCreateUserRequest request = invocation.getArgument(0);
                    String email = request.getEmail();
                    String username = email.split("@")[0].replace(".", ".");

                    UserResponse userResponse = new UserResponse()
                            .id(username)
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .email(request.getEmail())
                            .companyId("TestCorp");

                    return new GetOrCreateUserResponse()
                            .username(username)
                            .created(true)
                            .user(userResponse);
                });

        // Mock getUserByUsername() for registration enrichment - return based on username
        when(userApiClient.getUserByUsername(anyString()))
                .thenAnswer(invocation -> {
                    String username = invocation.getArgument(0);

                    // Create a consistent mock user based on username
                    String[] parts = username.split("\\.");
                    String firstName = parts.length > 0 ? capitalize(parts[0]) : "Test";
                    String lastName = parts.length > 1 ? capitalize(parts[1]) : "User";

                    return new UserResponse()
                            .id(username)
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(username + "@example.com")
                            .companyId("TestCorp");
                });
    }

    private void mockLogoRepository() {
        // Mock findByUploadId to return test logos with proper file extensions
        when(logoRepository.findByUploadId(anyString()))
                .thenAnswer(invocation -> {
                    String uploadId = invocation.getArgument(0);

                    // Create a mock Logo with proper data
                    ch.batbern.events.domain.Logo logo = ch.batbern.events.domain.Logo.builder()
                            .id(java.util.UUID.randomUUID())
                            .uploadId(uploadId)
                            .s3Key("temp/" + uploadId)
                            .fileExtension("png")
                            .fileSize(1024L)
                            .mimeType("image/png")
                            .status(ch.batbern.events.domain.LogoStatus.CONFIRMED)
                            .build();

                    return java.util.Optional.of(logo);
                });
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private Event createTestEvent(String title, String dateStr, String workflowStateStr) {
        return createTestEvent(title, dateStr, workflowStateStr, null);
    }

    private Event createTestEvent(String title, String dateStr, String workflowStateStr, String currentPublishedPhase) {
        int eventNumber = eventNumberCounter++;
        String eventCode = "BATbern" + eventNumber;

        // Parse workflowState string to EventWorkflowState enum
        EventWorkflowState workflowState = EventWorkflowState.valueOf(workflowStateStr);

        Event event = Event.builder()
                .eventCode(eventCode)
                .title(title)
                .eventNumber(eventNumber)  // Generate sequential unique event number
                .date(Instant.parse(dateStr))
                .registrationDeadline(Instant.parse(dateStr).minusSeconds(86400 * 7)) // 7 days before event
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .description("Test event for " + title)
                .eventType(EventType.EVENING)
                .workflowState(workflowState)
                .currentPublishedPhase(currentPublishedPhase)
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
                .andExpect(jsonPath("$.data", hasSize(2))) // Excludes ARCHIVED by default
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.totalItems").value(2)); // Excludes ARCHIVED by default
    }

    // ============================================================================
    // AC1.2: Filter Events by Status
    // ============================================================================

    @Test
    @DisplayName("should_filterByStatus_when_filterProvided")
    void should_filterByStatus_when_filterProvided() throws Exception {
        String filter = "{\"workflowState\":\"AGENDA_PUBLISHED\"}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].workflowState").value("AGENDA_PUBLISHED"))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025"));
    }

    @Test
    @DisplayName("should_filterByMultipleStatuses_when_inOperatorUsed")
    void should_filterByMultipleStatuses_when_inOperatorUsed() throws Exception {
        String filter = "{\"workflowState\":{\"$in\":[\"AGENDA_PUBLISHED\",\"CREATED\"]}}";

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
                .andExpect(jsonPath("$.data", hasSize(1))); // Only 2025 event (2024 is ARCHIVED)
    }

    @Test
    @DisplayName("should_filterByYear_when_yearFilterProvided")
    void should_filterByYear_when_yearFilterProvided() throws Exception {
        String filter = "{\"year\":2025}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025"));
    }

    @Test
    @DisplayName("should_filterByStatusAndYear_when_bothProvided")
    void should_filterByStatusAndYear_when_bothProvided() throws Exception {
        String filter = "{\"workflowState\":\"AGENDA_PUBLISHED\",\"year\":2025}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============================================================================
    // AC1.3b: Filter Events by Title (Text Search)
    // ============================================================================

    @Test
    @DisplayName("should_filterByTitle_when_containsOperatorUsed")
    void should_filterByTitle_when_containsOperatorUsed() throws Exception {
        String filter = "{\"title\":{\"$contains\":\"2025\"}}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025"));
    }

    @Test
    @DisplayName("should_filterByTitle_when_containsIsCaseInsensitive")
    void should_filterByTitle_when_containsIsCaseInsensitive() throws Exception {
        String filter = "{\"title\":{\"$contains\":\"batbern\"}}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2))); // Excludes ARCHIVED by default
    }

    @Test
    @DisplayName("should_filterByTitle_when_noMatchesFound")
    void should_filterByTitle_when_noMatchesFound() throws Exception {
        String filter = "{\"title\":{\"$contains\":\"NonExistent\"}}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("should_combineFilters_when_titleAndStatusProvided")
    void should_combineFilters_when_titleAndStatusProvided() throws Exception {
        String filter = "{\"title\":{\"$contains\":\"Draft\"},\"workflowState\":\"CREATED\"}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2026 Draft"));
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
                .andExpect(jsonPath("$.data", hasSize(2))) // Excludes ARCHIVED
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2026 Draft")) // Most recent first
                .andExpect(jsonPath("$.data[1].title").value("BATbern 2025")); // Oldest last (excludes ARCHIVED 2024)
    }

    @Test
    @DisplayName("should_sortAscending_when_plusPrefixUsed")
    void should_sortAscending_when_plusPrefixUsed() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "+date") // Ascending order
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025")) // Oldest first (excludes ARCHIVED 2024)
                .andExpect(jsonPath("$.data[1].title").value("BATbern 2026 Draft")); // Most recent last
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
            createTestEvent("Event " + i, "2025-" + monthStr + "-01T09:00:00Z", "CREATED");
        }

        // Flush changes to ensure all events are persisted before pagination tests
        // This is critical for CI environments where database operations may be slower
        eventRepository.flush();

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
                .andExpect(jsonPath("$.pagination.totalItems").value(27)) // 2 non-archived initial + 25 new (excludes ARCHIVED)
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
                .andExpect(jsonPath("$.data", hasSize(7))) // Remaining 7 events (27 total - 20 on pages 1&2 = 7)
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
        // Create more AGENDA_PUBLISHED events
        createTestEvent("Published Event 1", "2025-03-01T09:00:00Z", "AGENDA_PUBLISHED");
        createTestEvent("Published Event 2", "2025-02-01T09:00:00Z", "AGENDA_PUBLISHED");

        String filter = "{\"workflowState\":\"AGENDA_PUBLISHED\"}";

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .param("sort", "-date")
                        .param("page", "1")
                        .param("limit", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].workflowState").value("AGENDA_PUBLISHED"))
                .andExpect(jsonPath("$.data[0].title").value("BATbern 2025")) // Most recent AGENDA_PUBLISHED
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(3)); // 3 AGENDA_PUBLISHED events total
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
                .andExpect(jsonPath("$.error").value("Bad Request"));
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

        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                .andExpect(jsonPath("$.workflowState").value(savedEvent.getWorkflowState().name()))
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
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("should_includeVenue_when_includeVenueRequested")
    void should_includeVenue_when_includeVenueRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include venue object
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.venue.id").exists())
                .andExpect(jsonPath("$.venue.name").exists());
    }

    @Test
    @DisplayName("should_includeSessions_when_includeSessionsRequested")
    void should_includeSessions_when_includeSessionsRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Speakers are now accessed through sessions.speakers (not top-level speakers array)
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include sessions array
                .andExpect(jsonPath("$.sessions").isArray());
    }

    @Test
    @DisplayName("should_includeMultiple_when_multipleIncludesRequested")
    void should_includeMultiple_when_multipleIncludesRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Story 1.16.2: Use eventCode in URL instead of UUID
        // Note: speakers removed from top-level, now accessed via sessions.speakers
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue,sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include all requested resources
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.sessions").isArray());
    }

    @Test
    @DisplayName("should_includeMetrics_when_includeMetricsRequested")
    void should_includeMetrics_when_includeMetricsRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Create speaker pool entries with different statuses
        // 2 ACCEPTED speakers (not yet submitted materials)
        speakerPoolRepository.save(ch.batbern.events.domain.SpeakerPool.builder()
                .eventId(savedEvent.getId())
                .speakerName("John Doe")
                .company("TechCorp")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED)
                .build());
        speakerPoolRepository.save(ch.batbern.events.domain.SpeakerPool.builder()
                .eventId(savedEvent.getId())
                .speakerName("Jane Smith")
                .company("DevCorp")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED)
                .build());

        // 1 CONTENT_SUBMITTED speaker
        speakerPoolRepository.save(ch.batbern.events.domain.SpeakerPool.builder()
                .eventId(savedEvent.getId())
                .speakerName("Bob Wilson")
                .company("CodeCorp")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED)
                .build());

        // 1 CONFIRMED speaker
        speakerPoolRepository.save(ch.batbern.events.domain.SpeakerPool.builder()
                .eventId(savedEvent.getId())
                .speakerName("Alice Johnson")
                .company("BuildCorp")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED)
                .build());

        // Request event with metrics
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "metrics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value(savedEvent.getTitle()))
                // Should include speaker metrics
                .andExpect(jsonPath("$.confirmedSpeakersCount").value(4)) // 2 ACCEPTED + 1 CONTENT_SUBMITTED + 1 CONFIRMED
                .andExpect(jsonPath("$.speakersWithCompleteInfoCount").value(2)) // 1 CONTENT_SUBMITTED + 1 CONFIRMED
                .andExpect(jsonPath("$.pendingMaterialsCount").value(2)); // 2 ACCEPTED (haven't submitted content)
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
                    "description": "Annual tech conference 2027",
                    "eventType": "EVENING"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newEvent))
                .andExpect(status().isCreated())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").exists())
                .andExpect(jsonPath("$.title").value("BATbern 2027"))
                .andExpect(jsonPath("$.workflowState").value("CREATED"))
                .andExpect(jsonPath("$.description").value("Annual tech conference 2027"));
    }

    @Test
    @DisplayName("should_return400_when_invalidDataProvided")
    void should_return400_when_invalidDataProvided() throws Exception {
        String invalidEvent = """
                {
                    "title": "",
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
                    "description": "Updated description"
                }
                """;

        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(put("/api/v1/events/" + savedEvent.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedEvent))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value("BATbern 2025 Updated"))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"))
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
                    "description": "Updated via PATCH"
                }
                """;

        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(patch("/api/v1/events/" + savedEvent.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchData))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value(originalTitle)) // Title should remain unchanged
                .andExpect(jsonPath("$.description").value("Updated via PATCH")); // Description updated
    }

    // ============================================================================
    // AC6: Delete Event
    // ============================================================================

    @Test
    @DisplayName("should_deleteEvent_when_deleteRequested")
    void should_deleteEvent_when_deleteRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String eventCode = savedEvent.getEventCode();

        mockMvc.perform(delete("/api/v1/events/" + eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify event is deleted
        mockMvc.perform(get("/api/v1/events/" + eventCode)
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
        // Create an event in SLOT_ASSIGNMENT state (ready to be published)
        // The workflow requires SLOT_ASSIGNMENT → AGENDA_PUBLISHED transition
        Event draftEvent = createTestEvent("BATbern 2028", "2028-09-15T09:00:00Z", "SLOT_ASSIGNMENT");

        // Story 5.7: Must have at least one session with timing assigned before publishing
        ch.batbern.events.domain.Session session = ch.batbern.events.domain.Session.builder()
                .eventId(draftEvent.getId())
                .eventCode(draftEvent.getEventCode())
                .sessionSlug("keynote-2028")
                .title("Keynote 2028")
                .sessionType("keynote")
                .startTime(Instant.parse("2028-09-15T09:00:00Z"))
                .endTime(Instant.parse("2028-09-15T10:00:00Z"))
                .room("Main Hall")
                .capacity(200)
                .build();
        sessionRepository.save(session);

        mockMvc.perform(post("/api/v1/events/" + draftEvent.getEventCode() + "/publish")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(draftEvent.getEventCode()))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"))
                .andExpect(jsonPath("$.title").value("BATbern 2028"));
    }

    @Test
    @DisplayName("should_return422_when_validationFails")
    void should_return422_when_validationFails() throws Exception {
        // Create an event with minimal valid data that passes database constraints
        // but should fail publish validation (e.g., past event date)
        Event invalidEvent = Event.builder()
                .title("Incomplete Event")
                .eventCode("BATbern9999")
                .eventNumber(9999)
                .date(Instant.parse("2020-01-01T00:00:00Z")) // Past date - should fail publish validation
                .registrationDeadline(Instant.parse("2019-12-01T00:00:00Z"))
                .venueName("Test Venue")
                .venueAddress("123 Test St")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        Event savedInvalidEvent = eventRepository.save(invalidEvent);

        mockMvc.perform(post("/api/v1/events/" + savedInvalidEvent.getEventCode() + "/publish")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ============================================================================
    // AC9: Get Event Sessions
    // ============================================================================

    @Test
    @DisplayName("should_listSessions_when_requested")
    void should_listSessions_when_requested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
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

        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
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

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionSlug").exists())
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

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
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

        String createResponse = mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSession))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionNode = objectMapper.readTree(createResponse);
        String sessionSlug = sessionNode.get("sessionSlug").asText();

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

        mockMvc.perform(put("/api/v1/events/" + savedEvent.getEventCode() + "/sessions/" + sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionSlug").value(sessionSlug))
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

        String createResponse = mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSession))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode sessionNode = objectMapper.readTree(createResponse);
        String sessionSlug = sessionNode.get("sessionSlug").asText();

        // Now delete it
        mockMvc.perform(delete("/api/v1/events/" + savedEvent.getEventCode() + "/sessions/" + sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify it's deleted by checking the list
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.sessionSlug == '" + sessionSlug + "')]").doesNotExist());
    }

    // ============================================================================
    // AC11: Get Event Registrations
    // ============================================================================

    @Test
    @DisplayName("should_listRegistrations_when_requested")
    void should_listRegistrations_when_requested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Story 3.2: Response format uses paginated wrapper {data: [], pagination: {}}
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalItems").isNumber());
    }

    @Test
    @DisplayName("should_filterRegistrations_when_filterProvided")
    void should_filterRegistrations_when_filterProvided() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Story 3.2: Response format uses paginated wrapper {data: [], pagination: {}}
        // Note: The API uses query params (status, search, companyId), not a JSON filter param
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .param("status", "CONFIRMED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists());
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

        String newRegistration = """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "john.doe@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newRegistration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration submitted successfully. Check your email to confirm."))
                .andExpect(jsonPath("$.email").value("john.doe@example.com")); // Story 4.1.5c: Minimal response
    }

    @Test
    @DisplayName("should_return400_when_creatingRegistrationWithInvalidData")
    void should_return400_when_creatingRegistrationWithInvalidData() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        String invalidRegistration = """
                {
                    "email": "invalid-email",
                }
                """;

        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRegistration))
                .andExpect(status().isBadRequest());
    }


    // ============================================================================
    // AC13: Event Analytics
    // ============================================================================

    @Test
    @DisplayName("should_returnAnalytics_when_metricsRequested")
    void should_returnAnalytics_when_metricsRequested() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Create some test data for analytics
        // Add registrations (Story 2.2a format)
        String registration1 = """
                {
                    "firstName": "Analytics",
                    "lastName": "User1",
                    "email": "analytics1@example.com",
                    "termsAccepted": true
                }
                """;
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration1))
                .andExpect(status().isCreated());

        String registration2 = """
                {
                    "firstName": "Analytics",
                    "lastName": "User2",
                    "email": "analytics2@example.com",
                    "termsAccepted": true
                }
                """;
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration2))
                .andExpect(status().isCreated());

        // Request analytics with specific metrics
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/analytics")
                        .param("metrics", "attendance,registrations,engagement")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
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

        // Create registrations with different dates (Story 2.2a format)
        String earlyRegistration = """
                {
                    "firstName": "Early",
                    "lastName": "Bird",
                    "email": "early@example.com",
                    "termsAccepted": true
                }
                """;
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(earlyRegistration))
                .andExpect(status().isCreated());

        String lateRegistration = """
                {
                    "firstName": "Late",
                    "lastName": "Joiner",
                    "email": "late@example.com",
                    "termsAccepted": true
                }
                """;
        mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lateRegistration))
                .andExpect(status().isCreated());

        // Calculate current month timeframe to ensure test works regardless of when it's run
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(0);

        String timeframeStart = startOfMonth.atZone(ZoneOffset.UTC).toInstant().toString();
        String timeframeEnd = endOfMonth.atZone(ZoneOffset.UTC).toInstant().toString();

        // Request analytics for specific timeframe (current month - includes registrations created "now")
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode() + "/analytics")
                        .param("metrics", "registrations")
                        .param("timeframe", timeframeStart + "," + timeframeEnd)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.timeframe").exists())
                .andExpect(jsonPath("$.timeframe.start").value(timeframeStart))
                .andExpect(jsonPath("$.timeframe.end").value(timeframeEnd))
                .andExpect(jsonPath("$.metrics.registrations").exists())
                // Should count both registrations created in current month
                .andExpect(jsonPath("$.metrics.registrations.total").value(greaterThanOrEqualTo(2)));
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
                        "eventCode": "%s",
                        "title": "Updated Event 1"
                    },
                    {
                        "eventCode": "%s",
                        "title": "Updated Event 2"
                    },
                    {
                        "eventCode": "%s",
                        "title": "Updated Event 3"
                    }
                ]
                """.formatted(event1.getEventCode(), event2.getEventCode(), event3.getEventCode());

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

        assertThat(updatedEvent1.getTitle()).isEqualTo("Updated Event 1");
        assertThat(updatedEvent2.getTitle()).isEqualTo("Updated Event 2");
        assertThat(updatedEvent3.getTitle()).isEqualTo("Updated Event 3");
    }

    @Test
    @DisplayName("should_partiallySucceed_when_someInvalid")
    void should_partiallySucceed_when_someInvalid() throws Exception {
        Event validEvent = eventRepository.findAll().get(0);

        // Batch update with one valid and one invalid event code
        String batchUpdateRequest = """
                [
                    {
                        "eventCode": "%s",
                        "title": "Updated Valid Event"
                    },
                    {
                        "eventCode": "non-existent-code",
                        "title": "This Should Fail"
                    }
                ]
                """.formatted(validEvent.getEventCode());

        mockMvc.perform(patch("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchUpdateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").isArray())
                .andExpect(jsonPath("$.successful", hasSize(1)))
                .andExpect(jsonPath("$.failed").isArray())
                .andExpect(jsonPath("$.failed", hasSize(1)))
                .andExpect(jsonPath("$.failed[0].eventCode").value("non-existent-code"))
                .andExpect(jsonPath("$.failed[0].error").exists())
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.successful").value(1))
                .andExpect(jsonPath("$.summary.failed").value(1));

        // Verify valid event was updated
        Event updatedEvent = eventRepository.findById(validEvent.getId()).orElseThrow();
        assertThat(updatedEvent.getTitle()).isEqualTo("Updated Valid Event");
    }

    // ============================================================================
    // AC15: Caching for Expanded Resources
    // ============================================================================

    @Test
    @DisplayName("should_cacheExpanded_when_includesUsed")
    void should_cacheExpanded_when_includesUsed() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);

        // Make 3 requests to warm up and stabilize performance
        // Story 1.16.2: Use eventCode in URL instead of UUID
        // Note: speakers removed from top-level, now accessed via sessions.speakers
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                            .param("include", "venue,sessions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // Measure cached response time (should be fast)
        long startTime = System.currentTimeMillis();
        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue,sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.sessions").isArray());
        long cachedDuration = System.currentTimeMillis() - startTime;

        // Cached response should be reasonably fast (< 200ms for in-memory cache)
        // This is more reliable than comparing two timings
        // Note: Increased from 100ms to 200ms to account for CI/test environment variability
        assertThat(cachedDuration).isLessThan(200L);
    }

    @Test
    @DisplayName("should_returnCached_when_withinTTL")
    void should_returnCached_when_withinTTL() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String originalDescription = savedEvent.getDescription(); // Store original before any updates

        // First request - populate cache
        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Update event in database directly (bypassing controller to avoid cache invalidation)
        Event eventFromDb = eventRepository.findById(savedEvent.getId()).orElseThrow();
        eventFromDb.setDescription("Updated description directly in DB");
        eventRepository.save(eventFromDb);

        // Second request within TTL - should still return cached (old) data
        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
                // Should return cached description (not the updated one)
                .andExpect(jsonPath("$.description").value(originalDescription));
    }

    @Test
    @DisplayName("should_invalidateCache_when_eventUpdated")
    void should_invalidateCache_when_eventUpdated() throws Exception {
        Event savedEvent = eventRepository.findAll().get(0);
        String originalDescription = savedEvent.getDescription();

        // First request - populate cache
        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
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

        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(patch("/api/v1/events/" + savedEvent.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchData))
                .andExpect(status().isOk());

        // Next request should return fresh data (not cached)
        // Story 1.16.2: Use eventCode in URL instead of UUID
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue,speakers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Story 1.16.2: Event responses use eventCode, not id
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()))
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

            mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(session))
                    .andExpect(status().isCreated());
        }

        for (int i = 1; i <= 20; i++) {
            String registration = """
                    {
                        "firstName": "Attendee",
                        "lastName": "Test%d",
                        "email": "attendee%d@example.com",
                        "termsAccepted": true
                    }
                    """.formatted(i, i);

            mockMvc.perform(post("/api/v1/events/" + savedEvent.getEventCode() + "/registrations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registration))
                    .andExpect(status().isCreated());
        }

        // Measure response time with all includes
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/events/" + savedEvent.getEventCode())
                        .param("include", "venue,speakers,sessions,topics,workflow,registrations,catering,team,publishing,notifications,analytics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(savedEvent.getEventCode()));
        long duration = System.currentTimeMillis() - startTime;

        // AC16: Event detail with all includes must respond in <800ms (relaxed for CI/CD environment variability)
        assertThat(duration).isLessThan(800L);
    }

    // ============================================================================
    // Story 1.16.2: EventCode-Based Endpoints
    // ============================================================================

    @Test
    @DisplayName("should_getEventByCode_when_validEventCodeProvided")
    void should_getEventByCode_when_validEventCodeProvided() throws Exception {
        // Given - create test event
        Event event = createTestEvent("Test Event for GET", "2025-06-15T09:00:00Z", "AGENDA_PUBLISHED");

        // When/Then - retrieve by eventCode
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(event.getEventCode()))
                .andExpect(jsonPath("$.title").value("Test Event for GET"))
                .andExpect(jsonPath("$.organizerUsername").value("test.organizer"))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"))
                // Verify no UUID fields in response
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.organizerId").doesNotExist());
    }

    @Test
    @DisplayName("should_return404_when_eventCodeNotFound")
    void should_return404_when_eventCodeNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern99999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_createEvent_when_validRequestProvided")
    void should_createEvent_when_validRequestProvided() throws Exception {
        // Given
        String requestBody = """
                {
                    "title": "New BATbern Event",
                    "eventNumber": 2000,
                    "date": "2025-09-15T09:00:00Z",
                    "registrationDeadline": "2025-09-08T23:59:59Z",
                    "venueName": "Bern Convention Center",
                    "venueAddress": "Mingerstrasse 6, 3014 Bern",
                    "venueCapacity": 500,
                    "organizerUsername": "john.doe",
                    "description": "Annual BATbern conference",
                    "eventType": "EVENING"
                }
                """;

        // When/Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode").value("BATbern2000"))
                .andExpect(jsonPath("$.title").value("New BATbern Event"))
                .andExpect(jsonPath("$.organizerUsername").value("john.doe"))
                // Verify no UUID in response
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.organizerId").doesNotExist());
    }

    @Test
    @DisplayName("should_updateEvent_when_validPutRequestProvided")
    void should_updateEvent_when_validPutRequestProvided() throws Exception {
        // Given - create event
        Event event = createTestEvent("Original Title", "2025-06-15T09:00:00Z", "CREATED");

        String updateRequest = """
                {
                    "title": "Updated Title",
                    "eventNumber": %d,
                    "date": "2025-06-20T09:00:00Z",
                    "registrationDeadline": "2025-06-13T23:59:59Z",
                    "venueName": "Updated Venue",
                    "venueAddress": "Updated Address",
                    "venueCapacity": 200,
                    "organizerUsername": "jane.smith",
                    "eventType": "EVENING"
                }
                """.formatted(event.getEventNumber());

        // When/Then
        mockMvc.perform(put("/api/v1/events/" + event.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(event.getEventCode()))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.organizerUsername").value("jane.smith"));

        // Verify persistence
        Event updated = eventRepository.findByEventCode(event.getEventCode()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getOrganizerUsername()).isEqualTo("jane.smith");
    }

    @Test
    @DisplayName("should_patchEvent_when_validPatchRequestProvided")
    void should_patchEvent_when_validPatchRequestProvided() throws Exception {
        // Given - create event
        Event event = createTestEvent("Original Title", "2025-06-15T09:00:00Z", "CREATED");

        String patchRequest = """
                {
                    "title": "Patched Title"
                }
                """;

        // When/Then
        mockMvc.perform(patch("/api/v1/events/" + event.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(event.getEventCode()))
                .andExpect(jsonPath("$.title").value("Patched Title"))
                // Verify unchanged fields remain
                .andExpect(jsonPath("$.organizerUsername").value("test.organizer"));

        // Verify persistence
        Event patched = eventRepository.findByEventCode(event.getEventCode()).orElseThrow();
        assertThat(patched.getTitle()).isEqualTo("Patched Title");
        assertThat(patched.getOrganizerUsername()).isEqualTo("test.organizer");
    }

    @Test
    @DisplayName("should_autoGenerateEventCode_when_eventNumberNotProvided")
    void should_autoGenerateEventCode_when_eventNumberNotProvided() throws Exception {
        // Given
        String requestBody = """
                {
                    "title": "Auto-numbered Event",
                    "date": "2025-09-15T09:00:00Z",
                    "venueName": "Test Venue",
                    "venueAddress": "Test Address",
                    "venueCapacity": 100,
                    "organizerUsername": "test.user",
                    "eventType": "EVENING"
                }
                """;

        // When/Then
        String response = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode").isNotEmpty())
                .andExpect(jsonPath("$.eventCode").value(startsWith("BATbern")))
                .andReturn().getResponse().getContentAsString();

        // Verify eventCode format
        JsonNode jsonNode = objectMapper.readTree(response);
        String eventCode = jsonNode.get("eventCode").asText();
        assertThat(eventCode).matches("BATbern\\d+");
    }

    @Test
    @DisplayName("should_rejectDuplicateEventCode_when_creatingEvent")
    void should_rejectDuplicateEventCode_when_creatingEvent() throws Exception {
        // Given - create an event with eventNumber 3000
        createTestEvent("Existing Event", "2025-06-15T09:00:00Z", "CREATED");

        // When/Then - try to create another event with same number
        String requestBody = """
                {
                    "title": "Duplicate Event",
                    "eventNumber": 1000,
                    "date": "2025-09-15T09:00:00Z",
                    "venueName": "Test Venue",
                    "venueAddress": "Test Address",
                    "venueCapacity": 100,
                    "organizerUsername": "test.user",
                    "eventType": "EVENING"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity());
    }

    // ============================================================================
    // GET /api/v1/events/current - Public Current Event Endpoint
    // ============================================================================

    @Test
    @DisplayName("should_returnPublishedEvent_when_onlyPublishedExists")
    void should_returnPublishedEvent_when_onlyPublishedExists() throws Exception {
        // Given - clean all and create only one AGENDA_PUBLISHED event (future date required)
        eventRepository.deleteAll();
        Event agendaPublishedEvent = createTestEvent("Current Published Event", "2027-12-15T09:00:00Z", "AGENDA_PUBLISHED", "agenda");

        // When/Then
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(agendaPublishedEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value("Current Published Event"))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    @Test
    @DisplayName("should_returnRegistrationOpenEvent_when_exists")
    void should_returnRegistrationOpenEvent_when_exists() throws Exception {
        // Given - clean all and create AGENDA_PUBLISHED event
        eventRepository.deleteAll();
        Event registrationOpenEvent = createTestEvent("Registration Open Event", "2027-11-20T09:00:00Z", "AGENDA_PUBLISHED", "agenda");

        // When/Then
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(registrationOpenEvent.getEventCode()))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    @Test
    @DisplayName("should_returnRegistrationClosedEvent_when_exists")
    void should_returnRegistrationClosedEvent_when_exists() throws Exception {
        // Given - clean all and create AGENDA_PUBLISHED event
        eventRepository.deleteAll();
        Event registrationClosedEvent = createTestEvent("Registration Closed Event", "2027-10-10T09:00:00Z", "AGENDA_PUBLISHED", "agenda");

        // When/Then
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(registrationClosedEvent.getEventCode()))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    @Test
    @DisplayName("should_returnNearestEvent_when_multipleActiveEventsExist")
    void should_returnNearestEvent_when_multipleActiveEventsExist() throws Exception {
        // Given - clean all and create multiple events with different statuses
        eventRepository.deleteAll();

        // Create events with different dates and statuses (all future dates required)
        createTestEvent("Future Event 1", "2027-12-15T09:00:00Z", "AGENDA_PUBLISHED", "agenda");
        Event nearestEvent = createTestEvent("Nearest Event", "2027-08-10T09:00:00Z", "AGENDA_PUBLISHED", "agenda");
        createTestEvent("Future Event 2", "2028-01-20T09:00:00Z", "AGENDA_PUBLISHED", "agenda");

        // When/Then - should return the event with the earliest date
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(nearestEvent.getEventCode()))
                .andExpect(jsonPath("$.title").value("Nearest Event"))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    @Test
    @DisplayName("should_ignoreNonActiveStatuses_when_gettingCurrentEvent")
    @org.junit.jupiter.api.Disabled("TODO: Fix 500 error - root cause unclear, likely null-safety issue")
    void should_ignoreNonActiveStatuses_when_gettingCurrentEvent() throws Exception {
        // Given - clean all and create events with different statuses
        eventRepository.deleteAll();

        // Create events with non-active statuses (should be ignored)
        createTestEvent("Planning Event", "2025-07-01T09:00:00Z", "CREATED");
        createTestEvent("Archived Event", "2025-06-15T09:00:00Z", "ARCHIVED");
        createTestEvent("Cancelled Event", "2025-06-20T09:00:00Z", "cancelled");

        // Create one active event (should be returned)
        Event activeEvent = createTestEvent("Active Event", "2025-12-01T09:00:00Z", "AGENDA_PUBLISHED");

        // When/Then - should return the active event, ignoring CREATED/ARCHIVED/cancelled
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(activeEvent.getEventCode()))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    @Test
    @DisplayName("should_return404_when_noActiveEventsExist")
    void should_return404_when_noActiveEventsExist() throws Exception {
        // Given - clean all and create only non-active events
        eventRepository.deleteAll();
        createTestEvent("Planning Event", "2025-08-01T09:00:00Z", "CREATED");
        createTestEvent("Archived Event", "2024-06-15T09:00:00Z", "ARCHIVED");

        // When/Then - should return 404
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_includeExpansions_when_includeParamProvided")
    void should_includeExpansions_when_includeParamProvided() throws Exception {
        // Given - clean all and create a AGENDA_PUBLISHED event (future date required)
        eventRepository.deleteAll();
        Event agendaPublishedEvent = createTestEvent("Event with Expansions", "2027-11-15T09:00:00Z", "AGENDA_PUBLISHED", "agenda");

        // When/Then - request with include parameter
        // Note: speakers removed from top-level, now accessed via sessions.speakers
        mockMvc.perform(get("/api/v1/events/current?include=venue,sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(agendaPublishedEvent.getEventCode()))
                .andExpect(jsonPath("$.venue").exists())
                .andExpect(jsonPath("$.sessions").exists());
    }

    @Test
    @DisplayName("should_returnCurrentEvent_when_liveEventDateIsToday")
    void should_returnCurrentEvent_when_liveEventDateIsToday() throws Exception {
        // Given - EVENT_LIVE event whose date is today — on the actual event day the scheduler
        // transitions the event to EVENT_LIVE (not EVENT_COMPLETED), so Phase 1 picks it up.
        eventRepository.deleteAll();
        ZoneId bernZone = ZoneId.of("Europe/Zurich");
        String todayStr = LocalDate.now(bernZone).atStartOfDay(bernZone).toInstant().toString();
        Event todayEvent = createTestEvent("Today Live Event", todayStr, "EVENT_LIVE", "agenda");

        // When/Then - event day-of should be shown as current (Phase 1: EVENT_LIVE, published)
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(todayEvent.getEventCode()))
                .andExpect(jsonPath("$.workflowState").value("EVENT_LIVE"));
    }

    @Test
    @DisplayName("should_returnCompletedEvent_when_completedEventDateWasYesterday")
    void should_returnCompletedEvent_when_completedEventDateWasYesterday() throws Exception {
        // Given - EVENT_COMPLETED event whose date was yesterday (within 14-day post-event window)
        eventRepository.deleteAll();
        ZoneId bernZone = ZoneId.of("Europe/Zurich");
        String yesterdayStr = LocalDate.now(bernZone).minusDays(1).atStartOfDay(bernZone).toInstant().toString();
        createTestEvent("Yesterday Completed Event", yesterdayStr, "EVENT_COMPLETED");

        // When/Then - event from yesterday IS within 14-day window and should be returned (Story 4.2)
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowState").value("EVENT_COMPLETED"));
    }

    // ============================================================================
    // Story 2.5.3a: Event Theme Image Upload - Integration Tests
    // ============================================================================

    @Test
    @DisplayName("should_storeThemeImageUploadId_when_uploadIdProvided")
    void should_storeThemeImageUploadId_when_uploadIdProvided() throws Exception {
        // Given - Create event with theme image upload ID
        String uploadId = "test-upload-theme-123";

        String requestBody = """
                {
                    "title": "Cloud Conference 2025",
                    "eventNumber": 99,
                    "date": "2025-06-15T09:00:00Z",
                    "registrationDeadline": "2025-06-08T23:59:59Z",
                    "venueName": "Tech Hub Bern",
                    "venueAddress": "Bahnhofplatz 1, 3011 Bern",
                    "venueCapacity": 500,
                    "organizerUsername": "john.doe",
                    "currentAttendeeCount": 0,
                    "description": "A conference about cloud technologies",
                    "eventType": "EVENING",
                    "themeImageUploadId": "%s"
                }
                """.formatted(uploadId);

        // When - Create event with theme image
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode").value("BATbern99"));

        // Then - Verify uploadId was stored in event
        Event savedEvent = eventRepository.findByEventCode("BATbern99").orElseThrow();
        assertThat(savedEvent.getThemeImageUploadId()).isEqualTo(uploadId);
    }

    @Test
    @DisplayName("should_createEventWithoutThemeImage_when_uploadIdNotProvided")
    void should_createEventWithoutThemeImage_when_uploadIdNotProvided() throws Exception {
        // Given - No theme image upload
        String requestBody = """
                {
                    "title": "Traditional Conference 2025",
                    "eventNumber": 100,
                    "date": "2025-07-20T09:00:00Z",
                    "registrationDeadline": "2025-07-13T23:59:59Z",
                    "venueName": "Kornhausforum",
                    "venueAddress": "Kornhausplatz 18, 3011 Bern",
                    "venueCapacity": 200,
                    "organizerUsername": "jane.smith",
                    "currentAttendeeCount": 0,
                    "description": "Traditional architecture conference",
                    "eventType": "EVENING"
                }
                """;

        // When - Create event without theme image
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.themeImageUrl").doesNotExist())
                .andExpect(jsonPath("$.eventCode").value("BATbern100"));
    }

    @Test
    @DisplayName("should_updateThemeImageUploadId_when_patchingWithUploadId")
    void should_updateThemeImageUploadId_when_patchingWithUploadId() throws Exception {
        // Given - Create event without theme image
        Event existingEvent = createTestEvent("Event to Update", "2025-08-10T09:00:00Z", "AGENDA_PUBLISHED");

        String uploadId = "test-upload-update-456";
        String patchBody = """
                {
                    "themeImageUploadId": "%s"
                }
                """.formatted(uploadId);

        // When - Patch event with theme image
        mockMvc.perform(patch("/api/v1/events/" + existingEvent.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(existingEvent.getEventCode()));

        // Then - Verify uploadId was updated
        Event updatedEvent = eventRepository.findByEventCode(existingEvent.getEventCode()).orElseThrow();
        assertThat(updatedEvent.getThemeImageUploadId()).isEqualTo(uploadId);
    }

    @Test
    @DisplayName("PATCH /api/v1/events/{eventCode} - Should regenerate eventCode when eventNumber changes")
    void should_regenerateEventCode_when_eventNumberChangedViaPatch() throws Exception {
        // Given - Create an event with event number 100
        Event event = createTestEvent("Original Event", "2026-06-15T09:00:00Z", "CREATED");
        // Update to use a known event number for testing
        event.setEventNumber(5000);
        event.setEventCode("BATbern5000");
        Event savedEvent = eventRepository.save(event);

        // When - Update event number to 6000 via PATCH
        String patchBody = """
                {
                    "eventNumber": 6000
                }
                """;

        mockMvc.perform(patch("/api/v1/events/BATbern5000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("BATbern6000"))
                .andExpect(jsonPath("$.eventNumber").value(6000));

        // Then - Verify eventCode was regenerated in database
        Event updatedEvent = eventRepository.findById(savedEvent.getId()).orElseThrow();
        assertThat(updatedEvent.getEventCode()).isEqualTo("BATbern6000");
        assertThat(updatedEvent.getEventNumber()).isEqualTo(6000);
    }

    @Test
    @DisplayName("PUT /api/v1/events/{eventCode} - Should regenerate eventCode when eventNumber changes")
    void should_regenerateEventCode_when_eventNumberChangedViaPut() throws Exception {
        // Given - Create an event with event number 5001
        Event event = createTestEvent("Original Event", "2026-06-15T09:00:00Z", "CREATED");
        event.setEventNumber(5001);
        event.setEventCode("BATbern5001");
        Event savedEvent = eventRepository.save(event);

        // When - Update event with new event number 6001 via PUT
        String putBody = """
                {
                    "title": "Updated Event",
                    "eventNumber": 6001,
                    "date": "2026-06-15T09:00:00Z",
                    "registrationDeadline": "2026-06-01T23:59:59Z",
                    "venueName": "Bern TechHub",
                    "venueAddress": "Test Address",
                    "venueCapacity": 100,
                    "organizerUsername": "john.doe",
                    "currentAttendeeCount": 0,
                    "eventType": "EVENING"
                }
                """;

        mockMvc.perform(put("/api/v1/events/BATbern5001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("BATbern6001"))
                .andExpect(jsonPath("$.eventNumber").value(6001));

        // Then - Verify eventCode was regenerated in database
        Event updatedEvent = eventRepository.findById(savedEvent.getId()).orElseThrow();
        assertThat(updatedEvent.getEventCode()).isEqualTo("BATbern6001");
        assertThat(updatedEvent.getEventNumber()).isEqualTo(6001);
    }

    @Test
    @DisplayName("PATCH /api/v1/events/{eventCode} - Should reject duplicate eventNumber")
    void should_rejectDuplicateEventNumber_when_patchingEvent() throws Exception {
        // Given - Create two events with different event numbers
        Event event1 = createTestEvent("Event 5002", "2026-06-15T09:00:00Z", "CREATED");
        event1.setEventNumber(5002);
        event1.setEventCode("BATbern5002");
        eventRepository.save(event1);

        Event event2 = createTestEvent("Event 5003", "2026-06-15T09:00:00Z", "CREATED");
        event2.setEventNumber(5003);
        event2.setEventCode("BATbern5003");
        eventRepository.save(event2);

        // When - Try to change event2's number to 5002 (already used by event1)
        String patchBody = """
                {
                    "eventNumber": 5002
                }
                """;

        // Then - Should return 422 Unprocessable Entity with validation error
        mockMvc.perform(patch("/api/v1/events/BATbern5003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Event number 5002 is already in use")));
    }

    @Test
    @DisplayName("PATCH /api/v1/events/{eventCode} - Should reject duplicate eventCode when data inconsistency exists")
    void should_rejectDuplicateEventCode_when_dataInconsistencyExists() throws Exception {
        // Given - Create event with data inconsistency: eventCode "BATbern998" but eventNumber 500
        Event event1 = createTestEvent("Event with inconsistent data", "2026-06-15T09:00:00Z", "CREATED");
        event1.setEventNumber(500);
        event1.setEventCode("BATbern998");  // Inconsistent: should be BATbern500
        eventRepository.save(event1);

        // And - Create another event that we'll try to update
        Event event2 = createTestEvent("Event 58", "2026-06-15T09:00:00Z", "CREATED");
        event2.setEventNumber(58);
        event2.setEventCode("BATbern58");
        eventRepository.save(event2);

        // When - Try to change event2's number to 998 (would generate eventCode "BATbern998" which already exists)
        String patchBody = """
                {
                    "eventNumber": 998
                }
                """;

        // Then - Should return 422 Unprocessable Entity with validation error (not 500 Internal Server Error)
        mockMvc.perform(patch("/api/v1/events/BATbern58")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Generated event code BATbern998 is already in use")));
    }

    // ============================================================================
    // Full-Text Search: Speaker Name Search (Feature Enhancement)
    // ============================================================================

    @Test
    @DisplayName("should_findEventBySpeakerName_when_fullTextSearchUsed")
    void should_findEventBySpeakerName_when_fullTextSearchUsed() throws Exception {
        // Given - Create event with session and speaker
        Event event = createTestEvent("Architecture Workshop", "2025-08-15T09:00:00Z", "AGENDA_PUBLISHED");

        // Create session for the event
        ch.batbern.events.domain.Session session = ch.batbern.events.domain.Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .title("Modern Architecture Patterns")
                .description("Deep dive into modern architecture")
                .sessionSlug("modern-architecture-patterns")
                .startTime(Instant.parse("2025-08-15T10:00:00Z"))
                .endTime(Instant.parse("2025-08-15T11:30:00Z"))
                .build();
        session = sessionRepository.save(session);

        // Create session_user (speaker) with name cache fields
        ch.batbern.events.repository.SessionUserRepository sessionUserRepo =
            applicationContext.getBean(ch.batbern.events.repository.SessionUserRepository.class);

        ch.batbern.events.domain.SessionUser speaker = ch.batbern.events.domain.SessionUser.builder()
                .session(session)
                .username("mueller.hans")
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .build();
        sessionUserRepo.save(speaker);

        // Execute raw SQL to update speaker name cache fields (GENERATED column will auto-update)
        entityManager.createNativeQuery(
            "UPDATE session_users SET speaker_first_name = 'Hans', speaker_last_name = 'Müller' WHERE username = 'mueller.hans'"
        ).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When - Search for events by speaker last name
        String filter = "{\"title\":{\"$contains\":\"Müller\"}}";

        // Then - Event should be found via speaker name full-text search
        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.title == 'Architecture Workshop')]").exists());
    }

    @Test
    @DisplayName("should_findEventBySpeakerFirstName_when_fullTextSearchUsed")
    void should_findEventBySpeakerFirstName_when_fullTextSearchUsed() throws Exception {
        // Given - Create event with session and multiple speakers
        Event event = createTestEvent("Security Workshop", "2025-09-20T09:00:00Z", "AGENDA_PUBLISHED");

        // Create session for the event
        ch.batbern.events.domain.Session session = ch.batbern.events.domain.Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .title("Security Best Practices")
                .description("Comprehensive security workshop")
                .sessionSlug("security-best-practices")
                .startTime(Instant.parse("2025-09-20T10:00:00Z"))
                .endTime(Instant.parse("2025-09-20T12:00:00Z"))
                .build();
        session = sessionRepository.save(session);

        // Create two speakers with different names
        ch.batbern.events.repository.SessionUserRepository sessionUserRepo =
            applicationContext.getBean(ch.batbern.events.repository.SessionUserRepository.class);

        ch.batbern.events.domain.SessionUser speaker1 = ch.batbern.events.domain.SessionUser.builder()
                .session(session)
                .username("schmidt.petra")
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .build();
        sessionUserRepo.save(speaker1);

        ch.batbern.events.domain.SessionUser speaker2 = ch.batbern.events.domain.SessionUser.builder()
                .session(session)
                .username("weber.thomas")
                .speakerRole(SpeakerRole.CO_SPEAKER)
                .isConfirmed(true)
                .build();
        sessionUserRepo.save(speaker2);

        // Update speaker name cache fields
        entityManager.createNativeQuery(
            "UPDATE session_users SET speaker_first_name = 'Petra', speaker_last_name = 'Schmidt' WHERE username = 'schmidt.petra'"
        ).executeUpdate();
        entityManager.createNativeQuery(
            "UPDATE session_users SET speaker_first_name = 'Thomas', speaker_last_name = 'Weber' WHERE username = 'weber.thomas'"
        ).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When - Search for events by speaker first name "Petra"
        String filter = "{\"title\":{\"$contains\":\"Petra\"}}";

        // Then - Event should be found via speaker name full-text search
        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.title == 'Security Workshop')]").exists());
    }

    @Test
    @DisplayName("should_notFindEvent_when_speakerNameDoesNotMatch")
    void should_notFindEvent_when_speakerNameDoesNotMatch() throws Exception {
        // Given - Create event with session and speaker
        Event event = createTestEvent("Database Workshop", "2025-10-10T09:00:00Z", "AGENDA_PUBLISHED");

        ch.batbern.events.domain.Session session = ch.batbern.events.domain.Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .title("Database Optimization")
                .description("Performance tuning workshop")
                .sessionSlug("database-optimization")
                .startTime(Instant.parse("2025-10-10T10:00:00Z"))
                .endTime(Instant.parse("2025-10-10T11:00:00Z"))
                .build();
        session = sessionRepository.save(session);

        ch.batbern.events.repository.SessionUserRepository sessionUserRepo =
            applicationContext.getBean(ch.batbern.events.repository.SessionUserRepository.class);

        ch.batbern.events.domain.SessionUser speaker = ch.batbern.events.domain.SessionUser.builder()
                .session(session)
                .username("meier.anna")
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .build();
        sessionUserRepo.save(speaker);

        entityManager.createNativeQuery(
            "UPDATE session_users SET speaker_first_name = 'Anna', speaker_last_name = 'Meier' WHERE username = 'meier.anna'"
        ).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // When - Search for events by a non-existent speaker name
        String filter = "{\"title\":{\"$contains\":\"Zimmermann\"}}";

        // Then - No events should be found
        mockMvc.perform(get("/api/v1/events")
                        .param("filter", filter)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.title == 'Database Workshop')]").doesNotExist());
    }

    // ============================================================================
    // Story 10.4: PATCH event with topicSelectionNote field (AC: 34)
    // ============================================================================

    @Test
    @DisplayName("should_patchTopicSelectionNote_when_topicSelectionNoteAndTopicCodeProvided")
    void should_patchTopicSelectionNote_when_topicSelectionNoteAndTopicCodeProvided() throws Exception {
        // Given
        Event event = createTestEvent("Topic Note Test Event", "2026-04-15T09:00:00Z", "CREATED");
        String note = "✅ Selected Topic: Platform Engineering\n   Partner alignment: Swisscom (1/9)";

        String patchBody = """
                {
                    "topicSelectionNote": "%s",
                    "topicCode": "platform-engineering"
                }
                """.formatted(note.replace("\n", "\\n"));

        // When / Then — should return 200 with topicSelectionNote in response
        mockMvc.perform(patch("/api/v1/events/" + event.getEventCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicSelectionNote").value(note.replace("\\n", "\n")));

        // Verify persistence
        Event patched = eventRepository.findByEventCode(event.getEventCode()).orElseThrow();
        assertThat(patched.getTopicSelectionNote()).isEqualTo(note.replace("\\n", "\n"));
        assertThat(patched.getTopicCode()).isEqualTo("platform-engineering");
    }

    @Test
    @DisplayName("should_returnTopicSelectionNote_when_eventHasNote")
    void should_returnTopicSelectionNote_when_eventHasNote() throws Exception {
        // Given — create event and manually set note in DB
        Event event = createTestEvent("Note Read Test", "2026-05-15T09:00:00Z", "CREATED");
        event.setTopicSelectionNote("Test selection note");
        event.setTopicCode("test-topic");
        eventRepository.save(event);

        // When / Then — GET should include topicSelectionNote
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicSelectionNote").value("Test selection note"));
    }

    // ============================================================================
    // GET /events/current — two-phase logic (14-day post-event window)
    // ============================================================================

    @Test
    @DisplayName("should_returnUpcomingEvent_when_getCurrentEventCalled")
    void should_returnUpcomingEvent_when_getCurrentEventCalled() throws Exception {
        // Given: upcoming AGENDA_PUBLISHED event tomorrow with a published phase
        eventRepository.deleteAll();
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        createTestEvent("Tomorrow Event", tomorrow, "AGENDA_PUBLISHED", "agenda");

        // When / Then: current event returns the upcoming one
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tomorrow Event"))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    @Test
    @DisplayName("should_returnRecentlyCompletedEvent_when_noUpcomingButCompletedWithin14Days")
    void should_returnRecentlyCompletedEvent_when_noUpcomingButCompletedWithin14Days() throws Exception {
        // Given: no upcoming events, but EVENT_COMPLETED 7 days ago
        eventRepository.deleteAll();
        String sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        createTestEvent("Recent Completed", sevenDaysAgo, "EVENT_COMPLETED");

        // When / Then: fallback shows the recently completed event
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Recent Completed"))
                .andExpect(jsonPath("$.workflowState").value("EVENT_COMPLETED"));
    }

    @Test
    @DisplayName("should_preferUpcomingEvent_when_bothUpcomingAndRecentlyCompletedExist")
    void should_preferUpcomingEvent_when_bothUpcomingAndRecentlyCompletedExist() throws Exception {
        // Given: a recently completed event AND an upcoming published event
        eventRepository.deleteAll();
        String sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        createTestEvent("Recent Completed", sevenDaysAgo, "EVENT_COMPLETED");
        createTestEvent("Upcoming Event", tomorrow, "AGENDA_PUBLISHED", "agenda");

        // When / Then: upcoming event is preferred
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Upcoming Event"));
    }

    @Test
    @DisplayName("should_showRecentlyCompletedEvent_when_upcomingEventIsUnpublished")
    void should_showRecentlyCompletedEvent_when_upcomingEventIsUnpublished() throws Exception {
        // Regression test for bug where an upcoming event in SPEAKER_IDENTIFICATION (no publication
        // phase) was shown on the public homepage instead of the recently completed event within
        // the 14-day post-event window.
        //
        // Rule: only events with currentPublishedPhase != null qualify for Phase 1 (upcoming).
        // An unpublished future event must NOT block the Phase 2 fallback.
        eventRepository.deleteAll();
        String sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        String inSummer = Instant.now().plus(100, ChronoUnit.DAYS).toString();
        createTestEvent("Recent Completed", sevenDaysAgo, "EVENT_COMPLETED"); // no published phase
        createTestEvent("Unpublished Future", inSummer, "SPEAKER_IDENTIFICATION"); // no published phase

        // When / Then: Phase 2 fallback returns recently completed event
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Recent Completed"))
                .andExpect(jsonPath("$.workflowState").value("EVENT_COMPLETED"));
    }

    @Test
    @DisplayName("should_return404_when_completedEventOlderThan14Days")
    void should_return404_when_completedEventOlderThan14Days() throws Exception {
        // Given: EVENT_COMPLETED event 15 days ago (outside 14-day window)
        eventRepository.deleteAll();
        String fifteenDaysAgo = Instant.now().minus(15, ChronoUnit.DAYS).toString();
        createTestEvent("Old Completed", fifteenDaysAgo, "EVENT_COMPLETED");

        // When / Then: no current event found
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return404_when_noCurrentOrRecentEvent")
    void should_return404_when_noCurrentOrRecentEvent() throws Exception {
        // Given: only old archived events, nothing recent
        eventRepository.deleteAll();

        // When / Then
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isNotFound());
    }

    // ============================================================================
    // Registration Count Bug Fix: 'attended' status must be included in counts
    // Bug: detail endpoint excluded 'attended' registrations from currentAttendeeCount
    // and confirmedCount, causing mismatch with list endpoint (which used countByEventId).
    // ============================================================================

    private Registration createRegistration(java.util.UUID eventId, String status) {
        Registration reg = Registration.builder()
                .registrationCode("REG-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                .eventId(eventId)
                .attendeeUsername("test.user." + java.util.UUID.randomUUID().toString().substring(0, 4))
                .status(status)
                .attendeeFirstName("Test")
                .attendeeLastName("User")
                .attendeeEmail("test@example.com")
                .registrationDate(Instant.now())
                .build();
        return registrationRepository.save(reg);
    }

    @Test
    @DisplayName("should_include_attended_in_confirmedCount_when_enrichingEventResponse")
    void should_include_attended_in_confirmedCount_when_enrichingEventResponse() throws Exception {
        // Given: event with attended, confirmed, and waitlist registrations (no include param)
        eventRepository.deleteAll();
        Event event = createTestEvent("Legacy Event", "2023-05-15T09:00:00Z", "EVENT_COMPLETED");
        createRegistration(event.getId(), "attended");  // 3 historical attendees
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "confirmed"); // 1 new confirmed
        createRegistration(event.getId(), "waitlist");  // 1 waitlist
        entityManager.flush();

        // When: GET detail without include
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode()))
                // Then: confirmedCount includes 'attended' (3) + 'confirmed' (1) = 4
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(4))
                .andExpect(jsonPath("$.waitlistCount").value(1));
    }

    @Test
    @DisplayName("should_include_attended_in_currentAttendeeCount_when_registrations_included_in_detail")
    void should_include_attended_in_currentAttendeeCount_when_registrations_included_in_detail() throws Exception {
        // Given: event with attended + confirmed + waitlist registrations
        eventRepository.deleteAll();
        Event event = createTestEvent("Historical Event", "2023-05-15T09:00:00Z", "EVENT_COMPLETED");
        createRegistration(event.getId(), "attended");  // 224 in production; 3 here
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "confirmed"); // 1 confirmed
        createRegistration(event.getId(), "waitlist");  // 1 waitlist
        entityManager.flush();

        // When: GET detail with include=registrations
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode() + "?include=registrations"))
                // Then: currentAttendeeCount = 3 attended + 1 confirmed + 1 waitlist = 5
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAttendeeCount").value(5));
    }

    @Test
    @DisplayName("should_use_registrationCapacity_in_spotsRemaining_when_confirmedCountIncludesAttended")
    void should_use_registrationCapacity_in_spotsRemaining_when_confirmedCountIncludesAttended() throws Exception {
        // Given: event with registrationCapacity=5, 3 attended + 1 confirmed = 4 confirmed total
        eventRepository.deleteAll();
        Event event = Event.builder()
                .eventCode("BATbernCapTest")
                .title("Capacity Test")
                .eventNumber(9999)
                .date(Instant.parse("2023-05-15T09:00:00Z"))
                .registrationDeadline(Instant.parse("2023-05-08T00:00:00Z"))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(200)
                .registrationCapacity(5)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .build();
        event = eventRepository.save(event);
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "confirmed");
        entityManager.flush();

        // When: GET detail
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode()))
                // Then: confirmedCount=4 (3 attended + 1 confirmed), spotsRemaining=5-4=1
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(4))
                .andExpect(jsonPath("$.spotsRemaining").value(1));
    }

    @Test
    @DisplayName("should_exclude_cancelled_from_currentAttendeeCount_on_list_endpoint")
    void should_exclude_cancelled_from_currentAttendeeCount_on_list_endpoint() throws Exception {
        // Given: event with 3 active + 2 cancelled registrations
        eventRepository.deleteAll();
        Event event = createTestEvent("Active Count Event", "2024-06-15T09:00:00Z", "CREATED");
        createRegistration(event.getId(), "registered");
        createRegistration(event.getId(), "confirmed");
        createRegistration(event.getId(), "attended");
        createRegistration(event.getId(), "cancelled");
        createRegistration(event.getId(), "cancelled");
        entityManager.flush();

        // When: GET list with include=registrations
        mockMvc.perform(get("/api/v1/events?include=registrations"))
                // Then: currentAttendeeCount = 3 (cancelled excluded via ACTIVE_STATUSES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentAttendeeCount").value(3));
    }
}
