package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.BatchRegistrationItem;
import ch.batbern.events.dto.generated.BatchRegistrationItem.StatusEnum;
import ch.batbern.events.dto.generated.BatchRegistrationRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for Batch Registration API (Story BAT-14).
 *
 * Tests validate the API contract defined in events-api.openapi.yml:
 * - Request/response schemas match OpenAPI spec
 * - Validation rules enforced
 * - HTTP status codes correct
 * - Error responses follow standard format
 *
 * TDD: Written BEFORE implementation (RED phase)
 */
@Transactional
class BatchRegistrationContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @MockBean
    private UserApiClient userApiClient;

    private Event testEvent1;
    private Event testEvent2;

    @BeforeEach
    void setup() {
        // Clean up test data
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Mock UserApiClient for all tests
        UserResponse userProfile = new UserResponse();
        userProfile.setId("test.user");
        userProfile.setFirstName("Test");
        userProfile.setLastName("User");
        userProfile.setEmail("test@example.com");

        GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
        userResponse.setUsername("test.user");
        userResponse.setCreated(false);
        userResponse.setUser(userProfile);

        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class)))
            .thenReturn(userResponse);

        // Create test events
        Instant now = Instant.now();
        Instant eventDate = now.plus(30, ChronoUnit.DAYS);
        Instant regDeadline = now.plus(20, ChronoUnit.DAYS);

        testEvent1 = Event.builder()
            .eventCode("BATbern25")
            .eventNumber(25)
            .title("BATbern Event 25")
            .date(eventDate)
            .registrationDeadline(regDeadline)
            .venueName("Kornhausforum")
            .venueAddress("Kornhausplatz 18, 3011 Bern")
            .venueCapacity(200)
            .organizerUsername("john.doe")
            .workflowState(EventWorkflowState.CREATED)
            .eventType(EventType.EVENING)
            .currentAttendeeCount(0)
            .build();

        testEvent2 = Event.builder()
            .eventCode("BATbern31")
            .eventNumber(31)
            .title("BATbern Event 31")
            .date(eventDate.plus(30, ChronoUnit.DAYS))
            .registrationDeadline(regDeadline.plus(30, ChronoUnit.DAYS))
            .venueName("Kornhausforum")
            .venueAddress("Kornhausplatz 18, 3011 Bern")
            .venueCapacity(200)
            .organizerUsername("john.doe")
            .workflowState(EventWorkflowState.CREATED)
            .eventType(EventType.EVENING)
            .currentAttendeeCount(0)
            .build();

        eventRepository.saveAll(List.of(testEvent1, testEvent2));
    }

    /**
     * AC1: Request validation matches API contract
     * TDD RED: This test will FAIL until we implement the endpoint
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_acceptValidRequest_when_allFieldsProvided() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED)
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    /**
     * AC3: Response schema matches API contract
     * TDD RED: This test will FAIL until we implement the endpoint
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return200_when_batchRegistrationSucceeds() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("adrian.buerki@centrisag.ch")
            .firstName("Adrian")
            .lastName("Bürki")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED),
                new BatchRegistrationItem().eventCode("BATbern31").status(StatusEnum.ATTENDED)
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username", notNullValue()))
            .andExpect(jsonPath("$.totalRegistrations", is(2)))
            .andExpect(jsonPath("$.successfulRegistrations", is(2)))
            .andExpect(jsonPath("$.failedRegistrations", hasSize(0)))
            .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    /**
     * AC4: Partial success handled correctly
     * TDD RED: This test will FAIL until we implement partial success logic
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnPartialSuccess_when_someRegistrationsFail() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED),
                new BatchRegistrationItem().eventCode("BATbern99").status(StatusEnum.ATTENDED) // Missing event
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRegistrations", is(2)))
            .andExpect(jsonPath("$.successfulRegistrations", is(1)))
            .andExpect(jsonPath("$.failedRegistrations", hasSize(1)))
            .andExpect(jsonPath("$.failedRegistrations[0].eventCode", is("BATbern99")))
            .andExpect(jsonPath("$.failedRegistrations[0].reason", containsString("Event not found")));
    }

    /**
     * AC1: Request validation - invalid email
     * TDD RED: This test will FAIL until we add validation
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_invalidEmail() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("invalid-email") // Invalid email
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED)
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    /**
     * AC1: Request validation - empty registrations array
     * TDD RED: This test will FAIL until we add validation
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_emptyRegistrationsArray() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of()); // Empty array

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    /**
     * AC1: Request validation - too many registrations (>100)
     * TDD RED: This test will FAIL until we add validation
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_tooManyRegistrations() throws Exception {
        // Arrange - create 101 registrations (exceeds maxItems: 100)
        List<BatchRegistrationItem> tooManyRegistrations = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooManyRegistrations.add(new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED));
        }

        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(tooManyRegistrations);

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    /**
     * AC1: Authentication - requires ORGANIZER role
     * TDD RED: This test will FAIL until we add role authorization
     */
    @Test
    @WithMockUser(roles = "ATTENDEE") // Wrong role
    void should_return403_when_notOrganizerRole() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED)
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    /**
     * AC1: Authentication - requires authentication
     * Note: With @PreAuthorize, Spring Security returns 403 Forbidden for unauthenticated requests
     */
    @Test
    void should_return403_when_notAuthenticated() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status(StatusEnum.ATTENDED)
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }
}
