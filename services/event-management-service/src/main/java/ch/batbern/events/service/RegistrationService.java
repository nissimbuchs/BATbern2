package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.CreateRegistrationRequest;
import ch.batbern.events.dto.GetOrCreateUserRequest;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.dto.UserProfileDTO;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

/**
 * Service for Registration business logic
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * Handles anonymous event registrations where users can register without creating a Cognito account.
 * Creates user profiles via User Management Service API with cognitoSync=false.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String REGISTRATION_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int REGISTRATION_CODE_SUFFIX_LENGTH = 6;
    private static final int MAX_COLLISION_RETRIES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;
    private final RegistrationEmailService registrationEmailService; // Story 2.2a Task B12

    /**
     * Create a new anonymous registration for an event (ADR-005).
     * <p>
     * This method:
     * 1. Validates event exists
     * 2. Creates/retrieves user via User Management Service (anonymous user with cognitoSync=false)
     * 3. Generates unique registration code
     * 4. Creates and persists registration
     *
     * @param eventCode Event code (public identifier)
     * @param request Registration request with attendee details
     * @return Created registration entity
     * @throws NoSuchElementException if event not found
     */
    @Transactional
    public Registration createRegistration(String eventCode, CreateRegistrationRequest request) {
        log.debug("Creating registration for event: {}", eventCode);

        // 1. Validate event exists and get its UUID
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));

        log.debug("Found event: {} (ID: {})", eventCode, event.getId());

        // 2. Get or create user via User Management Service API (ADR-005: anonymous user)
        GetOrCreateUserRequest userRequest = GetOrCreateUserRequest.builder()
                .firstName(request.getAttendeeFirstName())
                .lastName(request.getAttendeeLastName())
                .email(request.getAttendeeEmail())
                .cognitoSync(false) // ADR-005: Create anonymous user (cognito_id = NULL)
                .build();

        UserProfileDTO userProfile = userApiClient.getOrCreateUser(userRequest);
        log.info("Got/created user profile for email: {}, username: {}",
                request.getAttendeeEmail(), userProfile.getUsername());

        // QA Fix (VALID-001): Check for duplicate registration (same event + attendee)
        boolean alreadyRegistered = registrationRepository.existsByEventIdAndAttendeeUsername(
                event.getId(), userProfile.getUsername());
        if (alreadyRegistered) {
            log.warn("Duplicate registration attempt for event: {} by user: {}",
                    eventCode, userProfile.getUsername());
            throw new IllegalStateException(
                    "User " + userProfile.getUsername() + " is already registered for event " + eventCode);
        }

        // 3. Generate unique registration code (ADR-003: Meaningful Identifiers)
        String registrationCode = generateUniqueRegistrationCode(eventCode);
        log.debug("Generated registration code: {}", registrationCode);

        // 4. Create and save registration (ADR-004: No denormalized user data)
        Registration registration = Registration.builder()
                .registrationCode(registrationCode)
                .eventId(event.getId())
                .eventCode(eventCode) // Transient field for API responses
                .attendeeUsername(userProfile.getUsername()) // Cross-service reference
                .status(request.getStatus())
                .registrationDate(Instant.parse(request.getRegistrationDate()))
                .build();

        Registration saved = registrationRepository.save(registration);
        log.info("Created registration: {} for user: {} at event: {}",
                registrationCode, userProfile.getUsername(), eventCode);

        // Story 2.2a Task B12: Send async registration confirmation email
        registrationEmailService.sendRegistrationConfirmation(
                saved,
                userProfile,
                event,
                java.util.Locale.GERMAN // Default to German for BATbern events
        );
        log.debug("Queued registration confirmation email for: {}", userProfile.getEmail());

        return saved;
    }

    /**
     * Enrich registration with user data from User Management Service (ADR-004).
     * <p>
     * Fetches user details via UserApiClient and builds RegistrationResponse DTO.
     * This ensures we never duplicate user data in the database while providing
     * complete data to API consumers.
     * <p>
     * Cached via UserApiClient (15min TTL) for performance.
     *
     * @param registration Registration entity with attendeeUsername
     * @return RegistrationResponse enriched with user data
     */
    @Transactional(readOnly = true)
    public RegistrationResponse enrichRegistrationWithUserData(Registration registration) {
        log.debug("Enriching registration: {} with user data", registration.getRegistrationCode());

        // Fetch user details from User Management Service (cached)
        UserProfileDTO userProfile = userApiClient.getUserByUsername(registration.getAttendeeUsername());
        log.debug("Enriched registration {} with user: {}", registration.getRegistrationCode(), userProfile.getUsername());

        return RegistrationResponse.builder()
                // Registration fields
                .registrationCode(registration.getRegistrationCode())
                .eventCode(registration.getEventCode()) // Transient field populated by controller
                .status(registration.getStatus())
                .registrationDate(registration.getRegistrationDate() != null
                        ? ISO_FORMATTER.format(registration.getRegistrationDate()) : null)
                .createdAt(registration.getCreatedAt() != null
                        ? ISO_FORMATTER.format(registration.getCreatedAt()) : null)
                .updatedAt(registration.getUpdatedAt() != null
                        ? ISO_FORMATTER.format(registration.getUpdatedAt()) : null)
                // Enriched user fields (ADR-004)
                .attendeeUsername(userProfile.getUsername())
                .attendeeFirstName(userProfile.getFirstName())
                .attendeeLastName(userProfile.getLastName())
                .attendeeEmail(userProfile.getEmail())
                .attendeeCompany(userProfile.getCompanyId()) // May be null for anonymous users
                .build();
    }

    /**
     * Generate a unique registration code with format: {eventCode}-reg-{random}
     * Example: BATbern142-reg-A3X9K2
     * <p>
     * Retries up to MAX_COLLISION_RETRIES times if code already exists.
     *
     * @param eventCode Event code prefix
     * @return Unique registration code
     * @throws IllegalStateException if unable to generate unique code after retries
     */
    private String generateUniqueRegistrationCode(String eventCode) {
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String code = eventCode + "-reg-" + generateRandomSuffix();

            if (!registrationRepository.existsByRegistrationCode(code)) {
                return code;
            }

            log.warn("Registration code collision on attempt {}: {}", attempt + 1, code);
        }

        throw new IllegalStateException(
                "Failed to generate unique registration code after " + MAX_COLLISION_RETRIES + " attempts"
        );
    }

    /**
     * Generate random alphanumeric suffix for registration code.
     * Uses SecureRandom for cryptographic randomness.
     *
     * @return Random suffix (e.g., "A3X9K2")
     */
    private String generateRandomSuffix() {
        StringBuilder suffix = new StringBuilder(REGISTRATION_CODE_SUFFIX_LENGTH);
        for (int i = 0; i < REGISTRATION_CODE_SUFFIX_LENGTH; i++) {
            int index = RANDOM.nextInt(REGISTRATION_CODE_CHARS.length());
            suffix.append(REGISTRATION_CODE_CHARS.charAt(index));
        }
        return suffix.toString();
    }
}
