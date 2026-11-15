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
import ch.batbern.events.exception.EventHasRealRegistrationsException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.shared.utils.EmailAddresses;
import ch.batbern.events.exception.RegistrationAlreadyProcessedException;
import ch.batbern.events.exception.RegistrationCapacityExceededException;
import ch.batbern.events.exception.DeregistrationClosedException;
import ch.batbern.events.exception.RegistrationClosedException;
import ch.batbern.events.exception.RegistrationNotFoundException;
import ch.batbern.events.exception.RegistrationProfileIncompleteException;
import ch.batbern.events.exception.RegistrationStatusTransitionException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.specification.RegistrationSpecification;
import ch.batbern.shared.api.DeterministicPaging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;
    private final RegistrationEmailService registrationEmailService;
    private final NewsletterSubscriberService newsletterSubscriberService;
    private final WaitlistPromotionEmailService waitlistPromotionEmailService; // Story 10.11
    private final WaitlistPromotionService waitlistPromotionService; // Story 10.12
    private final PersonAddresses personAddresses; // #1183
    private final RegistrationConfirmationSender confirmationSender; // #1315

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
                // #1315: a pending registration gets its confirmation mail again (fresh link); 200.
                log.info("Found pending registration for event: {} by user: {}, resending confirmation email",
                        eventCode, username);
                registration.setEventCode(eventCode); // Set transient field for API response
                registration.setCompanyUpdated(companyUpdated);
                confirmationSender.send(registration, event);
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

        // Story 10.11 (T10.3): a waitlist registration gets the waitlist mail, a pending one the
        // confirmation link (Story 4.1.5c; #1315 one sender, in the attendee's language).
        if ("waitlist".equals(saved.getStatus())) {
            waitlistPromotionEmailService.sendWaitlistConfirmationEmail(saved);
        } else {
            confirmationSender.send(saved, event);
        }

        return saved;
    }

    /**
     * One attendee's registrations across all events, newest event first, each enriched with its
     * event (code, title, date) and the attendee from CUMS. Story 4.10b, moved from
     * {@code EventController} ({@code GET /registrations?filter=…}); the events of a page are
     * loaded in one query.
     */
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> listForAttendee(
            String attendeeUsername, Pageable pageable) {
        Page<Registration> page =
                registrationRepository.findPageByAttendeeUsernameNewestEventFirst(attendeeUsername, pageable);

        Set<UUID> eventIds = page.getContent().stream()
                .map(Registration::getEventId)
                .collect(Collectors.toSet());
        Map<UUID, Event> events = new HashMap<>();
        eventRepository.findAllById(eventIds).forEach(e -> events.put(e.getId(), e));

        return page.map(registration -> {
            Event event = events.get(registration.getEventId());
            if (event != null) {
                registration.setEventCode(event.getEventCode()); // transient field
            }
            RegistrationResponse response = enrichRegistrationWithUserData(registration);
            if (event != null) {
                response.setEventTitle(event.getTitle());
                response.setEventDate(event.getDate() != null ? event.getDate().toString() : null);
            }
            return response;
        });
    }

    /**
     * One event's registrations, filtered in SQL (status, search over the denormalized attendee
     * fields, company), sorted by last and first name with the id as tie-breaker (#1131), each
     * enriched with the attendee from CUMS. Story 17.13, moved from {@code EventController}.
     *
     * @param page 1-based page number
     */
    // No transaction: one short query, then up to 100 CUMS calls that must not hold a DB connection.
    public Page<RegistrationResponse> listForEvent(Event event, List<String> statuses, String search,
            String companyId, int page, int limit) {
        Specification<Registration> spec = RegistrationSpecification.buildSpecification(
                event.getId(), statuses, search, companyId);
        Pageable pageable = DeterministicPaging.pageRequest(page - 1, limit,
                Sort.by(Sort.Order.asc("attendeeLastName"), Sort.Order.asc("attendeeFirstName")));
        return registrationRepository.findAll(spec, pageable).map(registration -> {
            registration.setEventCode(event.getEventCode()); // transient field
            return enrichRegistrationWithUserData(registration);
        });
    }

    /**
     * Live registration counts of one event (Story 10.11), moved from {@code EventController}
     * (Story 17.13). Plain counts that need no transaction of their own. Called inside
     * {@code createEvent} they run in its transaction, right after the event is saved; that is why
     * {@link RegistrationRepository#countRealAttendees} uses flushMode COMMIT.
     */
    public RegistrationCounts countsFor(UUID eventId) {
        return new RegistrationCounts(
                registrationRepository.countByEventIdAndStatusIn(eventId, Registration.CONFIRMED_STATUSES),
                registrationRepository.countByEventIdAndStatus(eventId, "waitlist"),
                registrationRepository.countRealAttendees(eventId, Registration.ACTIVE_STATUSES));
    }

    /**
     * Active registrations (registered, confirmed, ...) per event, in one query; an event without
     * any maps to 0. Drives {@code currentAttendeeCount} of {@code include=registrations}.
     */
    public Map<UUID, Long> activeCounts(Collection<UUID> eventIds) {
        Map<UUID, Long> counts = new HashMap<>();
        eventIds.forEach(id -> counts.put(id, 0L));
        if (!eventIds.isEmpty()) {
            registrationRepository.countPerEventByStatusIn(eventIds, Registration.ACTIVE_STATUSES)
                    .forEach(row -> counts.put(row.getEventId(), row.getRegistrations()));
        }
        return counts;
    }

    /**
     * An event with real attendees cannot be deleted (409): only self-registered attendees count.
     * Programmatic registrations (organizers and partners enrolled at creation, auto-registered
     * speakers) never block, otherwise no event would be deletable; the delete cascades them.
     *
     * @throws EventHasRealRegistrationsException when the event has real attendees
     */
    public void requireNoRealAttendees(String eventCode, UUID eventId) {
        long realAttendees = registrationRepository.countRealAttendees(eventId, Registration.ACTIVE_STATUSES);
        if (realAttendees > 0) {
            throw new EventHasRealRegistrationsException(eventCode, realAttendees);
        }
    }

    /**
     * Registration counts of one event.
     *
     * @param confirmed     registrations that hold a place ({@link Registration#CONFIRMED_STATUSES})
     * @param waitlisted    registrations on the waitlist
     * @param realAttendees active registrations that are not programmatic enrollments
     */
    public record RegistrationCounts(long confirmed, long waitlisted, long realAttendees) {}

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

    /** Import statuses of a person (API values); the database stores them lower case. */
    private static final Set<String> IMPORT_STATUSES = Set.of("REGISTERED", "ATTENDED", "CANCELLED");
    private static final int MAX_NAME = 100;
    static final String IMPORT_FAILED_REASON = "Could not register this person";
    static final String EMAIL_LINKED_REASON =
            "This e-mail is linked to an existing account; add that user by name instead";

    private ParticipantImportResult importParticipant(Event event, ParticipantImportPerson person) {
        ParticipantImportResult result = new ParticipantImportResult()
                .email(person.getEmail() != null ? person.getEmail() : "");
        String invalid = invalidImportPerson(person);
        if (invalid != null) {
            return result.outcome(ParticipantImportResult.OutcomeEnum.FAILED).reason(invalid);
        }
        try {
            GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
            userRequest.setEmail(person.getEmail());
            userRequest.setFirstName(person.getFirstName().trim());
            userRequest.setLastName(person.getLastName().trim());
            userRequest.setCompanyId(person.getCompanyId());
            userRequest.setCognitoSync(false); // ADR-005: historical attendee, no login
            GetOrCreateUserResponse created = userApiClient.getOrCreateUser(userRequest);
            String username = created.getUsername();
            result.username(username);

            if (registrationRepository.existsByEventIdAndAttendeeUsername(event.getId(), username)) {
                return result.outcome(ParticipantImportResult.OutcomeEnum.SKIPPED);
            }

            // The cache fields come from the profile CUMS returned, as on every other creation path:
            // an existing user keeps their own name, address and company.
            UserResponse user = created.getUser();
            registrationRepository.saveAndFlush(Registration.builder()
                    .registrationCode(generateUniqueRegistrationCode(event.getEventCode()))
                    .eventId(event.getId())
                    .attendeeUsername(username)
                    .attendeeFirstName(user.getFirstName())
                    .attendeeLastName(user.getLastName())
                    .attendeeEmail(user.getEmail())
                    .attendeeCompanyId(user.getCompanyId())
                    // API value UPPER_CASE, database lower case (coding standards)
                    .status(person.getStatus().toLowerCase(Locale.ROOT))
                    .registrationDate(Instant.now())
                    .build());
            return result.outcome(ParticipantImportResult.OutcomeEnum.REGISTERED);
        } catch (UserServiceException e) {
            // CUMS get-or-create answers 409 only for EMAIL_LINKED_TO_ACCOUNT (cums-internal spec).
            boolean linked = Integer.valueOf(409).equals(e.getStatusCode());
            log.warn("Participant import failed for {} on event {}: {}",
                    LoggingUtils.maskEmail(person.getEmail()), event.getEventCode(), e.getMessage(), e);
            return result.outcome(ParticipantImportResult.OutcomeEnum.FAILED)
                    .reason(linked ? EMAIL_LINKED_REASON : IMPORT_FAILED_REASON);
        } catch (RuntimeException e) {
            // Database or other internal errors: the details go to the log, never to the caller.
            log.error("Participant import failed for {} on event {}",
                    LoggingUtils.maskEmail(person.getEmail()), event.getEventCode(), e);
            return result.outcome(ParticipantImportResult.OutcomeEnum.FAILED).reason(IMPORT_FAILED_REASON);
        }
    }

    /**
     * The per-person rules of the import (checked here, not by the request schema, so one bad row
     * fails alone): the one e-mail rule (#1102), names non-blank and at most 100 characters,
     * company at most 100, status one of {@link #IMPORT_STATUSES}.
     *
     * @return the reason the person is refused, or {@code null} when the person is valid
     */
    private static String invalidImportPerson(ParticipantImportPerson person) {
        if (person.getEmail() == null || !EmailAddresses.isAcceptable(person.getEmail())) {
            return "Invalid e-mail address";
        }
        if (isBlankOrLong(person.getFirstName()) || isBlankOrLong(person.getLastName())) {
            return "First and last name are required (at most " + MAX_NAME + " characters)";
        }
        if (person.getCompanyId() != null && person.getCompanyId().length() > MAX_NAME) {
            return "Company is longer than " + MAX_NAME + " characters";
        }
        if (person.getStatus() == null || !IMPORT_STATUSES.contains(person.getStatus())) {
            return "Status must be REGISTERED, ATTENDED or CANCELLED";
        }
        return null;
    }

    private static boolean isBlankOrLong(String value) {
        return value == null || value.isBlank() || value.length() > MAX_NAME;
    }

    /**
     * The organizer's status changes (#1315): from a status to the statuses it may change to. The
     * current status is always allowed (no change); nothing else is. Nothing changes to
     * {@code registered} or {@code waitlist} here: those are set by registration and promotion.
     * {@code attended} and {@code no_show} reach {@code confirmed} and {@code cancelled} as
     * corrections after the event; BATbern60 attendance was recorded from {@code registered}.
     * The side effects are in {@link #changeStatus}.
     */
    static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "registered", Set.of("confirmed", "cancelled", "attended", "no_show"),
            "waitlist", Set.of("confirmed", "cancelled"),
            "confirmed", Set.of("cancelled", "attended", "no_show"),
            "attended", Set.of("confirmed", "cancelled", "no_show"),
            "no_show", Set.of("confirmed", "cancelled", "attended"),
            "cancelled", Set.of());

    /**
     * The registration with this code, if it belongs to this event: 404 otherwise, the same answer
     * for an unknown code and for a code of another event (its existence is not revealed).
     */
    @Transactional(readOnly = true)
    public Registration findOfEvent(Event event, String registrationCode) {
        return registrationRepository.findByRegistrationCode(registrationCode)
                .filter(registration -> event.getId().equals(registration.getEventId()))
                .orElseThrow(() -> new RegistrationNotFoundException(registrationCode));
    }

    /**
     * The user's registration for the event that is not cancelled, for their own cancellation
     * ({@code DELETE /events/{code}/my-registration}).
     *
     * @throws NoSuchElementException none (404)
     */
    @Transactional(readOnly = true)
    public Registration findActiveOfUser(Event event, String username) {
        return registrationRepository.findByEventIdAndAttendeeUsername(event.getId(), username)
                .filter(registration -> !"cancelled".equalsIgnoreCase(registration.getStatus()))
                .orElseThrow(() -> new NoSuchElementException("No active registration found for user " + username
                        + " at event " + event.getEventCode()));
    }

    /**
     * Organizer status change ({@code PATCH /events/{code}/registrations/{registrationCode}}), by the
     * table {@link #STATUS_TRANSITIONS}, in one transaction with its side effects (#1315):
     * <ul>
     *   <li>{@code registered} to {@code confirmed}: refused while more registrations hold a place
     *       than the capacity allows (this one counted);</li>
     *   <li>{@code registered} or {@code confirmed} to {@code cancelled}: the freed place goes to the
     *       waitlist ({@link WaitlistPromotionService#promoteFromWaitlist}, which joins this
     *       transaction);</li>
     *   <li>{@code waitlist} to {@code confirmed}: the promote endpoint
     *       ({@link WaitlistPromotionService#manuallyPromote}); the registration ends {@code registered};</li>
     *   <li>{@code waitlist} to {@code cancelled}: leaves the queue, the positions behind it move up;</li>
     *   <li>every other allowed change sets the status only.</li>
     * </ul>
     * No target, or the current status, writes nothing.
     *
     * @param target the new status, lower case (the spec enum), or {@code null}
     * @return the registration as it is now
     * @throws RegistrationNotFoundException          no registration with this code in this event (404)
     * @throws RegistrationStatusTransitionException   the change is not in the table (409)
     * @throws RegistrationCapacityExceededException   confirming would keep the event over capacity (409)
     */
    @Transactional
    public Registration changeStatus(Event event, String registrationCode, String target) {
        Registration registration = findOfEvent(event, registrationCode);
        String from = registration.getStatus();
        if (target == null || target.equals(from)) {
            return registration;
        }
        if (!STATUS_TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            throw new RegistrationStatusTransitionException(registrationCode, from, target);
        }

        if ("waitlist".equals(from)) {
            if ("confirmed".equals(target)) {
                waitlistPromotionService.manuallyPromote(registrationCode); // same entity, now registered
            } else {
                registration.setStatus(target);
                registration.setWaitlistPosition(null);
                registrationRepository.save(registration);
                renumberWaitlist(event.getId());
            }
        } else {
            if ("registered".equals(from) && "confirmed".equals(target)) {
                assertCapacityForConfirmation(event);
            }
            registration.setStatus(target);
            registrationRepository.save(registration);
            if ("cancelled".equals(target) && Registration.CAPACITY_STATUSES.contains(from)) {
                waitlistPromotionService.promoteFromWaitlist(event.getId());
            }
        }
        log.info("Organizer changed registration {} from {} to {}", registrationCode, from, registration.getStatus());
        return registration;
    }

    /**
     * Organizer delete ({@code DELETE /events/{code}/registrations/{registrationCode}}), in one
     * transaction with its waitlist effect (#1315): a place holder ({@code registered},
     * {@code confirmed}) frees its place for the waitlist; a waitlisted registration leaves the
     * queue and the positions behind it move up; any other status is only deleted.
     *
     * @throws RegistrationNotFoundException no registration with this code in this event (404)
     */
    @Transactional
    public void deleteByOrganizer(Event event, String registrationCode) {
        Registration registration = findOfEvent(event, registrationCode);
        String status = registration.getStatus();
        registrationRepository.delete(registration);
        if (Registration.CAPACITY_STATUSES.contains(status)) {
            waitlistPromotionService.promoteFromWaitlist(event.getId());
        } else if ("waitlist".equals(status)) {
            renumberWaitlist(event.getId());
        }
        log.info("Organizer deleted registration {} ({}) of event {}", registrationCode, status, event.getEventCode());
    }

    /** The event's place holders, the pending one to confirm counted, fit its capacity. */
    private void assertCapacityForConfirmation(Event event) {
        Integer capacity = event.getRegistrationCapacity();
        if (capacity == null) {
            return;
        }
        long holders = registrationRepository.countByEventIdAndStatusIn(event.getId(), Registration.CAPACITY_STATUSES);
        if (holders > capacity) {
            throw RegistrationCapacityExceededException.forConfirmation(event.getEventCode(), holders, capacity);
        }
    }

    /**
     * Numbers the event's waitlist 1, 2, 3, ... in its current order, after an organizer took a
     * registration off it (#1315). The query flushes the change that left the queue first.
     */
    private void renumberWaitlist(UUID eventId) {
        List<Registration> waiting = registrationRepository.findWaitlistByEventIdOrdered(eventId);
        for (int i = 0; i < waiting.size(); i++) {
            Registration registration = waiting.get(i);
            if (!Integer.valueOf(i + 1).equals(registration.getWaitlistPosition())) {
                registration.setWaitlistPosition(i + 1);
                registrationRepository.save(registration);
            }
        }
    }

    /**
     * The organizer's resend of the confirmation mail of a pending registration, with a fresh link.
     *
     * @return the attendee it goes to, or empty when CUMS has no address for them (nothing sent)
     */
    public Optional<UserResponse> resendConfirmation(Registration registration, Event event) {
        return confirmationSender.send(registration, event);
    }

    /** What a confirmation link did: confirmed a pending registration, or found it confirmed already. */
    public enum ConfirmationResult { CONFIRMED, ALREADY_CONFIRMED }

    /**
     * Confirms a pending registration with the (already validated) confirmation link (Story 4.1.5c).
     * {@code registered} becomes {@code confirmed} and gets the confirmed mail with the calendar
     * invite; {@code confirmed} answers {@link ConfirmationResult#ALREADY_CONFIRMED} without a mail.
     * Any other status was processed after the link was sent (cancelled, waitlisted, attended,
     * no-show): the old link must not revive it (#1315).
     *
     * @throws NoSuchElementException                 unknown registration or its event (404)
     * @throws IllegalArgumentException               the registration belongs to another event (400)
     * @throws RegistrationAlreadyProcessedException  the registration is no longer pending (409)
     */
    @Transactional
    public ConfirmationResult confirmRegistration(String eventCode, UUID registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new NoSuchElementException("Registration not found: " + registrationId));
        Event event = eventRepository.findById(registration.getEventId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Event not found for registration: " + registrationId));
        if (!event.getEventCode().equals(eventCode)) {
            throw new IllegalArgumentException("Registration does not belong to event: " + eventCode);
        }

        String status = registration.getStatus();
        if ("confirmed".equals(status)) {
            return ConfirmationResult.ALREADY_CONFIRMED;
        }
        if (!"registered".equals(status)) {
            throw new RegistrationAlreadyProcessedException(registration.getRegistrationCode(), status);
        }

        registration.setStatus("confirmed");
        registrationRepository.save(registration);
        log.info("Registration confirmed: {} for event: {}", registration.getRegistrationCode(), eventCode);

        // The pending mail carries no calendar invite; it comes with the confirmed mail.
        notifyRegistrationConfirmed(registration, event);
        return ConfirmationResult.CONFIRMED;
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
     * - RegistrationController ({@code DELETE /events/{eventCode}/my-registration}, signed-in attendee)
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
        // event from being deleted; see requireNoRealAttendees + realAttendeeCount).
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
                throw new RegistrationProfileIncompleteException(
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
