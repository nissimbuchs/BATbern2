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
 * Applies {@link PublicSessionVisibilityPolicy} to every read path that exposes sessions or
 * speakers of an event. Organizers see everything; every other caller (anonymous, attendee,
 * speaker, partner) sees only what the published phase allows.
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

    /** Sessions of one event the current caller may see. */
    @Transactional(readOnly = true)
    public List<Session> visibleSessions(Event event, List<Session> sessions) {
        if (callerIsOrganizer() || !policy.isFiltered(event, Instant.now())) {
            return sessions;
        }
        return policy.filterForPublic(event, sessions, speakerPoolRepository.findByEventId(event.getId()),
                Instant.now());
    }

    /** Batch variant for list endpoints: one pool query for all events. */
    @Transactional(readOnly = true)
    public Map<UUID, List<Session>> visibleSessions(Collection<Event> events,
                                                    Map<UUID, List<Session>> sessionsByEventId) {
        if (callerIsOrganizer()) {
            return sessionsByEventId;
        }
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

    /** Cache-key suffix so organizer and public responses never share a cache entry. */
    public String audienceCacheSuffix() {
        return callerIsOrganizer() ? "_organizer" : "_public";
    }
}
