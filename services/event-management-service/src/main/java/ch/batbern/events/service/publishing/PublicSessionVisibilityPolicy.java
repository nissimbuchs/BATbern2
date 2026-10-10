package ch.batbern.events.service.publishing;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which sessions (and therefore which speakers) a non-organizer may see for an event.
 *
 * <p>Rules per published phase, for upcoming events:
 * <ul>
 *   <li>unpublished / TOPIC: nothing.</li>
 *   <li>SPEAKERS: sessions whose speaker is ACCEPTED or later. The session list itself is not
 *       rendered publicly in this phase; the sessions carry the speakers for the speaker grid.</li>
 *   <li>AGENDA: sessions with an assigned slot whose speaker is QUALITY_REVIEWED, plus slotted
 *       structural sessions (moderation, break, lunch, aperitif; {@link Session#isStructuralType}).</li>
 * </ul>
 * Completed, archived and past events are returned unfiltered (archive).
 *
 * <p>Why this exists: since Story 11.E.8 (2026-05-20) a session is created when a speaker becomes
 * READY, not when content is submitted. The public event read had relied on that ordering as an
 * implicit filter and started exposing READY/INVITED speakers on www.batbern.ch.
 */
@Component
public class PublicSessionVisibilityPolicy {

    private static final Set<SpeakerWorkflowState> SPEAKERS_PHASE_VISIBLE = EnumSet.of(
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.QUALITY_REVIEWED);

    private static final Set<SpeakerWorkflowState> AGENDA_PHASE_VISIBLE =
            EnumSet.of(SpeakerWorkflowState.QUALITY_REVIEWED);

    private static final Set<EventWorkflowState> UNFILTERED_STATES =
            EnumSet.of(EventWorkflowState.EVENT_COMPLETED, EventWorkflowState.ARCHIVED);

    private static final ZoneId BERN = ZoneId.of("Europe/Zurich");

    /**
     * @param event    the event the sessions belong to
     * @param sessions all sessions of the event
     * @param pool     the event's speaker-pool rows (linked to sessions via {@code session_id})
     * @param now      current instant, to recognise past events
     * @return the sessions a public (non-organizer) reader may see, in input order
     */
    public List<Session> filterForPublic(Event event, List<Session> sessions,
                                         Collection<SpeakerPool> pool, Instant now) {
        if (!isFiltered(event, now)) {
            return sessions;
        }
        String phase = normalizedPhase(event);
        Map<UUID, SpeakerWorkflowState> stateBySession = bestStateBySession(pool);

        return switch (phase) {
            case "speakers" -> sessions.stream()
                    .filter(s -> !isStructural(s))
                    .filter(s -> SPEAKERS_PHASE_VISIBLE.contains(stateBySession.get(s.getId())))
                    .toList();
            case "agenda" -> sessions.stream()
                    .filter(s -> s.getStartTime() != null)
                    .filter(s -> isStructural(s)
                            || AGENDA_PHASE_VISIBLE.contains(stateBySession.get(s.getId())))
                    .toList();
            default -> List.of();
        };
    }

    /**
     * Whether the public website may show this event at all: it has a published phase, or it is
     * completed or archived (archive). Unpublished drafts are never public.
     */
    public boolean isPubliclyVisible(Event event) {
        if (UNFILTERED_STATES.contains(event.getWorkflowState())) {
            return true;
        }
        String phase = normalizedPhase(event);
        return phase.equals("topic") || phase.equals("speakers") || phase.equals("agenda");
    }

    /**
     * Whether the public may see sessions and talk titles, not just who speaks. From the AGENDA
     * phase on, and for past/completed/archived events. In the SPEAKERS phase only the lineup is
     * public (owner decision 2026-10-07: no session list, no talk titles).
     */
    public boolean sessionDetailsPublic(Event event, Instant now) {
        return !isFiltered(event, now) || normalizedPhase(event).equals("agenda");
    }

    /**
     * Whether {@link #filterForPublic} restricts this event at all. Callers use it to keep
     * the organizer-independent cache key stable for archive events.
     */
    public boolean isFiltered(Event event, Instant now) {
        if (UNFILTERED_STATES.contains(event.getWorkflowState())) {
            return false;
        }
        if (event.getDate() == null) {
            return true;
        }
        Instant startOfToday = LocalDate.ofInstant(now, BERN).atStartOfDay(BERN).toInstant();
        return !event.getDate().isBefore(startOfToday);
    }

    private static String normalizedPhase(Event event) {
        String phase = event.getCurrentPublishedPhase();
        return phase == null ? "" : phase.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isStructural(Session session) {
        return Session.isStructuralType(session.getSessionType());
    }

    /**
     * A session normally has exactly one pool row. Should several point at the same session, the
     * most advanced state wins (ordinal order of the 8-state model), DECLINED never wins.
     */
    private static Map<UUID, SpeakerWorkflowState> bestStateBySession(Collection<SpeakerPool> pool) {
        Map<UUID, SpeakerWorkflowState> result = new HashMap<>();
        for (SpeakerPool row : pool) {
            if (row.getSessionId() == null || row.getStatus() == null
                    || row.getStatus() == SpeakerWorkflowState.DECLINED) {
                continue;
            }
            result.merge(row.getSessionId(), row.getStatus(),
                    (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
        }
        return result;
    }
}
