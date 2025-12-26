package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.BatchRegistrationItem;
import ch.batbern.events.dto.generated.BatchRegistrationItem.StatusEnum;
import ch.batbern.events.dto.generated.BatchRegistrationRequest;
import ch.batbern.events.dto.generated.BatchRegistrationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service layer tests for batch registration functionality (Story BAT-14).
 *
 * Tests the business logic in RegistrationService.createBatchRegistrations() method:
 * - User creation via User Service Client
 * - Registration creation with idempotency
 * - Partial success handling
 * - Error handling
 *
 * TDD: Written BEFORE implementation (RED phase)
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceBatchTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private RegistrationEmailService registrationEmailService;

    @InjectMocks
    private RegistrationService registrationService;

    private Event testEvent1;
    private Event testEvent2;
    private GetOrCreateUserResponse testUserResponse;

    @BeforeEach
    void setup() {
        Instant now = Instant.now();
        Instant eventDate = now.plus(30, ChronoUnit.DAYS);
        Instant regDeadline = now.plus(20, ChronoUnit.DAYS);

        testEvent1 = Event.builder()
            .id(UUID.randomUUID())
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
            .id(UUID.randomUUID())
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

        // Setup default user response
        UserResponse userProfile = new UserResponse();
        userProfile.setId("max.mustermann");
        userProfile.setFirstName("Max");
        userProfile.setLastName("Mustermann");
        userProfile.setEmail("max.mustermann@example.com");

        testUserResponse = new GetOrCreateUserResponse();
        testUserResponse.setUsername("max.mustermann");
        testUserResponse.setCreated(false);
        testUserResponse.setUser(userProfile);
    }

    /**
     * Test 1: should_createUser_when_emailNotExists
     *
     * Verifies that a new user is created via UserApiClient when the email doesn't exist.
     *
     * Expected behavior:
     * - Calls userApiClient.getOrCreateUser() with correct parameters
     * - Request includes: email, firstName, lastName, cognitoSync=false
     * - Returns user data for subsequent registration creation
     */
    @Test
    void should_createUser_when_emailNotExists() {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest(
            "max.mustermann@example.com",
            "Max",
            "Mustermann",
            List.of(
                new BatchRegistrationItem("BATbern25", StatusEnum.ATTENDED)
            )
        );

        when(eventRepository.findByEventCode("BATbern25")).thenReturn(Optional.of(testEvent1));
        when(registrationRepository.existsByEventIdAndAttendeeUsername(any(), anyString())).thenReturn(false);
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(testUserResponse);

        // Set user response to indicate new user was created
        testUserResponse.setCreated(true);

        // Act
        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);

        // Assert - Verify UserApiClient called with correct parameters
        ArgumentCaptor<GetOrCreateUserRequest> userRequestCaptor = ArgumentCaptor.forClass(GetOrCreateUserRequest.class);
        verify(userApiClient).getOrCreateUser(userRequestCaptor.capture());

        GetOrCreateUserRequest capturedRequest = userRequestCaptor.getValue();
        assertThat(capturedRequest.getEmail()).isEqualTo("max.mustermann@example.com");
        assertThat(capturedRequest.getFirstName()).isEqualTo("Max");
        assertThat(capturedRequest.getLastName()).isEqualTo("Mustermann");
        assertThat(capturedRequest.getCognitoSync()).isFalse(); // ADR-005: Anonymous user

        // Assert - Response contains created username
        assertThat(response.getUsername()).isEqualTo("max.mustermann");
        assertThat(response.getSuccessfulRegistrations()).isEqualTo(1);
    }

    /**
     * Test 2: should_useExistingUser_when_emailExists
     *
     * Verifies that existing user is returned when email already exists.
     *
     * Expected behavior:
     * - Calls userApiClient.getOrCreateUser() which returns existing user
     * - Response indicates user was NOT created (created=false)
     * - Proceeds with registration creation using existing username
     */
    @Test
    void should_useExistingUser_when_emailExists() {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest(
            "max.mustermann@example.com",
            "Max",
            "Mustermann",
            List.of(
                new BatchRegistrationItem("BATbern25", StatusEnum.ATTENDED)
            )
        );

        when(eventRepository.findByEventCode("BATbern25")).thenReturn(Optional.of(testEvent1));
        when(registrationRepository.existsByEventIdAndAttendeeUsername(any(), anyString())).thenReturn(false);
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(testUserResponse);

        // User already exists (created=false)
        testUserResponse.setCreated(false);

        // Act
        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);

        // Assert - UserApiClient called once
        verify(userApiClient, times(1)).getOrCreateUser(any(GetOrCreateUserRequest.class));

        // Assert - Response uses existing user
        assertThat(response.getUsername()).isEqualTo("max.mustermann");
        assertThat(response.getSuccessfulRegistrations()).isEqualTo(1);
    }

    /**
     * Test 3: should_createRegistrations_when_validEvents
     *
     * Verifies that registrations are created for all valid events in the batch.
     *
     * Expected behavior:
     * - Looks up each event by eventCode
     * - Creates registration for each event
     * - Sets status from request (e.g., "ATTENDED" for historical data)
     * - Links registration to user via attendeeUsername
     * - Returns success count matching number of registrations
     */
    @Test
    void should_createRegistrations_when_validEvents() {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest(
            "max.mustermann@example.com",
            "Max",
            "Mustermann",
            List.of(
                new BatchRegistrationItem("BATbern25", StatusEnum.ATTENDED),
                new BatchRegistrationItem("BATbern31", StatusEnum.ATTENDED)
            )
        );

        when(eventRepository.findByEventCode("BATbern25")).thenReturn(Optional.of(testEvent1));
        when(eventRepository.findByEventCode("BATbern31")).thenReturn(Optional.of(testEvent2));
        when(registrationRepository.existsByEventIdAndAttendeeUsername(any(), anyString())).thenReturn(false);
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(testUserResponse);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);

        // Assert - Two registrations created
        verify(registrationRepository, times(2)).save(any(Registration.class));

        // Assert - Response shows all successful
        assertThat(response.getTotalRegistrations()).isEqualTo(2);
        assertThat(response.getSuccessfulRegistrations()).isEqualTo(2);
        assertThat(response.getFailedRegistrations()).isEmpty();
        assertThat(response.getErrors()).isEmpty();

        // Assert - Verify registration data
        ArgumentCaptor<Registration> registrationCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository, times(2)).save(registrationCaptor.capture());

        List<Registration> savedRegistrations = registrationCaptor.getAllValues();
        assertThat(savedRegistrations).hasSize(2);
        assertThat(savedRegistrations.get(0).getAttendeeUsername()).isEqualTo("max.mustermann");
        assertThat(savedRegistrations.get(0).getStatus()).isEqualToIgnoringCase("attended");
        assertThat(savedRegistrations.get(1).getAttendeeUsername()).isEqualTo("max.mustermann");
        assertThat(savedRegistrations.get(1).getStatus()).isEqualToIgnoringCase("attended");
    }

    /**
     * Test 4: should_skipDuplicates_when_registrationExists
     *
     * Verifies idempotency: duplicate registrations are skipped without error.
     *
     * Expected behavior:
     * - Checks if registration exists (same user + event)
     * - If exists, skips creating duplicate
     * - Does NOT count skipped registration as successful
     * - Does NOT add to failed registrations
     * - Continues processing remaining registrations
     */
    @Test
    void should_skipDuplicates_when_registrationExists() {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest(
            "max.mustermann@example.com",
            "Max",
            "Mustermann",
            List.of(
                new BatchRegistrationItem("BATbern25", StatusEnum.ATTENDED),
                new BatchRegistrationItem("BATbern31", StatusEnum.ATTENDED)
            )
        );

        when(eventRepository.findByEventCode("BATbern25")).thenReturn(Optional.of(testEvent1));
        when(eventRepository.findByEventCode("BATbern31")).thenReturn(Optional.of(testEvent2));
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(testUserResponse);

        // First registration already exists (duplicate), second does not
        when(registrationRepository.existsByEventIdAndAttendeeUsername(testEvent1.getId(), "max.mustermann"))
            .thenReturn(true);
        when(registrationRepository.existsByEventIdAndAttendeeUsername(testEvent2.getId(), "max.mustermann"))
            .thenReturn(false);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);

        // Assert - Only one registration created (second one, first was duplicate)
        verify(registrationRepository, times(1)).save(any(Registration.class));

        // Assert - Response shows only 1 successful (duplicate skipped)
        assertThat(response.getTotalRegistrations()).isEqualTo(2);
        assertThat(response.getSuccessfulRegistrations()).isEqualTo(1);
        assertThat(response.getFailedRegistrations()).isEmpty(); // Duplicates don't count as failures
    }

    /**
     * Test 5: should_returnFailedRegistrations_when_eventNotFound
     *
     * Verifies partial success: some registrations fail while others succeed.
     *
     * Expected behavior:
     * - Continues processing after event not found error
     * - Adds failed registration to failedRegistrations list
     * - Includes event code and error reason
     * - Processes remaining registrations successfully
     * - Returns partial success response
     */
    @Test
    void should_returnFailedRegistrations_when_eventNotFound() {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest(
            "max.mustermann@example.com",
            "Max",
            "Mustermann",
            List.of(
                new BatchRegistrationItem("BATbern25", StatusEnum.ATTENDED),
                new BatchRegistrationItem("BATbern99", StatusEnum.ATTENDED), // Non-existent event
                new BatchRegistrationItem("BATbern31", StatusEnum.ATTENDED)
            )
        );

        when(eventRepository.findByEventCode("BATbern25")).thenReturn(Optional.of(testEvent1));
        when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.empty()); // Not found
        when(eventRepository.findByEventCode("BATbern31")).thenReturn(Optional.of(testEvent2));
        when(registrationRepository.existsByEventIdAndAttendeeUsername(any(), anyString())).thenReturn(false);
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(testUserResponse);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);

        // Assert - Two registrations created (BATbern25, BATbern31), one failed (BATbern99)
        verify(registrationRepository, times(2)).save(any(Registration.class));

        // Assert - Partial success response
        assertThat(response.getTotalRegistrations()).isEqualTo(3);
        assertThat(response.getSuccessfulRegistrations()).isEqualTo(2);
        assertThat(response.getFailedRegistrations()).hasSize(1);

        // Assert - Failed registration details
        assertThat(response.getFailedRegistrations().get(0).getEventCode()).isEqualTo("BATbern99");
        assertThat(response.getFailedRegistrations().get(0).getReason()).contains("not found");

        // Assert - Errors list populated
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0)).contains("BATbern99");
    }
}
