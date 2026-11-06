package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the canonical participant list for an event — the single source of truth
 * for both the XLSX export ({@link ParticipantsExportService}) and the DOCX
 * name-badge export ({@link ParticipantsDocxExportService}).
 *
 * <p>Source-set union, deduplicated by {@code username} with role precedence
 * {@code ORGANIZER > SPEAKER > PARTNER > ATTENDEE} (the declaration order of {@link Role}):
 * <ol>
 *   <li>All ORGANIZER-role users from CUMS
 *       ({@link UserApiClient#getOrganizerUsernames()})</li>
 *   <li>All {@code PRIMARY_SPEAKER} + {@code CO_SPEAKER} session_users of the
 *       event</li>
 *   <li>All registrations with status in {@code [registered, confirmed, attended]}; a registrant
 *       holding the PARTNER role in CUMS ({@link UserApiClient#getPartnerUsernames()}) is a
 *       PARTNER (#1236). Being a partner alone does not put someone on the list.</li>
 * </ol>
 *
 * <p><strong>Deterministic sort</strong> applied at the end so XLSX and DOCX
 * both honour it: by role precedence ascending (organizers, speakers, partners,
 * attendees), then alphabetically by
 * {@code lastName} via a {@link Locale#GERMAN} {@link Collator} (so Swiss-
 * German umlaut handling is correct — {@code Müller} sorts before
 * {@code Niederer}), then by {@code firstName} as a tie-breaker.
 *
 * <p>Failure modes are absorbed silently (warn-logged): a CUMS hiccup mid-
 * collection contributes whatever has been gathered up to that point. Callers
 * never see exceptions from this service except {@link NotFoundException} when
 * the event itself doesn't exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantsCollector {

    /**
     * Statuses that count as participants for badge printing. Mirrors
     * {@code Registration.CONFIRMED_STATUSES} but is reproduced literally
     * here so a future change to {@code CONFIRMED_STATUSES} (e.g. adding
     * {@code "waitlist"}) does NOT silently widen the badge list.
     */
    private static final List<String> BADGE_STATUSES =
            List.of("registered", "confirmed", "attended");

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final SessionUserRepository sessionUserRepository;
    private final UserApiClient userApiClient;

    /**
     * Collect, dedupe and sort the participants for {@code eventCode}.
     *
     * @param eventCode meaningful event identifier (ADR-003)
     * @return participant rows in canonical order: role precedence then last
     *         name (German collator), never null
     * @throws NotFoundException if the event does not exist
     */
    @Transactional(readOnly = true)
    public List<ParticipantRow> collect(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // LinkedHashMap keeps an intermediate insertion order; the final sort
        // overrides it deterministically, but the map ordering keeps the
        // dedupe-with-precedence logic readable.
        Map<String, ParticipantRow> rows = new LinkedHashMap<>();

        collectOrganizers(rows);
        collectEventSpeakers(event, rows);
        collectRegisteredAttendees(event, rows);

        return sortCanonically(new ArrayList<>(rows.values()));
    }

    private void collectOrganizers(Map<String, ParticipantRow> rows) {
        List<String> usernames;
        try {
            usernames = userApiClient.getOrganizerUsernames();
        } catch (Exception e) {
            log.warn("Could not fetch organizer list for participants export: {}", e.getMessage());
            return;
        }
        for (String username : usernames) {
            try {
                UserResponse user = userApiClient.getUserByUsername(username);
                // Key uniformly by `username` so role-precedence dedupe works when the
                // same physical user appears in multiple source sets (review patch #1
                // from spec-auto-participant-email-aliases-excel-export).
                rows.put(username, new ParticipantRow(
                        user.getFirstName(),
                        user.getLastName(),
                        resolveCompany(user.getCompanyId()),
                        Role.ORGANIZER));
            } catch (UserNotFoundException ignore) {
                log.warn("Organizer username {} not resolvable — skipping", username);
            }
        }
    }

    private void collectEventSpeakers(Event event, Map<String, ParticipantRow> rows) {
        List<SessionUser> speakers =
                sessionUserRepository.findEventSpeakersByEventId(event.getId());
        for (SessionUser su : speakers) {
            String username = su.getUsername();
            if (username == null || username.isBlank()) {
                continue;
            }
            ParticipantRow existing = rows.get(username);
            if (existing != null && existing.role() == Role.ORGANIZER) {
                continue; // higher-precedence row already present
            }
            try {
                UserResponse user = userApiClient.getUserByUsername(username);
                rows.put(username, new ParticipantRow(
                        user.getFirstName(),
                        user.getLastName(),
                        resolveCompany(user.getCompanyId()),
                        Role.SPEAKER));
            } catch (UserNotFoundException ignore) {
                // Fall back to cached fields on session_users (no CUMS profile).
                rows.put(username, new ParticipantRow(
                        su.getSpeakerFirstName(),
                        su.getSpeakerLastName(),
                        "",
                        Role.SPEAKER));
            }
        }
    }

    private void collectRegisteredAttendees(Event event, Map<String, ParticipantRow> rows) {
        List<Registration> registrations =
                registrationRepository.findByEventId(event.getId()).stream()
                        .filter(r -> r.getStatus() != null
                                && BADGE_STATUSES.contains(r.getStatus().toLowerCase()))
                        .toList();
        Set<String> partners = partnerUsernames();
        for (Registration r : registrations) {
            String username = r.getAttendeeUsername();
            if (username == null || username.isBlank()) {
                continue;
            }
            ParticipantRow existing = rows.get(username);
            // ORGANIZER and SPEAKER both outrank PARTNER and ATTENDEE.
            if (existing != null
                    && (existing.role() == Role.ORGANIZER || existing.role() == Role.SPEAKER)) {
                continue;
            }
            Role role = partners.contains(username) ? Role.PARTNER : Role.ATTENDEE;
            // Prefer the live CUMS profile — historical registrations imported
            // before the denormalised attendee_first_name / attendee_last_name
            // columns existed have NULL there, so badges came out empty. Same
            // enrichment pattern collectEventSpeakers uses. UserApiClient is
            // @Cacheable so repeat exports of the same event are cheap.
            UserResponse user = null;
            try {
                user = userApiClient.getUserByUsername(username);
            } catch (UserNotFoundException ignore) {
                // Fall through to the denormalised-cache path below.
            }
            if (user != null) {
                rows.put(username, new ParticipantRow(
                        nullToEmpty(user.getFirstName()),
                        nullToEmpty(user.getLastName()),
                        resolveCompany(user.getCompanyId()),
                        role));
            } else {
                // CUMS doesn't know this user — fall back to whatever the
                // registration row's denormalised cache fields hold (legacy
                // rows may have them populated even if CUMS doesn't know the
                // user any more).
                rows.put(username, new ParticipantRow(
                        nullToEmpty(r.getAttendeeFirstName()),
                        nullToEmpty(r.getAttendeeLastName()),
                        resolveCompany(r.getAttendeeCompanyId()),
                        role));
            }
        }
    }

    /** Usernames holding the PARTNER role; empty (logged) when CUMS cannot answer, as for organizers. */
    private Set<String> partnerUsernames() {
        try {
            return new HashSet<>(userApiClient.getPartnerUsernames());
        } catch (Exception e) {
            log.warn("Could not fetch partner list for participants export, partners listed as attendees: {}",
                    e.getMessage());
            return Set.of();
        }
    }

    /** Canonical sort: role precedence, then lastName (German collator), then firstName. */
    private static List<ParticipantRow> sortCanonically(List<ParticipantRow> rows) {
        Collator german = Collator.getInstance(Locale.GERMAN);
        german.setStrength(Collator.SECONDARY); // case-insensitive but umlaut-aware
        Comparator<ParticipantRow> byRole = Comparator.comparingInt(
                r -> r.role() == null ? Integer.MAX_VALUE : r.role().ordinal());
        Comparator<ParticipantRow> byLastName = (a, b) ->
                german.compare(nullToEmpty(a.lastName()), nullToEmpty(b.lastName()));
        Comparator<ParticipantRow> byFirstName = (a, b) ->
                german.compare(nullToEmpty(a.firstName()), nullToEmpty(b.firstName()));
        rows.sort(byRole.thenComparing(byLastName).thenComparing(byFirstName));
        return rows;
    }

    /**
     * Resolve a company slug to its display name for badge rendering.
     *
     * <p>Per-slug cached (15 min) in {@code userApiCache} via
     * {@link UserApiClient#getCompanyDisplayName(String)} (introduced in PR #685):
     * repeated companies across the organizer / speaker / attendee passes hit
     * the cache after the first lookup, so an export with N participants and K
     * distinct companies makes at most K upstream calls. Returns the slug as a
     * graceful fallback when CUMS doesn't know the company.
     */
    private String resolveCompany(String companySlug) {
        if (companySlug == null || companySlug.isBlank()) {
            return "";
        }
        try {
            String displayName = userApiClient.getCompanyDisplayName(companySlug);
            return displayName != null ? displayName : companySlug;
        } catch (Exception e) {
            log.warn("Could not resolve display name for company {} — falling back to slug: {}",
                    companySlug, e.getMessage());
            return companySlug;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
