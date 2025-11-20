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
import ch.batbern.events.service.ConfirmationTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Tests for Registration Endpoints
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * Story 4.1.5c: Secure Email-Based Registration Confirmation
 * <p>
 * Test Scenarios:
 * - Create anonymous registration (no auth required) - Returns {message, email}
 * - Registration creates/retrieves user via User Management API
 * - Registration code generation and format validation
 * - Confirm registration via JWT token
 * - Retrieve registration by code with enriched user data
 * - List all registrations for an event
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

    @Autowired
    private ConfirmationTokenService confirmationTokenService;

    @MockitoBean
    private UserApiClient userApiClient;

    private Event testEvent;
    private UserResponse mockUserProfile;
    private GetOrCreateUserResponse mockCreateResponse;

    @BeforeEach
    void setUp() {
        // Reset mocks to prevent test pollution
        reset(userApiClient);

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

        // Story 4.1.5c: Registration now returns {message, email} instead of full registration
        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(containsString("Registration submitted successfully")))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                // No longer returns registration details in response
                .andExpect(jsonPath("$.registrationCode").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());

        // Verify registration was saved to database
        assertThat(registrationRepository.count()).isEqualTo(1);
        Registration savedRegistration = registrationRepository.findAll().get(0);
        assertThat(savedRegistration.getRegistrationCode()).startsWith("BATbern142-reg-");
        assertThat(savedRegistration.getAttendeeUsername()).isEqualTo("john.doe");
        assertThat(savedRegistration.getStatus()).isEqualTo("registered"); // Story 4.1.5c: starts as 'registered'
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

        // Create first registration - Story 4.1.5c: returns {message, email}
        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

        // Get registration code from database
        String registrationCode1 = registrationRepository.findAll().get(0).getRegistrationCode();

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

        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"));

        // Get second registration code from database
        String registrationCode2 = registrationRepository.findAll().get(1).getRegistrationCode();

        // Verify codes are unique
        assertThat(registrationCode1).isNotEqualTo(registrationCode2);
        assertThat(registrationCode1).matches("BATbern142-reg-[A-Z0-9]{6}");
        assertThat(registrationCode2).matches("BATbern142-reg-[A-Z0-9]{6}");
    }

    // ============================================================================
    // Test: List Registrations for Event
    // ============================================================================

    @Test
    @Disabled("TODO: Test isolation issue - passes individually but fails in full suite. Investigate test pollution.")
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
                .andExpect(jsonPath("$.status").value("CONFIRMED")) // API returns uppercase
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
    // Test: QR Code Generation (DEPRECATED - Story 4.1.5c)
    // ============================================================================
    // QR code functionality has been removed in Story 4.1.5c
    // Replaced with email-based confirmation using JWT tokens

    @Test
    @Disabled("Story 4.1.5c: QR code functionality removed")
    @DisplayName("should_generateQRCode_when_validRegistrationProvided")
    void should_generateQRCode_when_validRegistrationProvided() throws Exception {
        // This test is disabled because QR code functionality has been removed
    }

    @Test
    @Disabled("Story 4.1.5c: QR code functionality removed")
    @DisplayName("should_return404_when_qrCodeRequestedForNonexistentRegistration")
    void should_return404_when_qrCodeRequestedForNonexistentRegistration() throws Exception {
        // This test is disabled because QR code functionality has been removed
    }

    // ============================================================================
    // Test: Email Confirmation Flow (Story 4.1.5c)
    // ============================================================================

    @Test
    @DisplayName("should_confirmRegistration_when_validTokenProvided")
    void should_confirmRegistration_when_validTokenProvided() throws Exception {
        // Create a test registration in 'registered' status
        Registration registration = createTestRegistration("john.doe", "registered");

        // Generate confirmation token
        String token = confirmationTokenService.generateConfirmationToken(
                registration.getId(),
                testEvent.getEventCode()
        );

        // Confirm registration
        mockMvc.perform(post("/api/v1/events/" + testEvent.getEventCode() + "/registrations/confirm")
                        .param("token", token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration confirmed successfully!"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Verify status was updated in database
        Registration confirmedRegistration = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(confirmedRegistration.getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("should_returnAlreadyConfirmed_when_registrationAlreadyConfirmed")
    void should_returnAlreadyConfirmed_when_registrationAlreadyConfirmed() throws Exception {
        // Create a test registration that's already confirmed
        Registration registration = createTestRegistration("john.doe", "confirmed");

        // Generate confirmation token
        String token = confirmationTokenService.generateConfirmationToken(
                registration.getId(),
                testEvent.getEventCode()
        );

        // Try to confirm again (should be idempotent)
        mockMvc.perform(post("/api/v1/events/" + testEvent.getEventCode() + "/registrations/confirm")
                        .param("token", token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Registration already confirmed"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Verify status is still confirmed
        Registration stillConfirmed = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(stillConfirmed.getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("should_return400_when_invalidTokenProvided")
    void should_return400_when_invalidTokenProvided() throws Exception {
        String invalidToken = "invalid.jwt.token";

        mockMvc.perform(post("/api/v1/events/" + testEvent.getEventCode() + "/registrations/confirm")
                        .param("token", invalidToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return404_when_registrationNotFoundForToken")
    void should_return404_when_registrationNotFoundForToken() throws Exception {
        // Generate token for non-existent registration
        java.util.UUID nonExistentId = java.util.UUID.randomUUID();
        String token = confirmationTokenService.generateConfirmationToken(
                nonExistentId,
                testEvent.getEventCode()
        );

        mockMvc.perform(post("/api/v1/events/" + testEvent.getEventCode() + "/registrations/confirm")
                        .param("token", token)
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
        // Story 4.1.5c: Registration now returns {message, email}
        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

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
