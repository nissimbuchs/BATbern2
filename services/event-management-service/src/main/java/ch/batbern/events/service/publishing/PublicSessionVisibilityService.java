package ch.batbern.events.service.publishing;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SpeakerPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Applies {@link PublicSessionVisibilityPolicy} (the single rule) to session lists.
 *
 * <ul>
 *   <li>{@link #publicSessions}: by publishing state only, whoever asks. Used by the public read
 *       model ({@code /public/events/*}) and the public zone of the Watch app.</li>
 *   <li>{@link #visibleSessions}: organizers see everything, everyone else the public view. Used
 *       by the session endpoints ({@code /events/{code}/sessions}).</li>
 *   <li>{@link #callerIsOrganizer}: the permission rule of the organizer endpoints
 *       ({@code /events/{code}}, {@code /events}), which give sessions to organizers only.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PublicSessionVisibilityService {

    private static final String ORGANIZER_AUTHORITY = "ROLE_ORGANIZER";

    private final PublicSessionVisibilityPolicy policy;
    private final SpeakerPoolRepository speakerPoolRepository;

    /** True when the current caller holds the ORGANIZER role. */
    public boolean callerIsOrganizer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ORGANIZER_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Organizers see every session; everyone else the public view. */
    @Transactional(readOnly = true)
    public List<Session> visibleSessions(Event event, List<Session> sessions) {
        return callerIsOrganizer() ? sessions : publicSessions(event, sessions);
    }

    /**
     * Sessions the public website may show, by publishing state only, whoever is asking
     * (bug 2026-10-07: a logged-in organizer saw READY speakers on www.batbern.ch).
     */
    @Transactional(readOnly = true)
    public List<Session> publicSessions(Event event, List<Session> sessions) {
        Instant now = Instant.now();
        if (!policy.isFiltered(event, now)) {
            return sessions;
        }
        return policy.filterForPublic(event, sessions, speakerPoolRepository.findByEventId(event.getId()), now);
    }

    /** Batch variant of {@link #publicSessions} for list endpoints: one pool query for all events. */
    @Transactional(readOnly = true)
    public Map<UUID, List<Session>> publicSessions(Collection<Event> events,
                                                   Map<UUID, List<Session>> sessionsByEventId) {
        Instant now = Instant.now();
        Set<UUID> filteredEventIds = events.stream()
                .filter(e -> policy.isFiltered(e, now))
                .map(Event::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<SpeakerPool>> poolByEventId = filteredEventIds.isEmpty()
                ? Map.of()
                : speakerPoolRepository.findByEventIdIn(filteredEventIds).stream()
                        .collect(Collectors.groupingBy(SpeakerPool::getEventId));

        return events.stream().collect(Collectors.toMap(
                Event::getId,
                e -> {
                    List<Session> sessions = sessionsByEventId.getOrDefault(e.getId(), List.of());
                    return filteredEventIds.contains(e.getId())
                            ? policy.filterForPublic(e, sessions, poolByEventId.getOrDefault(e.getId(), List.of()), now)
                            : sessions;
                },
                (a, b) -> a));
    }

    /** Cache-key suffix so organizer and non-organizer responses never share a cache entry. */
    public String audienceCacheSuffix() {
        return callerIsOrganizer() ? "_organizer" : "_public";
    }
}
