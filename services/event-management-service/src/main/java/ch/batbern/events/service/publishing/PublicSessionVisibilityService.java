package ch.batbern.events.service.publishing;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.CallerRoles;
import ch.batbern.events.service.SessionVisibility;
import lombok.RequiredArgsConstructor;
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
 *       ({@code /events/{code}}, {@code /events}), which give sessions to organizers only
 *       ({@link CallerRoles#isOrganizer}).</li>
 * </ul>
 *
 * <p>Implements the sessions module's {@link SessionVisibility}, through which the event read's
 * session expansion asks for the public view without depending on publishing.
 */
@Service
@RequiredArgsConstructor
public class PublicSessionVisibilityService implements SessionVisibility {

    private final PublicSessionVisibilityPolicy policy;
    private final SpeakerPoolRepository speakerPoolRepository;

    /** True when the current caller holds the ORGANIZER role. */
    public boolean callerIsOrganizer() {
        return CallerRoles.isOrganizer();
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
    @Override
    @Transactional(readOnly = true)
    public List<Session> publicSessions(Event event, List<Session> sessions) {
        Instant now = Instant.now();
        if (!policy.isFiltered(event, now)) {
            return sessions;
        }
        return policy.filterForPublic(event, sessions, speakerPoolRepository.findByEventId(event.getId()), now);
    }

    /** Batch variant of {@link #publicSessions} for list endpoints: one pool query for all events. */
    @Override
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
}
