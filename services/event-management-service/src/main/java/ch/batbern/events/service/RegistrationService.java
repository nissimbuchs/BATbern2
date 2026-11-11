package ch.batbern.events.service;

import ch.batbern.shared.utils.LoggingUtils;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.registrations.dto.generated.AddParticipantsResponse;
import ch.batbern.events.registrations.dto.generated.CreateRegistrationRequest;
import ch.batbern.events.registrations.dto.generated.ParticipantImportPerson;
import ch.batbern.events.registrations.dto.generated.ParticipantImportResult;
import ch.batbern.events.registrations.dto.generated.MyRegistrationResponse;
import ch.batbern.events.registrations.dto.generated.MyRegistrationResponse.StatusEnum;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.exception.DuplicateSubscriberException;
import ch.batbern.events.exception.RegistrationCapacityExceededException;
import ch.batbern.events.exception.DeregistrationClosedException;
import ch.batbern.events.exception.RegistrationClosedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;
    private final RegistrationEmailService registrationEmailService;
    private final NewsletterSubscriberService newsletterSubscriberService;
    private final WaitlistPromotionEmailService waitlistPromotionEmailService; // Story 10.11
    private final WaitlistPromotionService waitlistPromotionService; // Story 10.12
    private final PersonAddresses personAddresses; // #1183

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

        // Feedback #1: close public registration AND waitlist once the deadline has passed.
        assertRegistrationOpen(event);

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
        boolean companyUpdated = Boolean.TRUE.equals(userResponse.getCompanyUpdated());
        log.info("Got/created user profile for email: {}, username: {}, created: {}, "
                        + "typedCompany: '{}', companyUpdated: {}",
                LoggingUtils.maskEmail(request.getEmail()), username, userResponse.getCreated(),
                request.getCompany(), companyUpdated);

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
                registration.setCompanyUpdated(companyUpdated);
                return registration;
            } else if ("waitlist".equalsIgnoreCase(registration.getStatus())) {
                // Story 10.11 (T10.4): Duplicate waitlist registration — do NOT create another entry.
                // Return existing and resend waitlist-confirmation email.
                log.info("Found waitlist registration for event: {} by user: {},"
                        + " will resend waitlist-confirmation email", eventCode, username);
                registration.setEventCode(eventCode);
                registration.setCompanyUpdated(companyUpdated);
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
                    event.getId(), Registration.CAPACITY_STATUSES);
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
                .companyUpdated(companyUpdated) // BATbern59 badge fix: surfaced in API response for UI notice
                .build();

        Registration saved = registrationRepository.save(registration);
        log.info("Created registration: {} for user: {} at event: {}",
                registrationCode, username, eventCode);

        // Story 10.7 (AC6): Auto-subscribe to newsletter if opted in during registration
        if (request.getCommunicationPreferences() != null
                && Boolean.TRUE.equals(request.getCommunicationPreferences().getNewsletterSubscribed())) {
            try {
                String displayName = (request.getFirstName() + " " + request.getLastName()).trim();
                newsletterSubscriberService.subscribe(
                        request.getEmail(), displayName, "de", "registration", username);
                log.info("Auto-subscribed {} to newsletter via registration",
                        LoggingUtils.maskEmail(request.getEmail()));
            } catch (DuplicateSubscriberException e) {
                // Already subscribed — silently ignore (AC6)
                log.debug("Newsletter auto-subscribe: {} already active, skipping",
                        LoggingUtils.maskEmail(request.getEmail()));
            } catch (ch.batbern.events.exception.ReservedEmailDomainException e) {
                // Reserved/test domain (RFC 2606) — let registration succeed but skip subscribe
                log.warn("Newsletter auto-subscribe skipped for reserved domain: {}",
                        LoggingUtils.maskEmail(request.getEmail()));
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
                    .waitlistPosition(registration.getWaitlistPosition())
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
                    .waitlistPosition(registration.getWaitlistPosition())
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
                // #1183: else a registration of the same person under another profile (verified address)
                .or(() -> {
                    Set<String> addresses = personAddresses.verified(authenticatedUsername);
                    return addresses.isEmpty() ? Optional.empty()
                            : registrationRepository.findByEventCodeAndAttendeeEmailIn(eventCode, addresses)
                                    .stream().min(PersonAddresses.best(authenticatedUsername));
                })
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
                    return new MyRegistrationResponse().registered(true)
                            .registrationCode(registration.getRegistrationCode())
                            .eventCode(eventCode)
                            .status(statusEnum)
                            .registrationDate(registrationDate)
                            .waitlistPosition(registration.getWaitlistPosition()); // AC13 (Story 10.11)
                })
                .orElse(new MyRegistrationResponse().registered(false));
    }

    /**
     * Historical import for one event (Story 4.10c; formerly the per-participant
     * {@code POST /events/batch_registrations} of BAT-14): each person is looked up or created by
     * e-mail in CUMS (no login, ADR-005) and registered with the given status unless already
     * registered.
     *
     * <p>Not one transaction on purpose: each person's registration is saved on its own, so a
     * person whose lookup or save fails is reported as {@code FAILED} and the others stay. No mail
     * is sent.
     *
     * @param event  the event (loaded by the controller)
     * @param people at most 100 people (checked by the request schema)
     * @return counts and one result per person, in request order
     */
    public AddParticipantsResponse importParticipants(Event event, List<ParticipantImportPerson> people) {
        List<ParticipantImportResult> results = new ArrayList<>();
        int enrolled = 0;
        int skipped = 0;
        int failed = 0;
        for (ParticipantImportPerson person : people) {
            ParticipantImportResult result = importParticipant(event, person);
            results.add(result);
            if (result.getOutcome() == ParticipantImportResult.OutcomeEnum.REGISTERED) {
                enrolled++;
            } else if (result.getOutcome() == ParticipantImportResult.OutcomeEnum.SKIPPED) {
                skipped++;
            } else {
                failed++;
            }
        }
        log.info("Participant import for event {}: total={}, registered={}, skipped={}, failed={}",
                event.getEventCode(), people.size(), enrolled, skipped, failed);
        return new AddParticipantsResponse()
                .enrolled(enrolled)
                .skipped(skipped)
                .failed(failed)
                .results(results);
    }

    private ParticipantImportResult importParticipant(Event event, ParticipantImportPerson person) {
        ParticipantImportResult result = new ParticipantImportResult().email(person.getEmail());
        try {
            GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
            userRequest.setEmail(person.getEmail());
            userRequest.setFirstName(person.getFirstName());
            userRequest.setLastName(person.getLastName());
            userRequest.setCompanyId(person.getCompanyId());
            userRequest.setCognitoSync(false); // ADR-005: historical attendee, no login
            GetOrCreateUserResponse user = userApiClient.getOrCreateUser(userRequest);
            String username = user.getUsername();
            result.username(username);

            if (registrationRepository.existsByEventIdAndAttendeeUsername(event.getId(), username)) {
                return result.outcome(ParticipantImportResult.OutcomeEnum.SKIPPED);
            }

            registrationRepository.saveAndFlush(Registration.builder()
                    .registrationCode(generateUniqueRegistrationCode(event.getEventCode()))
                    .eventId(event.getId())
                    .attendeeUsername(username)
                    .attendeeFirstName(person.getFirstName())
                    .attendeeLastName(person.getLastName())
                    .attendeeEmail(person.getEmail())
                    .attendeeCompanyId(person.getCompanyId())
                    // API enum UPPER_CASE, database lower case (coding standards)
                    .status(person.getStatus().getValue().toLowerCase(Locale.ROOT))
                    .registrationDate(Instant.now())
                    .build());
            return result.outcome(ParticipantImportResult.OutcomeEnum.REGISTERED);
        } catch (RuntimeException e) {
            log.warn("Participant import failed for {} on event {}: {}",
                    LoggingUtils.maskEmail(person.getEmail()), event.getEventCode(), e.getMessage());
            return result.outcome(ParticipantImportResult.OutcomeEnum.FAILED)
                    .reason(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
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
     * Send the "registration confirmed" mail (with the calendar invite) for a registration that has
     * just moved from pending to confirmed. Called by both confirmation paths: the confirmation link
     * and the ACCEPT email reply. Never throws: a failed lookup or send must not undo the
     * confirmation, which is already persisted.
     *
     * @param registration the confirmed registration
     * @param event        the registration's event
     */
    public void notifyRegistrationConfirmed(Registration registration, Event event) {
        try {
            UserResponse user = userApiClient.getUserByUsername(registration.getAttendeeUsername());
            String pref = userApiClient.getPreferredLanguage(registration.getAttendeeUsername());
            Locale locale = (pref == null || pref.toLowerCase(Locale.ROOT).startsWith("de"))
                    ? Locale.GERMAN : Locale.ENGLISH;
            registrationEmailService.sendRegistrationConfirmed(registration, user, event, locale);
        } catch (Exception e) {
            log.error("Could not send registration-confirmed email for registration {}",
                    registration.getRegistrationCode(), e);
        }
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
                            eventRepository.findByEventCode(eventCode)
                                    .ifPresent(event -> notifyRegistrationConfirmed(registration, event));
                        },
                        () -> log.info("confirmByEmail: no active registration found for {}*** / {}",
                                email.substring(0, Math.min(5, email.length())), eventCode)
            );
    }

    /**
     * Cancels a registration at the attendee's request (link, hero "Abmelden").
     *
     * <p>#1238 E2: refused from the event day on ({@link DeregistrationClosedException}); after the
     * registration deadline the freed place is not given to the waitlist, because the name badges
     * are printed by then. Before the deadline the waitlist moves up as before.
     */
    @Transactional
    public void cancelRegistration(Registration registration) {
        Event event = eventRepository.findById(registration.getEventId()).orElse(null);
        Instant now = Instant.now();
        if (event != null && RegistrationWindow.isEventDayReached(event, now)) {
            throw new DeregistrationClosedException(event.getEventCode());
        }
        registration.setStatus("cancelled");
        registrationRepository.save(registration);
        if (event != null && RegistrationWindow.isDeadlinePassed(event, now)) {
            log.info("Registration {} cancelled after the deadline of {}; no waitlist promotion (#1238)",
                    registration.getRegistrationCode(), event.getEventCode());
            return;
        }
        waitlistPromotionService.promoteFromWaitlist(registration.getEventId());
        log.info("Registration {} cancelled; waitlist promotion triggered for event {}",
                registration.getRegistrationCode(), registration.getEventId());
    }

    /**
     * Create a system-managed confirmed registration for a known user (organizer or partner).
     * <p>
     * Skips email confirmation, capacity checks, and waitlist logic — these users always
     * receive a confirmed spot.
     *
     * @param event the event entity
     * @param user  the UserResponse for the user to auto-enroll
     * @return true if a new registration was created, false if the user was already registered
     */
    @Transactional
    public boolean createInternalRegistration(Event event, ch.batbern.events.dto.generated.users.UserResponse user) {
        String username = user.getId();

        if (registrationRepository.existsByEventIdAndAttendeeUsername(event.getId(), username)) {
            log.debug("Auto-enrollment skipped: {} already registered for event {}", username, event.getEventCode());
            return false;
        }

        String registrationCode = generateUniqueRegistrationCode(event.getEventCode());

        // Mark this as a programmatic registration so it is NOT counted as a real attendee
        // (organizers/partners are auto-enrolled on every event — they must never block the
        // event from being deleted; see EventController.deleteEvent + realAttendeeCount).
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Registration.AUTO_REGISTERED_FROM_KEY, Registration.TRIGGER_STAKEHOLDER_ENROLLMENT);

        Registration registration = Registration.builder()
                .registrationCode(registrationCode)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName(user.getFirstName())
                .attendeeLastName(user.getLastName())
                .attendeeEmail(user.getEmail())
                .attendeeCompanyId(user.getCompanyId())
                .status("confirmed")
                .registrationDate(Instant.now())
                .metadata(metadata)
                .build();

        registrationRepository.save(registration);
        log.info("Auto-enrolled {} as confirmed (programmatic) participant for event {}",
                username, event.getEventCode());
        return true;
    }

    /**
     * Closes PUBLIC self-registration AND waitlist once the registration deadline has passed
     * (feedback #1). Falls back to the event start date when no explicit deadline is set, so a
     * missing deadline never leaves registration open forever. Called before capacity branching,
     * so a full event past its deadline returns REGISTRATION_CLOSED rather than a waitlist spot.
     * The organizer {@link #addParticipant} path deliberately bypasses this.
     *
     * @throws RegistrationClosedException when {@code now} is after the (deadline ?: event date)
     */
    private void assertRegistrationOpen(Event event) {
        if (RegistrationWindow.isDeadlinePassed(event, Instant.now())) {
            throw new RegistrationClosedException(event.getEventCode(), RegistrationWindow.deadline(event));
        }
    }

    /**
     * Organizer "Add participant": add an EXISTING user onto an event directly as a
     * {@code confirmed} participant — no self-registration / email-confirmation step.
     *
     * <p>Unlike {@link #createInternalRegistration} (stakeholder auto-enrol), this is a REAL
     * attendee: the row carries NO {@link Registration#AUTO_REGISTERED_FROM_KEY} marker, so it
     * counts toward capacity and the event delete-guard. Capacity is respected unless
     * {@code force} is set (organizer override). A {@code cancelled} prior registration is
     * replaced; an active one is rejected (409 via {@link IllegalStateException}).
     *
     * @param event   the event entity (loaded by the controller)
     * @param username the chosen existing user's username (= {@code UserResponse.id})
     * @param force   add over capacity when the event is full
     * @param notify  send the attendee a "you have a confirmed spot" email
     * @param addedBy the organizer username performing the add (audit metadata)
     * @return the created confirmed registration
     * @throws UserNotFoundException                  unknown username (→ 404)
     * @throws IllegalStateException                  user already actively registered (→ 409)
     * @throws RegistrationCapacityExceededException  event full and {@code force=false} (→ 409)
     */
    @Transactional
    public Registration addParticipant(Event event, String username, boolean force,
                                       boolean notify, String addedBy) {
        // Resolve the existing user (UserNotFoundException → 404).
        ch.batbern.events.dto.generated.users.UserResponse user =
                userApiClient.getUserByUsername(username);

        // Dedupe (mirror the authenticated path): replace a cancelled row, reject an active one.
        Optional<Registration> existing =
                registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), username);
        if (existing.isPresent()) {
            Registration reg = existing.get();
            if ("cancelled".equalsIgnoreCase(reg.getStatus())) {
                registrationRepository.delete(reg);
                registrationRepository.flush(); // free the (event_id, username) unique slot before re-insert
            } else {
                throw new IllegalStateException(
                        "User " + username + " is already registered for event " + event.getEventCode());
            }
        }

        // Capacity: organizer may override with force; otherwise a full event is rejected (409).
        Integer capacity = event.getRegistrationCapacity();
        if (capacity != null && !force) {
            long activeCount = registrationRepository.countByEventIdAndStatusIn(
                    event.getId(), Registration.CAPACITY_STATUSES);
            if (activeCount >= capacity) {
                throw new RegistrationCapacityExceededException(
                        event.getEventCode(), activeCount, capacity);
            }
        }

        // Real attendee — NO AUTO_REGISTERED_FROM_KEY (counts for capacity + delete-guard); audit
        // who added them.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("addedByOrganizer", addedBy);

        Registration registration = Registration.builder()
                .registrationCode(generateUniqueRegistrationCode(event.getEventCode()))
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName(user.getFirstName())
                .attendeeLastName(user.getLastName())
                .attendeeEmail(user.getEmail())
                .attendeeCompanyId(user.getCompanyId())
                .status("confirmed")
                .registrationDate(Instant.now())
                .metadata(metadata)
                .build();

        Registration saved = registrationRepository.save(registration);
        log.info("Organizer {} added {} as confirmed participant for event {} (force={})",
                addedBy, username, event.getEventCode(), force);

        // Notify with a dedicated "added by the organizing team" confirmation: the place is
        // already confirmed, so this is NOT the double-opt-in registration-confirmation email and
        // NOT the waitlist-promotion email (no "confirm please", no waitlist story, no reg code).
        if (notify) {
            String pref = userApiClient.getPreferredLanguage(username);
            Locale locale = (pref != null && pref.toLowerCase(Locale.ROOT).startsWith("de"))
                    ? Locale.GERMAN : Locale.ENGLISH;
            registrationEmailService.sendOrganizerAddedConfirmation(saved, user, event, locale);
        }

        return saved;
    }

    /**
     * Create a registration for an already-authenticated attendee (quick registration flow).
     * <p>
     * Differs from {@link #createRegistration} in that:
     * - The user profile is fetched by username (user already exists in the system)
     * - Non-waitlist registrations start as "confirmed" (no email confirmation step)
     * - A confirmed registration gets the registration-confirmed mail with the calendar invite,
     *   like every other confirmed attendee (#1097); a waitlisted one gets the waitlist mail,
     *   like an anonymous waitlist registration
     * - Capacity/waitlist logic still applies
     *
     * @param eventCode event code (e.g., "BATbern42")
     * @param username  the Cognito username from the JWT (custom:username claim)
     * @return the created (or existing) registration
     * @throws NoSuchElementException  if the event is not found
     * @throws IllegalStateException   if the user is already actively registered
     */
    @Transactional
    public Registration createRegistrationForAuthenticatedUser(
            String eventCode, String username, String fallbackEmail) {
        log.debug("Creating authenticated registration for event: {} by user: {}", eventCode, username);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));

        // Feedback #1: close public registration AND waitlist once the deadline has passed.
        assertRegistrationOpen(event);

        // User profile enriches the registration cache fields but may not exist for
        // ATTENDEEs who were created directly in Cognito. Fall back gracefully.
        ch.batbern.events.dto.generated.users.UserResponse userProfile = null;
        try {
            userProfile = userApiClient.getUserByUsername(username);
        } catch (ch.batbern.events.exception.UserNotFoundException e) {
            log.info("User profile not found for username: {} — using JWT email as fallback",
                    username);
        }

        // The authenticated path used to trust whatever was on user_profile,
        // even an empty first_name / last_name (2026-05-18 incident: 20
        // BATbern59 registrations with blank attendee names from JIT-created
        // user.X / firstname.lastname.2 accounts). Reject early with a clear
        // 409 so the frontend can prompt the user to complete their profile
        // and retry. Only enforced when we DO have a profile — anonymous-
        // user fallback (userProfile == null) still goes through the
        // public-form path elsewhere.
        if (userProfile != null) {
            boolean missingFirst = userProfile.getFirstName() == null
                    || userProfile.getFirstName().isBlank();
            boolean missingLast = userProfile.getLastName() == null
                    || userProfile.getLastName().isBlank();
            if (missingFirst || missingLast) {
                throw new ch.batbern.events.exception.IncompleteProfileException(
                        username, missingFirst, missingLast);
            }
        }

        Optional<Registration> existing = registrationRepository
                .findByEventIdAndAttendeeUsername(event.getId(), username);
        if (existing.isPresent()) {
            Registration reg = existing.get();
            if ("cancelled".equalsIgnoreCase(reg.getStatus())) {
                registrationRepository.delete(reg);
                log.info("Deleted cancelled registration for event: {} by user: {}, allowing re-registration",
                        eventCode, username);
            } else {
                log.warn("Duplicate authenticated registration attempt: event={} user={} status={}",
                        eventCode, username, reg.getStatus());
                throw new IllegalStateException(
                        "User " + username + " is already registered for event " + eventCode);
            }
        }

        String registrationCode = generateUniqueRegistrationCode(eventCode);

        Integer capacity = event.getRegistrationCapacity();
        String registrationStatus = "confirmed"; // authenticated users skip the email-confirmation step
        Integer waitlistPosition = null;
        if (capacity != null) {
            long activeCount = registrationRepository.countByEventIdAndStatusIn(
                    event.getId(), Registration.CAPACITY_STATUSES);
            if (activeCount >= capacity) {
                registrationStatus = "waitlist";
                waitlistPosition = registrationRepository.getNextWaitlistPosition(event.getId());
                log.info("Event {} is at capacity, placing authenticated user {} on waitlist at position {}",
                        eventCode, username, waitlistPosition);
            }
        }

        Registration registration = Registration.builder()
                .registrationCode(registrationCode)
                .eventId(event.getId())
                .eventCode(eventCode)
                .attendeeUsername(username)
                .attendeeFirstName(userProfile != null ? userProfile.getFirstName() : null)
                .attendeeLastName(userProfile != null ? userProfile.getLastName() : null)
                .attendeeEmail(userProfile != null ? userProfile.getEmail() : fallbackEmail)
                .attendeeCompanyId(userProfile != null ? userProfile.getCompanyId() : null)
                .status(registrationStatus)
                .waitlistPosition(waitlistPosition)
                .deregistrationToken(UUID.randomUUID())
                .registrationDate(Instant.now())
                .build();

        Registration saved = registrationRepository.save(registration);
        log.info("Created authenticated registration: {} for user: {} at event: {} (status={})",
                registrationCode, username, eventCode, registrationStatus);
        if ("confirmed".equals(registrationStatus)) {
            notifyRegistrationConfirmed(saved, event);
        } else {
            waitlistPromotionEmailService.sendWaitlistConfirmationEmail(saved);
        }
        return saved;
    }

    /**
     * Enroll all organizers and partners as confirmed participants for the given event.
     * <p>
     * Fetches all users with ORGANIZER and PARTNER roles, then calls
     * {@link #createInternalRegistration} for each. Already-registered users are skipped
     * (idempotent). Per-user failures are logged but do not abort the batch.
     *
     * @param event the event to enroll stakeholders for
     * @return summary of how many users were enrolled vs skipped
     */
    @Transactional
    public EnrollmentSummary enrollStakeholders(Event event) {
        List<String> usernames = new ArrayList<>();
        try {
            usernames.addAll(userApiClient.getOrganizerUsernames());
        } catch (Exception e) {
            log.warn("Could not fetch organizer list for event {}: {}", event.getEventCode(), e.getMessage());
        }
        try {
            usernames.addAll(userApiClient.getPartnerUsernames());
        } catch (Exception e) {
            log.warn("Could not fetch partner list for event {}: {}", event.getEventCode(), e.getMessage());
        }

        int enrolled = 0;
        int skipped = 0;
        for (String username : usernames) {
            try {
                ch.batbern.events.dto.generated.users.UserResponse user = userApiClient.getUserByUsername(username);
                if (createInternalRegistration(event, user)) {
                    enrolled++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Stakeholder enrollment failed for user {} on event {}: {}",
                        username, event.getEventCode(), e.getMessage());
                skipped++;
            }
        }

        log.info("Stakeholder enrollment for event {}: enrolled={}, skipped={}",
                event.getEventCode(), enrolled, skipped);
        return new EnrollmentSummary(enrolled, skipped);
    }

    /**
     * Summary returned by {@link #enrollStakeholders}.
     *
     * @param enrolled number of users newly registered
     * @param skipped  number of users already registered or that failed
     */
    public record EnrollmentSummary(int enrolled, int skipped) {}

    private String generateUniqueRegistrationCode(String eventCode) {
        return RegistrationCodes.newUnique(eventCode, registrationRepository::existsByRegistrationCode);
    }
}
