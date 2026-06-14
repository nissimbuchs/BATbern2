package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
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
 * (F2). Two kinds are supported:
 * <ul>
 *   <li>{@code speakers} → {@code batbern{N}-speaker@}: PRIMARY_SPEAKER + CO_SPEAKER of every
 *       scheduled session of the event, plus each speaker's verified additionalEmails.
 *       MODERATOR / PANELIST roles are excluded.</li>
 *   <li>{@code moderator} → {@code batbern{N}-moderator@}: the event lead organizer
 *       ({@code Event.organizerUsername}), plus their additionalEmails. BATbern's "event
 *       moderator" interpretation; {@code SessionUser.MODERATOR} is per-session and not
 *       targeted here.</li>
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
                sessionUserRepository.findScheduledSpeakersByEventId(event.getId());

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
