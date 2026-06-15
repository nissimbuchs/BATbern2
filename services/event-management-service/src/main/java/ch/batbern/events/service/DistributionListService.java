package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves the email addresses behind the per-event mailing aliases.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * (F2). Three kinds are supported:
 * <ul>
 *   <li>{@code speakers} → {@code batbern{N}-speaker@}: PRIMARY_SPEAKER of every scheduled
 *       session of the event, plus each speaker's verified additionalEmails.</li>
 *   <li>{@code moderator} → {@code batbern{N}-moderator@}: the event lead organizer
 *       ({@code Event.organizerUsername}), plus their additionalEmails. BATbern's "event
 *       moderator" interpretation; {@code SessionUser.MODERATOR} is per-session and not
 *       targeted here.</li>
 *   <li>{@code participants} → {@code batbern{N}-participants@}: every active registrant of the
 *       event (registered/confirmed/attended). Lets organizers email all participants directly
 *       ("Way 2"), reaching the same audience as the Communications → Event registrants send.</li>
 * </ul>
 *
 * <p>All output emails are lowercased and deduplicated, preserving insertion order so the
 * primary address comes first. Missing users (UserApiClient 404) are skipped with a WARN log
 * so a stale username never crashes the inbound-email forwarder.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributionListService {

    private final EventRepository eventRepository;
    private final SessionUserRepository sessionUserRepository;
    private final RegistrationRepository registrationRepository;
    private final UserApiClient userApiClient;

    /**
     * Resolve speakers list for an event.
     *
     * @param eventCode event code (e.g. {@code "BATbern57"})
     * @return ordered, lowercased, deduplicated email set
     * @throws NotFoundException event not found
     */
    @Transactional(readOnly = true)
    public Set<String> resolveSpeakers(String eventCode) {
        Event event = loadEvent(eventCode);
        List<SessionUser> speakers =
                sessionUserRepository.findScheduledPrimarySpeakersByEventId(event.getId());

        Set<String> result = new LinkedHashSet<>();
        for (SessionUser su : speakers) {
            collectUserEmails(su.getUsername(), result);
        }
        log.info("Resolved {} email(s) for {}-speaker distribution list", result.size(), eventCode);
        return result;
    }

    /**
     * Resolve moderator list for an event.
     *
     * @param eventCode event code (e.g. {@code "BATbern57"})
     * @return ordered, lowercased, deduplicated email set
     * @throws NotFoundException event not found
     */
    @Transactional(readOnly = true)
    public Set<String> resolveModerator(String eventCode) {
        Event event = loadEvent(eventCode);
        Set<String> result = new LinkedHashSet<>();
        collectUserEmails(event.getOrganizerUsername(), result);
        log.info("Resolved {} email(s) for {}-moderator distribution list", result.size(), eventCode);
        return result;
    }

    /**
     * Resolve the participants list for an event — every active registrant
     * ({@code registered}/{@code confirmed}/{@code attended}; excludes {@code waitlist} and
     * {@code cancelled}). Backs the {@code batbern{N}-participants@} alias ("Way 2": email all
     * registrants directly), reaching the same audience as the Communications → Event registrants
     * send ("Way 1").
     *
     * <p>Each registration's own {@code attendeeEmail} is used when present (registrations always
     * capture it), falling back to a CUMS lookup by username only when blank. Emails are
     * lowercased and deduplicated.
     *
     * @param eventCode event code (e.g. {@code "BATbern59"})
     * @return ordered, lowercased, deduplicated email set
     * @throws NotFoundException event not found
     */
    @Transactional(readOnly = true)
    public Set<String> resolveParticipants(String eventCode) {
        Event event = loadEvent(eventCode);
        List<Registration> registrants = registrationRepository.findByEventIdAndStatusIn(
                event.getId(), Registration.CONFIRMED_STATUSES);

        Set<String> result = new LinkedHashSet<>();
        for (Registration registration : registrants) {
            String email = registration.getAttendeeEmail();
            if (email != null && !email.isBlank()) {
                addLowercased(email, result);
            } else {
                collectUserEmails(registration.getAttendeeUsername(), result);
            }
        }
        log.info("Resolved {} email(s) for {}-participants distribution list", result.size(), eventCode);
        return result;
    }

    private Event loadEvent(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));
    }

    private void collectUserEmails(String username, Set<String> sink) {
        if (username == null || username.isBlank()) {
            return;
        }
        try {
            UserResponse user = userApiClient.getUserByUsername(username);
            addLowercased(user.getEmail(), sink);
            List<AdditionalEmail> additional = user.getAdditionalEmails();
            if (additional != null) {
                for (AdditionalEmail extra : additional) {
                    addLowercased(extra.getEmail(), sink);
                }
            }
        } catch (UserNotFoundException e) {
            log.warn("Distribution-list resolver: user '{}' not found in CUMS — skipping", username);
        } catch (Exception e) {
            // Review patch #2 — 2026-05-28. UserApiClient also throws UserServiceException
            // (5xx / timeout / network). Resolver must NOT propagate — the Lambda treats an
            // empty list as "drop the message" and a CloudWatch alarm covers high-rate drops.
            // Bubbling up here would 5xx the Lambda's HTTP call and re-deliver indefinitely.
            log.warn("Distribution-list resolver: CUMS lookup failed for '{}' — skipping: {}",
                    username, e.toString());
        }
    }

    private void addLowercased(String email, Set<String> sink) {
        if (email == null || email.isBlank()) {
            return;
        }
        sink.add(email.trim().toLowerCase(Locale.ROOT));
    }
}
