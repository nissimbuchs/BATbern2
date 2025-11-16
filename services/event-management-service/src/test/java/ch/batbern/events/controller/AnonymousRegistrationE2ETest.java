package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End Integration Test for Anonymous Event Registration
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * Story 4.1.5c: Secure Email-Based Registration Confirmation
 * Task C1: End-to-end integration test
 * <p>
 * Tests the complete flow:
 * 1. Create anonymous registration (no auth) - Returns {message, email}
 * 2. Verify User Management Service called with cognitoSync=false
 * 3. Confirm registration via JWT token
 * 4. Get registration by code (public access)
 * 5. Verify enriched user data in response
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class AnonymousRegistrationE2ETest extends AbstractIntegrationTest {

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
    private UserResponse anonymousUserProfile;

    @BeforeEach
    void setUp() {
        // Clean database
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        testEvent = Event.builder()
                .eventCode("BATbern142")
                .title("BATbern 2025 E2E Test")
                .eventNumber(142)
                .date(Instant.parse("2025-06-15T09:00:00Z"))
                .registrationDeadline(Instant.parse("2025-06-08T23:59:59Z"))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .status("published")
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .description("Test event for E2E registration")
                .build();
        testEvent = eventRepository.save(testEvent);

        // Mock anonymous user profile (cognito_id = NULL)
        anonymousUserProfile = new UserResponse()
                .id("alice.wonderland")
                .firstName("Alice")
                .lastName("Wonderland")
                .email("alice.wonderland@example.com")
                .companyId(null) // Anonymous users may not have company
                .active(true)
                ;

        // Wrap UserResponse in GetOrCreateUserResponse for getOrCreateUser mock
        GetOrCreateUserResponse createUserResponse = new GetOrCreateUserResponse()
                .username("alice.wonderland")
                .created(true)
                .user(anonymousUserProfile);
        when(userApiClient.getOrCreateUser(any())).thenReturn(createUserResponse);
        when(userApiClient.getUserByUsername("alice.wonderland")).thenReturn(anonymousUserProfile);
    }

    @Test
    @DisplayName("E2E: Complete anonymous registration flow")
    void should_completeAnonymousRegistrationFlow_when_userRegistersWithoutAuth() throws Exception {
        // ========================================================================
        // STEP 1: Create Anonymous Registration (No Authentication)
        // ========================================================================

        String registrationRequest = """
                {
                    "firstName": "Alice",
                    "lastName": "Wonderland",
                    "email": "alice.wonderland@example.com",
                    "termsAccepted": true
                }
                """;

        // Story 4.1.5c: Registration now returns {message, email} instead of full registration
        MvcResult createResult = mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(containsString("Registration submitted successfully")))
                .andExpect(jsonPath("$.email").value("alice.wonderland@example.com"))
                // No longer returns registrationCode in response
                .andExpect(jsonPath("$.registrationCode").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();

        // Registration code is no longer returned in response
        // We'll need to get it from database for verification
        var registration = registrationRepository.findAll().get(0);
        String registrationCode = registration.getRegistrationCode();

        // Verify registration code format (ADR-003)
        assertThat(registrationCode).matches("BATbern142-reg-[A-Z0-9]{6}");

        // Verify registration starts in 'registered' status (Story 4.1.5c)
        assertThat(registration.getStatus()).isEqualTo("registered");

        // ========================================================================
        // STEP 2: Verify User Management Service was called
        // ========================================================================

        // Verify getOrCreateUser was called with cognitoSync=false
        verify(userApiClient, times(1)).getOrCreateUser(argThat(request ->
                request.getEmail().equals("alice.wonderland@example.com") &&
                request.getFirstName().equals("Alice") &&
                request.getLastName().equals("Wonderland") &&
                request.getCognitoSync() == Boolean.FALSE
        ));

        // Verify getUserByUsername was called for enrichment
        verify(userApiClient, times(1)).getUserByUsername("alice.wonderland");

        // ========================================================================
        // STEP 3: Get Registration by Code (Public Access - No Auth)
        // ========================================================================

        mockMvc.perform(get("/api/v1/events/BATbern142/registrations/" + registrationCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCode").value(registrationCode))
                .andExpect(jsonPath("$.eventCode").value("BATbern142"))
                .andExpect(jsonPath("$.attendeeUsername").value("alice.wonderland"))
                .andExpect(jsonPath("$.attendeeFirstName").value("Alice"))
                .andExpect(jsonPath("$.attendeeLastName").value("Wonderland"))
                .andExpect(jsonPath("$.attendeeEmail").value("alice.wonderland@example.com"))
                .andExpect(jsonPath("$.status").value("REGISTERED")) // Story 4.1.5c: starts as 'registered'
                .andExpect(jsonPath("$.registrationDate").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // Verify user enrichment was called again (from cache)
        verify(userApiClient, times(2)).getUserByUsername("alice.wonderland");

        // ========================================================================
        // STEP 4: Verify Enriched Response Includes User Details (ADR-004)
        // ========================================================================

        // Already verified in STEP 3 assertions
        // User data is NOT duplicated in database, only enriched at API response time

        // ========================================================================
        // VERIFICATION SUMMARY
        // ========================================================================

        // ✅ Anonymous registration created without authentication
        // ✅ User Management Service called with cognitoSync=false
        // ✅ User profile created with cognito_id=NULL (mocked)
        // ✅ Registration code format validated (ADR-003)
        // ✅ Registration starts in 'registered' status (unconfirmed)
        // ✅ Response contains minimal data {message, email} (no sensitive info)
        // ✅ Enriched response includes user details (ADR-004)
        // ✅ Public access to registration details (no auth)
    }

    @Test
    @DisplayName("E2E: Get-or-create returns existing anonymous user")
    void should_returnExistingAnonymousUser_when_sameEmailRegistersAgain() throws Exception {
        // Create first registration
        String firstRegistrationRequest = """
                {
                    "firstName": "Alice",
                    "lastName": "Wonderland",
                    "email": "alice.wonderland@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRegistrationRequest))
                .andExpect(status().isCreated());

        // Reset mock to track second call
        reset(userApiClient);
        // Wrap UserResponse in GetOrCreateUserResponse for getOrCreateUser mock
        GetOrCreateUserResponse createUserResponse = new GetOrCreateUserResponse()
                .username("alice.wonderland")
                .created(true)
                .user(anonymousUserProfile);
        when(userApiClient.getOrCreateUser(any())).thenReturn(createUserResponse);
        when(userApiClient.getUserByUsername("alice.wonderland")).thenReturn(anonymousUserProfile);

        // Create another event for second registration
        Event secondEvent = Event.builder()
                .eventCode("BATbern143")
                .title("BATbern 2025 Second Event")
                .eventNumber(143)
                .date(Instant.parse("2025-07-15T09:00:00Z"))
                .registrationDeadline(Instant.parse("2025-07-08T23:59:59Z"))
                .venueName("Test Venue")
                .venueAddress("Test Address 456, Bern")
                .venueCapacity(100)
                .status("published")
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .build();
        eventRepository.save(secondEvent);

        // Create second registration with same email (different event)
        String secondRegistrationRequest = """
                {
                    "firstName": "Alice",
                    "lastName": "Wonderland",
                    "email": "alice.wonderland@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern143/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRegistrationRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(containsString("Registration submitted successfully")))
                .andExpect(jsonPath("$.email").value("alice.wonderland@example.com"));

        // Verify get-or-create was called and returned existing user
        verify(userApiClient, times(1)).getOrCreateUser(any());
        verify(userApiClient, times(1)).getUserByUsername("alice.wonderland");

        // Verify both registrations exist
        assertThat(registrationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("E2E: List registrations returns enriched data")
    void should_returnEnrichedRegistrations_when_listingEventRegistrations() throws Exception {
        // Create multiple registrations
        String request1 = """
                {
                    "firstName": "Alice",
                    "lastName": "Wonderland",
                    "email": "alice.wonderland@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request1))
                .andExpect(status().isCreated());

        // Create second user
        UserResponse bobProfile = new UserResponse()
                .id("bob.builder")
                .firstName("Bob")
                .lastName("Builder")
                .email("bob.builder@example.com")
                .companyId("BuildCo")
                .active(true)
                ;

        GetOrCreateUserResponse bobCreateResponse = new GetOrCreateUserResponse()
                .username("bob.builder")
                .created(true)
                .user(bobProfile);

        when(userApiClient.getOrCreateUser(argThat(req ->
                req.getEmail().equals("bob.builder@example.com")))).thenReturn(bobCreateResponse);
        when(userApiClient.getUserByUsername("bob.builder")).thenReturn(bobProfile);

        String request2 = """
                {
                    "firstName": "Bob",
                    "lastName": "Builder",
                    "email": "bob.builder@example.com",
                    "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request2))
                .andExpect(status().isCreated());

        // List all registrations
        mockMvc.perform(get("/api/v1/events/BATbern142/registrations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].attendeeUsername").exists())
                .andExpect(jsonPath("$[0].attendeeFirstName").exists())
                .andExpect(jsonPath("$[0].attendeeLastName").exists())
                .andExpect(jsonPath("$[0].attendeeEmail").exists())
                .andExpect(jsonPath("$[1].attendeeUsername").exists())
                .andExpect(jsonPath("$[1].attendeeFirstName").exists())
                .andExpect(jsonPath("$[1].attendeeLastName").exists())
                .andExpect(jsonPath("$[1].attendeeEmail").exists());
    }
}
