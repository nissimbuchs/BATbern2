package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.BatchRegistrationItem;
import ch.batbern.events.dto.generated.BatchRegistrationRequest;
import ch.batbern.events.dto.generated.BatchRegistrationResponse;
import ch.batbern.events.dto.generated.CreateRegistrationRequest;
import ch.batbern.events.dto.generated.FailedRegistration;
import ch.batbern.events.dto.generated.MyRegistrationResponse;
import ch.batbern.events.dto.generated.MyRegistrationResponse.StatusEnum;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.exception.DuplicateSubscriberException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    @SuppressWarnings("unused") // Story 2.2a Task B12 - Will be used when email confirmation is implemented
    private final RegistrationEmailService registrationEmailService;
    private final NewsletterSubscriberService newsletterSubscriberService;
    private final WaitlistPromotionEmailService waitlistPromotionEmailService; // Story 10.11
    private final WaitlistPromotionService waitlistPromotionService; // Story 10.12

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
        GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setEmail(request.getEmail());
        // ADR-005: Pass company display name - User Service will get-or-create company with slug
        userRequest.setCompanyId(request.getCompany());  // "Test Co" → Company.getOrCreate → companyId: "testco"
        userRequest.setCognitoSync(false); // ADR-005: Create anonymous user (cognito_id = NULL)

        GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);
        String username = userResponse.getUsername();
        log.info("Got/created user profile for email: {}, username: {}, created: {}",
                request.getEmail(), username, userResponse.getCreated());

        // QA Fix (VALID-001): Check for duplicate registration (same event + attendee)
        // If registration exists but is pending ("registered" status), return it to resend confirmation email
        Optional<Registration> existingRegistration = registrationRepository.findByEventIdAndAttendeeUsername(
                event.getId(), username);
        if (existingRegistration.isPresent()) {
            Registration registration = existingRegistration.get();
            if ("registered".equalsIgnoreCase(registration.getStatus())) {
                // Return existing pending registration - controller will resend confirmation email
                log.info("Found pending registration for event: {} by user: {}, will resend confirmation email",
                        eventCode, username);
                registration.setEventCode(eventCode); // Set transient field for API response
                return registration;
            } else if ("waitlist".equalsIgnoreCase(registration.getStatus())) {
                // Story 10.11 (T10.4): Duplicate waitlist registration — do NOT create another entry.
                // Return existing and resend waitlist-confirmation email.
                log.info("Found waitlist registration for event: {} by user: {},"
                        + " will resend waitlist-confirmation email", eventCode, username);
                registration.setEventCode(eventCode);
                waitlistPromotionEmailService.sendWaitlistConfirmationEmail(registration);
                return registration;
            } else if ("cancelled".equalsIgnoreCase(registration.getStatus())) {
                // Story 10.10 (T4.6): Allow re-registration for cancelled users.
                // Delete the cancelled record and fall through to create a new registration.
                // This replaces the confusing IllegalStateException that users previously experienced.
                registrationRepository.delete(registration);
                log.info("Deleted cancelled registration for event: {} by user: {}, allowing re-registration",
                        eventCode, username);
                // Fall through to create a new registration below
            } else {
                // confirmed — reject duplicate
                log.warn("Duplicate registration attempt for event: {} by user: {} (status: {})",
                        eventCode, username, registration.getStatus());
                throw new IllegalStateException(
                        "User " + username + " is already registered for event " + eventCode);
            }
        }

        // 3. Generate unique registration code (ADR-003: Meaningful Identifiers)
        String registrationCode = generateUniqueRegistrationCode(eventCode);
        log.debug("Generated registration code: {}", registrationCode);

        // Story 10.11 (T10.1): Capacity enforcement
        // Count active (registered + confirmed) registrations; if full → place on waitlist
        Integer capacity = event.getRegistrationCapacity();
        String registrationStatus = "registered"; // default
        Integer waitlistPosition = null;
        if (capacity != null) {
            long activeCount = registrationRepository.countByEventIdAndStatusIn(
                    event.getId(), List.of("registered", "confirmed"));
            if (activeCount >= capacity) {
                registrationStatus = "waitlist";
                waitlistPosition = registrationRepository.getNextWaitlistPosition(event.getId());
                log.info("Event {} is at capacity ({}/{}), placing {} on waitlist at position {}",
                        eventCode, activeCount, capacity, username, waitlistPosition);
            }
        }

        // 4. Create and save registration (ADR-004: No denormalized user data)
        // Story 4.1.5c: Status starts as "registered", becomes "confirmed" after email confirmation
        // Story 10.11: Status may be "waitlist" when event is full
        // Story 10.12: Generate deregistration token (non-expiring, never rotated)
        // Performance: Populate search cache fields for database-level filtering
        Registration registration = Registration.builder()
                .registrationCode(registrationCode)
                .eventId(event.getId())
                .eventCode(eventCode) // Transient field for API responses
                .attendeeUsername(username) // Cross-service reference
                // Search cache fields (performance optimization for database-level filtering)
                .attendeeFirstName(userResponse.getUser().getFirstName())
                .attendeeLastName(userResponse.getUser().getLastName())
                .attendeeEmail(userResponse.getUser().getEmail())
                .attendeeCompanyId(userResponse.getUser().getCompanyId())
                .status(registrationStatus) // "registered" or "waitlist" (lowercase per DB constraint)
                .waitlistPosition(waitlistPosition) // null for registered, 1-based for waitlist
                .deregistrationToken(UUID.randomUUID()) // Story 10.12: self-service deregistration token
                .registrationDate(Instant.now()) // Auto-set registration timestamp
                .build();

        Registration saved = registrationRepository.save(registration);
        log.info("Created registration: {} for user: {} at event: {}",
                registrationCode, username, eventCode);

        // Story 10.7 (AC6): Auto-subscribe to newsletter if opted in during registration
        if (request.getCommunicationPreferences() != null
                && Boolean.TRUE.equals(request.getCommunicationPreferences().getNewsletterSubscribed())) {
            try {
                newsletterSubscriberService.subscribe(
                        request.getEmail(), request.getFirstName(), "de", "registration", username);
                log.info("Auto-subscribed {} to newsletter via registration", request.getEmail());
            } catch (DuplicateSubscriberException e) {
                // Already subscribed — silently ignore (AC6)
                log.debug("Newsletter auto-subscribe: {} already active, skipping", request.getEmail());
            }
        }

        // Story 10.11 (T10.3): For waitlist registrations, send waitlist-confirmation email now.
        // Normal registrations: confirmation email sent by controller after token generation.
        if ("waitlist".equals(saved.getStatus())) {
            waitlistPromotionEmailService.sendWaitlistConfirmationEmail(saved);
        }

        // Story 4.1.5c: Email sending moved to EventController (needs JWT token)
        // Email will be sent after token generation in controller layer

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
     * <p>
     * <strong>Data Integrity Handling:</strong> If a user is not found (data integrity issue),
     * returns placeholder data instead of failing. This allows organizers to see all registrations
     * even when some reference missing users, making data quality issues visible.
     *
     * @param registration Registration entity with attendeeUsername
     * @return RegistrationResponse enriched with user data (or placeholders if user missing)
     */
    @Transactional(readOnly = true)
    public RegistrationResponse enrichRegistrationWithUserData(Registration registration) {
        log.debug("Enriching registration: {} with user data", registration.getRegistrationCode());

        try {
            // Fetch user details from User Management Service (cached)
            ch.batbern.events.dto.generated.users.UserResponse userProfile =
                userApiClient.getUserByUsername(registration.getAttendeeUsername());
            log.debug("Enriched registration {} with user: {}", registration.getRegistrationCode(),
                userProfile.getId());

            return RegistrationResponse.builder()
                    // Registration fields
                    .registrationCode(registration.getRegistrationCode())
                    .eventCode(registration.getEventCode()) // Transient field populated by controller
                    .status(registration.getStatus() != null ? registration.getStatus().toUpperCase() : null)
                    .registrationDate(registration.getRegistrationDate() != null
                            ? ISO_FORMATTER.format(registration.getRegistrationDate()) : null)
                    .createdAt(registration.getCreatedAt() != null
                            ? ISO_FORMATTER.format(registration.getCreatedAt()) : null)
                    .updatedAt(registration.getUpdatedAt() != null
                            ? ISO_FORMATTER.format(registration.getUpdatedAt()) : null)
                    // Enriched user fields (ADR-004)
                    .attendeeUsername(userProfile.getId())
                    .attendeeFirstName(userProfile.getFirstName())
                    .attendeeLastName(userProfile.getLastName())
                    .attendeeEmail(userProfile.getEmail())
                    .attendeeCompany(userProfile.getCompanyId()) // May be null for anonymous users
                    .build();

        } catch (ch.batbern.events.exception.UserNotFoundException e) {
            // Data integrity issue: Registration exists but user was deleted or never created
            // Return placeholder data so organizers can see the registration and identify the issue
            log.warn("User not found for registration {}: {} - returning placeholder data to maintain list integrity",
                    registration.getRegistrationCode(), registration.getAttendeeUsername());

            return RegistrationResponse.builder()
                    // Registration fields
                    .registrationCode(registration.getRegistrationCode())
                    .eventCode(registration.getEventCode())
                    .status(registration.getStatus() != null ? registration.getStatus().toUpperCase() : null)
                    .registrationDate(registration.getRegistrationDate() != null
                            ? ISO_FORMATTER.format(registration.getRegistrationDate()) : null)
                    .createdAt(registration.getCreatedAt() != null
                            ? ISO_FORMATTER.format(registration.getCreatedAt()) : null)
                    .updatedAt(registration.getUpdatedAt() != null
                            ? ISO_FORMATTER.format(registration.getUpdatedAt()) : null)
                    // Placeholder user fields (data integrity issue)
                    .attendeeUsername(registration.getAttendeeUsername())
                    .attendeeFirstName("[User Not Found]")
                    .attendeeLastName("[" + registration.getAttendeeUsername() + "]")
                    .attendeeEmail("missing@unknown.invalid")
                    .attendeeCompany(null)
                    .build();
        }
    }

    /**
     * Get the authenticated user's registration status for a specific event.
     * <p>
     * Story 10.10: GET /events/{eventCode}/my-registration (AC1)
     * <p>
     * ADR-004: Response is minimal — no user profile fields (firstName, lastName, email).
     * ADR-003: Uses registrationCode and eventCode as meaningful identifiers.
     * <p>
     * Always returns a response (never throws). Use {@code registered} field to determine
     * if a record exists — avoids browser console 404 errors on unenrolled events.
     *
     * @param eventCode             Event code (e.g., "BATbern142")
     * @param authenticatedUsername Username extracted from SecurityContext
     * @return MyRegistrationResponse with registered=true if found, registered=false if not
     */
    @Transactional(readOnly = true)
    public MyRegistrationResponse getMyRegistration(String eventCode, String authenticatedUsername) {
        log.debug("Getting registration for event: {} and user: {}", eventCode, authenticatedUsername);

        return registrationRepository.findByEventCodeAndAttendeeUsername(eventCode, authenticatedUsername)
                .map(registration -> {
                    // Map DB status to API enum. DB uses 'waitlisted'; API enum uses WAITLIST.
                    String dbStatus = registration.getStatus() != null
                            ? registration.getStatus().toUpperCase() : null;
                    if ("WAITLISTED".equals(dbStatus)) {
                        dbStatus = "WAITLIST";
                    }
                    StatusEnum statusEnum = dbStatus != null ? StatusEnum.fromValue(dbStatus) : null;
                    OffsetDateTime registrationDate = registration.getRegistrationDate() != null
                            ? registration.getRegistrationDate().atOffset(ZoneOffset.UTC) : null;
                    return new MyRegistrationResponse(true)
                            .registrationCode(registration.getRegistrationCode())
                            .eventCode(eventCode)
                            .status(statusEnum)
                            .registrationDate(registrationDate)
                            .waitlistPosition(registration.getWaitlistPosition()); // AC13 (Story 10.11)
                })
                .orElse(new MyRegistrationResponse(false));
    }

    /**
     * Create batch event registrations for a participant (Story BAT-14).
     * <p>
     * This method:
     * 1. Gets or creates user via User Management Service (anonymous user with cognitoSync=false)
     * 2. Creates registrations for all events in the batch
     * 3. Skips duplicate registrations (idempotent)
     * 4. Returns detailed results with partial success support
     * <p>
     * Designed for historical data migration where a single participant attended multiple events.
     *
     * @param request Batch registration request with participant data and event list
     * @return Batch registration response with success/failure counts and details
     */
    @Transactional
    public BatchRegistrationResponse createBatchRegistrations(BatchRegistrationRequest request) {
        log.debug("Creating batch registrations for participant: {}", request.getParticipantEmail());

        // 1. Get or create user via User Management Service
        GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
        userRequest.setEmail(request.getParticipantEmail());
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setCompanyId(request.getCompanyId()); // Story 3.2: Include company from batch import
        userRequest.setCognitoSync(false); // ADR-005: Create anonymous user for historical data

        GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);
        String username = userResponse.getUsername();
        log.info("Got/created user for batch registration: {}, created: {}",
                username, userResponse.getCreated());

        // 2. Process each registration in the batch
        List<FailedRegistration> failedRegistrations = new ArrayList<>();
        int successCount = 0;

        for (BatchRegistrationItem item : request.getRegistrations()) {
            try {
                // Validate event exists
                Event event = eventRepository.findByEventCode(item.getEventCode())
                        .orElseThrow(() -> new NoSuchElementException(
                                "Event not found: " + item.getEventCode()));

                log.debug("Processing registration for event: {} (ID: {})", item.getEventCode(), event.getId());

                // Check if registration already exists (idempotency)
                boolean exists = registrationRepository.existsByEventIdAndAttendeeUsername(
                        event.getId(),
                        username
                );

                if (exists) {
                    // Skip duplicate - idempotent behavior
                    log.debug("Registration already exists for event: {} by user: {}, skipping",
                            item.getEventCode(), username);
                    continue;
                }

                // Create registration
                // Convert enum status to lowercase string for database (per coding standards)
                String status = item.getStatus().getValue().toLowerCase();

                Registration registration = Registration.builder()
                        .registrationCode(generateUniqueRegistrationCode(item.getEventCode()))
                        .eventId(event.getId())
                        .attendeeUsername(username)
                        .attendeeCompanyId(request.getCompanyId())
                        .status(status)
                        .registrationDate(Instant.now())
                        .build();

                registrationRepository.save(registration);
                successCount++;
                log.debug("Created registration for event: {} by user: {}", item.getEventCode(), username);

            } catch (NoSuchElementException e) {
                // Event not found
                failedRegistrations.add(new FailedRegistration(
                        item.getEventCode(),
                        "Event not found"
                ));
                log.warn("Failed to create registration for event: {} - Event not found", item.getEventCode());

            } catch (Exception e) {
                // Other errors (e.g., database errors, validation errors)
                failedRegistrations.add(new FailedRegistration(
                        item.getEventCode(),
                        e.getMessage()
                ));
                log.error("Failed to create registration for event: {} - {}", item.getEventCode(), e.getMessage(), e);
            }
        }

        // 3. Build response with detailed results
        List<String> errors = failedRegistrations.stream()
                .map(f -> String.format("Event %s: %s", f.getEventCode(), f.getReason()))
                .collect(Collectors.toList());

        BatchRegistrationResponse response = new BatchRegistrationResponse(
                username,
                request.getRegistrations().size(),
                successCount,
                failedRegistrations,
                errors
        );

        log.info("Batch registration completed for user: {} - Total: {}, Successful: {}, Failed: {}",
                username, response.getTotalRegistrations(), response.getSuccessfulRegistrations(),
                response.getFailedRegistrations().size());

        return response;
    }

    /**
     * Soft-cancel a registration and trigger waitlist promotion.
     * <p>
     * Story 10.12 (AC4, AC5): Cancels a registration by setting status = "cancelled"
     * (no hard delete). After persisting, calls WaitlistPromotionService to promote
     * the next waitlisted attendee if one exists.
     * <p>
     * Called by:
     * - DeregistrationService (token-based self-service flow)
     * - DeregistrationService (by-email flow — sends link, promotion happens on actual cancel)
     * - EventController (organizer cancel — replaces hard-delete)
     * - EventController JWT-token cancel endpoint (anonymous cancellation flow)
     *
     * @param registration The registration to cancel (must not be null, must have eventId)
     */
    /**
     * Cancel a registration by email address and event code.
     * Called when an attendee replies with a CANCEL keyword (Story 10.17).
     * Replicates the same status change as clicking the deregistration link in the email,
     * including waitlist promotion.
     * <p>
     * Anti-enumeration: silent no-op if no matching active registration is found.
     *
     * @param email     attendee email address
     * @param eventCode event code (e.g. "BATbern42")
     */
    @Transactional
    public void cancelByEmail(String email, String eventCode) {
        registrationRepository.findByAttendeeEmailAndEventCode(email, eventCode)
                .filter(r -> !"cancelled".equalsIgnoreCase(r.getStatus()))
                .ifPresentOrElse(
                        registration -> {
                            cancelRegistration(registration);
                            log.info("Registration cancelled via email reply: {} for event: {}",
                                    registration.getRegistrationCode(), eventCode);
                        },
                        () -> log.info("cancelByEmail: no active registration found for {}*** / {}",
                                email.substring(0, Math.min(5, email.length())), eventCode)
            );
    }

    /**
     * Confirm a registration by email address and event code.
     * Called when an attendee replies with an ACCEPT keyword (Story 10.17).
     * Replicates the same status change as clicking the confirmation link in the email.
     * <p>
     * Anti-enumeration: silent no-op if no matching active registration is found.
     *
     * @param email     attendee email address
     * @param eventCode event code (e.g. "BATbern42")
     */
    @Transactional
    public void confirmByEmail(String email, String eventCode) {
        registrationRepository.findByAttendeeEmailAndEventCode(email, eventCode)
                .filter(r -> !"confirmed".equalsIgnoreCase(r.getStatus())
                          && !"cancelled".equalsIgnoreCase(r.getStatus()))
                .ifPresentOrElse(
                        registration -> {
                            registration.setStatus("confirmed");
                            registration.setUpdatedAt(java.time.Instant.now());
                            registrationRepository.save(registration);
                            log.info("Registration confirmed via email reply: {} for event: {}",
                                    registration.getRegistrationCode(), eventCode);
                        },
                        () -> log.info("confirmByEmail: no active registration found for {}*** / {}",
                                email.substring(0, Math.min(5, email.length())), eventCode)
            );
    }

    @Transactional
    public void cancelRegistration(Registration registration) {
        registration.setStatus("cancelled");
        registrationRepository.save(registration);
        waitlistPromotionService.promoteFromWaitlist(registration.getEventId());
        log.info("Registration {} cancelled; waitlist promotion triggered for event {}",
                registration.getRegistrationCode(), registration.getEventId());
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
