package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Auto-registers committed speakers as participants of their event.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * (F1). Idempotent. Called inline from speaker workflow hooks so the registration is
 * read-your-write consistent with the speaker_pool / session_users mutations that triggered it.
 *
 * <p>The four valid trigger sources are persisted verbatim in
 * {@code registrations.metadata.autoRegisteredFrom}:
 * <ul>
 *   <li>{@code POOL_ACCEPTED} — INVITED → ACCEPTED (speaker self-acceptance)</li>
 *   <li>{@code POOL_ACCEPTED_ON_BEHALF} — READY → ACCEPTED (organizer-on-behalf)</li>
 *   <li>{@code SESSION_PRIMARY_SPEAKER} — PRIMARY_SPEAKER session_users row provisioned at READY</li>
 *   <li>{@code SESSION_CO_SPEAKER} — co-speaker added to a session</li>
 * </ul>
 *
 * <p>Skip conditions (all silent — they do NOT break the calling workflow):
 * <ul>
 *   <li>Event missing or in the past</li>
 *   <li>User missing in CUMS (UserApiClient throws UserNotFoundException)</li>
 *   <li>(event_id, attendee_username) row already exists — unique constraint backs this up</li>
 * </ul>
 *
 * <p>Speakers <strong>bypass</strong> {@code registration_capacity}: they are committed by the
 * organizer, not registering through the public funnel. The auto-registration row is created
 * directly at {@code status="confirmed"} with no confirmation email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerAutoRegistrationService {

    /**
     * Speaker pool: INVITED → ACCEPTED (speaker self-acceptance via portal or magic link).
     */
    public static final String TRIGGER_POOL_ACCEPTED = "POOL_ACCEPTED";

    /**
     * Speaker pool: READY → ACCEPTED (organizer on speaker's behalf).
     */
    public static final String TRIGGER_POOL_ACCEPTED_ON_BEHALF = "POOL_ACCEPTED_ON_BEHALF";

    /**
     * Session: PRIMARY_SPEAKER session_users row provisioned (typically at the READY hook).
     */
    public static final String TRIGGER_SESSION_PRIMARY_SPEAKER = "SESSION_PRIMARY_SPEAKER";

    /**
     * Session: CO_SPEAKER added to an existing session.
     */
    public static final String TRIGGER_SESSION_CO_SPEAKER = "SESSION_CO_SPEAKER";

    private static final String REGISTRATION_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int REGISTRATION_CODE_SUFFIX_LENGTH = 6;
    private static final int MAX_COLLISION_RETRIES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;

    /**
     * Idempotently auto-register a speaker as a participant of the given event.
     *
     * <p>Safe to call from any speaker workflow hook. All failure modes are absorbed silently
     * (logged) so they cannot break the calling transaction's success path.
     *
     * @param eventId       event UUID (internal — the trigger callers already know it)
     * @param username      speaker's username (from session_users / speaker_pool resolution)
     * @param triggerSource one of the {@code TRIGGER_*} constants on this class
     */
    @Transactional
    public void autoRegisterIfAbsent(UUID eventId, String username, String triggerSource) {
        if (eventId == null || username == null || username.isBlank()) {
            log.debug("autoRegisterIfAbsent: skipping — eventId={} username={}", eventId, username);
            return;
        }

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            log.warn("autoRegisterIfAbsent: skipping — event {} not found (trigger={}, username={})",
                    eventId, triggerSource, username);
            return;
        }
        Event event = eventOpt.get();

        // Past-event skip uses a 1-day grace window so that day-of-event speaker additions
        // (e.g., last-minute co-speaker assigned at 11:00 for a 19:00 event, or accept
        // landing at 18:00 same day) still auto-register. Only events whose date is strictly
        // more than one day in the past are skipped. Review patch #3 — 2026-05-28.
        if (event.getDate() != null
                && event.getDate().plus(1, ChronoUnit.DAYS).isBefore(Instant.now())) {
            log.info("autoRegisterIfAbsent: skipping — event {} is in the past (trigger={}, username={})",
                    event.getEventCode(), triggerSource, username);
            return;
        }

        // Idempotency: the read-check is best-effort; the catch on DataIntegrityViolationException
        // below covers the concurrent-trigger race where two callers both pass this check before
        // either has saved. (The DB-level uniqueness on attendee_username is enforced by application
        // logic across all auto-reg + manual registration paths; V108 may later add a hard constraint.)
        if (registrationRepository.findByEventIdAndAttendeeUsername(eventId, username).isPresent()) {
            log.debug("autoRegisterIfAbsent: skipping — already registered (event={}, username={}, trigger={})",
                    event.getEventCode(), username, triggerSource);
            return;
        }

        UserResponse user;
        try {
            user = userApiClient.getUserByUsername(username);
        } catch (UserNotFoundException e) {
            log.warn("autoRegisterIfAbsent: user '{}' not found in CUMS — skipping (event={}, trigger={})",
                    username, event.getEventCode(), triggerSource);
            return;
        } catch (Exception e) {
            // Review patch #2 — 2026-05-28. UserApiClient also throws UserServiceException
            // (and other transient runtimes) on 5xx / timeout / network. The Javadoc promises
            // "all failure modes absorbed silently" — make it actually so, otherwise a transient
            // CUMS hiccup would roll back the calling speaker_pool transition.
            log.warn("autoRegisterIfAbsent: CUMS lookup failed for '{}' — skipping (event={}, trigger={}): {}",
                    username, event.getEventCode(), triggerSource, e.toString());
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("autoRegisteredFrom", triggerSource);

        Registration registration;
        try {
            registration = Registration.builder()
                    .registrationCode(generateUniqueRegistrationCode(event.getEventCode()))
                    .eventId(eventId)
                    .eventCode(event.getEventCode())
                    .attendeeUsername(username)
                    .attendeeFirstName(user.getFirstName())
                    .attendeeLastName(user.getLastName())
                    .attendeeEmail(user.getEmail())
                    .attendeeCompanyId(user.getCompanyId())
                    .status("confirmed")
                    .registrationDate(Instant.now())
                    .deregistrationToken(UUID.randomUUID())
                    .metadata(metadata)
                    .build();
        } catch (IllegalStateException codeCollision) {
            // Review patch #4 — 2026-05-28. Extreme bad-luck case (5 collisions on a
            // 6-char × 36-alphabet code, ~3 in 10^9). Absorb so the speaker workflow keeps moving.
            log.warn("autoRegisterIfAbsent: registration code generation failed for {} ({}): {} — skipping",
                    username, event.getEventCode(), codeCollision.getMessage());
            return;
        }

        try {
            registrationRepository.save(registration);
            log.info("Auto-registered speaker {} as participant of {} (trigger={})",
                    username, event.getEventCode(), triggerSource);
        } catch (DataIntegrityViolationException race) {
            // Review patch #4 — 2026-05-28. Concurrent trigger race: another auto-reg call
            // (or RegistrationService.createRegistration) inserted the row between our
            // findByEventIdAndAttendeeUsername check and our save. Behave idempotently.
            log.debug("autoRegisterIfAbsent: race detected — {} already registered for {} (trigger={})",
                    username, event.getEventCode(), triggerSource);
        }
    }

    private String generateUniqueRegistrationCode(String eventCode) {
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String code = eventCode + "-reg-" + generateRandomSuffix();
            if (!registrationRepository.existsByRegistrationCode(code)) {
                return code;
            }
            log.warn("Registration code collision on attempt {}: {}", attempt + 1, code);
        }
        throw new IllegalStateException(
                "Failed to generate unique registration code after " + MAX_COLLISION_RETRIES + " attempts");
    }

    private String generateRandomSuffix() {
        StringBuilder sb = new StringBuilder(REGISTRATION_CODE_SUFFIX_LENGTH);
        for (int i = 0; i < REGISTRATION_CODE_SUFFIX_LENGTH; i++) {
            sb.append(REGISTRATION_CODE_CHARS.charAt(RANDOM.nextInt(REGISTRATION_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
