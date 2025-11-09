package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for Registration Endpoints
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * Test Scenarios:
 * - Create anonymous registration (no auth required)
 * - Registration creates/retrieves user via User Management API
 * - Registration code generation and format validation
 * - Retrieve registration by code with enriched user data
 * - List all registrations for an event
 * - Generate QR code for registration
 * <p>
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class RegistrationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserApiClient userApiClient;

    private Event testEvent;
    private UserResponse mockUserProfile;
    private GetOrCreateUserResponse mockCreateResponse;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        testEvent = Event.builder()
                .eventCode("BATbern142")
                .title("BATbern 2025 Test")
                .eventNumber(142)
                .date(Instant.parse("2025-06-15T09:00:00Z"))
                .registrationDeadline(Instant.parse("2025-06-08T23:59:59Z"))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .status("published")
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .description("Test event for registration")
                .build();
        testEvent = eventRepository.save(testEvent);

        // Mock UserApiClient response (simulates User Management Service)
        mockUserProfile = new UserResponse()
                .id("john.doe")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .companyId("Test Company");

        mockCreateResponse = new GetOrCreateUserResponse()
                .username("john.doe")
                .created(true)
                .user(mockUserProfile);

        when(userApiClient.getOrCreateUser(any())).thenReturn(mockCreateResponse);

        // Dynamic mock for getUserByUsername - handles any username
        when(userApiClient.getUserByUsername(any(String.class)))
                .thenAnswer(invocation -> {
                    String username = invocation.getArgument(0);
                    String[] parts = username.split("\\.");
                    String firstName = parts.length > 0 ? capitalize(parts[0]) : "Test";
                    String lastName = parts.length > 1 ? capitalize(parts[1]) : "User";

                    return new UserResponse()
                            .id(username)
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(username + "@example.com")
                            .companyId("Test Company")
                            ;
                });
    }

    // ============================================================================
    // Test: Create Anonymous Registration (ADR-005)
    // ============================================================================

    @Test
    @DisplayName("should_createRegistration_when_validDataProvided")
    void should_createRegistration_when_validDataProvided() throws Exception {
        String requestJson = """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "john.doe@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationCode").exists())
                .andExpect(jsonPath("$.registrationCode").value(startsWith("BATbern142-reg-")))
                .andExpect(jsonPath("$.eventCode").value("BATbern142"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.attendeeUsername").value("john.doe"))
                .andExpect(jsonPath("$.attendeeFirstName").value("John"))
                .andExpect(jsonPath("$.attendeeLastName").value("Doe"))
                .andExpect(jsonPath("$.attendeeEmail").value("john.doe@example.com"))
                .andExpect(jsonPath("$.attendeeCompany").value("Test Company"))
                .andExpect(jsonPath("$.registrationDate").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // Verify registration was saved to database
        assertThat(registrationRepository.count()).isEqualTo(1);
        Registration savedRegistration = registrationRepository.findAll().get(0);
        assertThat(savedRegistration.getRegistrationCode()).startsWith("BATbern142-reg-");
        assertThat(savedRegistration.getAttendeeUsername()).isEqualTo("john.doe");
        assertThat(savedRegistration.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("should_return400_when_invalidDataProvided")
    void should_return400_when_invalidDataProvided() throws Exception {
        String requestJson = """
                {
                    "firstName": "",
                    "lastName": "Doe",
                    "email": "invalid-email",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return404_when_eventNotFound")
    void should_return404_when_eventNotFound() throws Exception {
        String requestJson = """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "john.doe@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/NONEXISTENT/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }

    // ============================================================================
    // Test: Registration Code Format Validation (ADR-003)
    // ============================================================================

    @Test
    @DisplayName("should_generateUniqueRegistrationCode_when_multipleRegistrationsCreated")
    void should_generateUniqueRegistrationCode_when_multipleRegistrationsCreated() throws Exception {
        String requestJson = """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "john.doe@example.com",
                    "termsAccepted": true
                }
                """;

        // Create first registration
        String response1 = mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String registrationCode1 = objectMapper.readTree(response1).get("registrationCode").asText();

        // Create second registration with different email
        UserResponse janeProfile = new UserResponse()
                .id("jane.smith")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .companyId("Test Company");

        GetOrCreateUserResponse janeCreateResponse = new GetOrCreateUserResponse()
                .username("jane.smith")
                .created(true)
                .user(janeProfile);

        when(userApiClient.getOrCreateUser(any())).thenReturn(janeCreateResponse);
        when(userApiClient.getUserByUsername("jane.smith")).thenReturn(janeProfile);

        String requestJson2 = """
                {
                    "firstName": "Jane",
                    "lastName": "Smith",
                    "email": "jane.smith@example.com",
                    "termsAccepted": true
                }
                """;

        String response2 = mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String registrationCode2 = objectMapper.readTree(response2).get("registrationCode").asText();

        // Verify codes are unique
        assertThat(registrationCode1).isNotEqualTo(registrationCode2);
        assertThat(registrationCode1).matches("BATbern142-reg-[A-Z0-9]{6}");
        assertThat(registrationCode2).matches("BATbern142-reg-[A-Z0-9]{6}");
    }

    // ============================================================================
    // Test: List Registrations for Event
    // ============================================================================

    @Test
    @DisplayName("should_listRegistrations_when_eventHasRegistrations")
    void should_listRegistrations_when_eventHasRegistrations() throws Exception {
        // Create test registrations
        createTestRegistration("john.doe", "confirmed");
        createTestRegistration("jane.smith", "registered");

        mockMvc.perform(get("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].registrationCode").exists())
                .andExpect(jsonPath("$[0].eventCode").value("BATbern142"))
                .andExpect(jsonPath("$[0].attendeeUsername").exists())
                .andExpect(jsonPath("$[1].registrationCode").exists());
    }

    @Test
    @DisplayName("should_returnEmptyList_when_eventHasNoRegistrations")
    void should_returnEmptyList_when_eventHasNoRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ============================================================================
    // Test: Get Registration Detail with User Enrichment (ADR-004)
    // ============================================================================

    @Test
    @DisplayName("should_getRegistration_when_validCodeProvided")
    void should_getRegistration_when_validCodeProvided() throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");

        mockMvc.perform(get("/api/v1/events/BATbern142/registrations/" + registration.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCode").value(registration.getRegistrationCode()))
                .andExpect(jsonPath("$.eventCode").value("BATbern142"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.attendeeUsername").value("john.doe"))
                .andExpect(jsonPath("$.attendeeFirstName").value("John"))
                .andExpect(jsonPath("$.attendeeLastName").value("Doe"))
                .andExpect(jsonPath("$.attendeeEmail").value("john.doe@example.com"))
                .andExpect(jsonPath("$.attendeeCompany").value("Test Company"));
    }

    @Test
    @DisplayName("should_return404_when_registrationNotFound")
    void should_return404_when_registrationNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern142/registrations/NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return404_when_registrationBelongsToDifferentEvent")
    void should_return404_when_registrationBelongsToDifferentEvent() throws Exception {
        // Create another event
        Event otherEvent = Event.builder()
                .eventCode("BATbern143")
                .title("Different Event")
                .eventNumber(143)
                .date(Instant.parse("2025-07-15T09:00:00Z"))
                .registrationDeadline(Instant.parse("2025-07-08T23:59:59Z"))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .status("published")
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .build();
        otherEvent = eventRepository.save(otherEvent);

        // Create registration for BATbern142
        Registration registration = createTestRegistration("john.doe", "confirmed");

        // Try to access it via BATbern143
        mockMvc.perform(get("/api/v1/events/BATbern143/registrations/" + registration.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ============================================================================
    // Test: QR Code Generation
    // ============================================================================

    @Test
    @DisplayName("should_generateQRCode_when_validRegistrationProvided")
    void should_generateQRCode_when_validRegistrationProvided() throws Exception {
        Registration registration = createTestRegistration("john.doe", "confirmed");

        byte[] qrCodeBytes = mockMvc.perform(get("/api/v1/events/BATbern142/registrations/" + registration.getRegistrationCode() + "/qr")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", containsString("registration-" + registration.getRegistrationCode())))
                .andReturn().getResponse().getContentAsByteArray();

        // Verify QR code was generated
        assertThat(qrCodeBytes).isNotNull();
        assertThat(qrCodeBytes.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("should_return404_when_qrCodeRequestedForNonexistentRegistration")
    void should_return404_when_qrCodeRequestedForNonexistentRegistration() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern142/registrations/NONEXISTENT/qr")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("QA Fix (VALID-001): should_return500_when_duplicateRegistrationAttempted")
    void should_return500_when_duplicateRegistrationAttempted() throws Exception {
        // Given: A user who already has a registration for this event
        when(userApiClient.getOrCreateUser(any())).thenReturn(mockCreateResponse);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserProfile);

        String registrationRequest = """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "john.doe@example.com",
                    "termsAccepted": true
                }
                """;

        // When: First registration attempt (should succeed)
        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendeeUsername").value("john.doe"));

        // Then: Second registration attempt with same user should fail with 409 Conflict
        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already registered")));

        // Verify only one registration exists
        assertThat(registrationRepository.count()).isEqualTo(1);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private Registration createTestRegistration(String attendeeUsername, String status) {
        Registration registration = Registration.builder()
                .registrationCode(testEvent.getEventCode() + "-reg-" + generateRandomCode())
                .eventId(testEvent.getId())
                .attendeeUsername(attendeeUsername)
                .status(status)
                .registrationDate(Instant.parse("2025-06-01T10:00:00Z"))
                .build();

        // No need to mock getUserByUsername - handled by dynamic mock in setUp()
        return registrationRepository.save(registration);
    }

    private String generateRandomCode() {
        return "TEST" + System.currentTimeMillis() % 100000;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
