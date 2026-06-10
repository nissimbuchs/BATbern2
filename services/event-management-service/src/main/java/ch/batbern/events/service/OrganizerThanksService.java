package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OrganizerThanks;
import ch.batbern.events.dto.ThanksCountResponse;
import ch.batbern.events.dto.ThanksNoteResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.ThanksNotAllowedException;
import ch.batbern.events.exception.ThanksRateLimitedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OrganizerThanksRepository;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Service for "Thank the Organizers" (Story 7.4).
 *
 * <p>Anyone — logged-in or anonymous — can thank the volunteer organizers once an event is
 * live/completed. Logged-in thank-yous are deduped to one per (event, user) and the note may be
 * updated (AC4); anonymous thank-yous are clap-style rows, rate-limited per (event, IP) and not
 * user-deduped (AC5). The aggregate count is public; notes are organizer-visible only (AC6).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizerThanksService {

    /** Thank-yous open only once the event has actually happened (AC1). */
    private static final Set<EventWorkflowState> THANKABLE_STATES =
            Set.of(EventWorkflowState.EVENT_LIVE, EventWorkflowState.EVENT_COMPLETED);

    private final OrganizerThanksRepository thanksRepository;
    private final EventRepository eventRepository;
    private final ThanksRateLimiter rateLimiter;

    /**
     * Submit a thank-you. Returns the new aggregate count for the event.
     *
     * @param eventCode the event being thanked
     * @param username  the logged-in attendee's username, or {@code null} for an anonymous clap
     * @param note      optional short note (organizer-visible only)
     * @param clientIp  the caller's IP — used for the anonymous per-(event,IP) rate limit
     */
    @Transactional
    public long submitThanks(String eventCode, String username, String note, String clientIp) {
        Event event = loadThankableEvent(eventCode);

        if (username != null && !username.isBlank()) {
            // Logged-in: race-safe atomic upsert by (event, username) — one row per attendee, note
            // updatable (AC4). A check-then-insert would let two concurrent submissions from the
            // same user collide on the partial unique index and 500; ON CONFLICT dedupes cleanly.
            thanksRepository.upsertLoggedInThanks(event.getId(), username, note);
            log.info("Logged-in thank-you for event {} by {}", eventCode, username);
        } else {
            // Anonymous: rate-limit BEFORE inserting so a rejected attempt does not bump the
            // counter (AC3 — "without incrementing"). Then insert a clap row (AC5).
            if (!rateLimiter.isAllowed(event.getId(), clientIp)) {
                throw new ThanksRateLimitedException(
                        "Too many thank-yous for this event from your network — please try later.");
            }
            thanksRepository.save(OrganizerThanks.builder()
                    .eventId(event.getId())
                    .thankedByUsername(null)
                    .note(note)
                    .build());
            log.info("Anonymous thank-you for event {}", eventCode);
        }

        return thanksRepository.countByEventId(event.getId());
    }

    /**
     * Read the aggregate thank-you count for an event. When {@code includeNotes} is true (organizer
     * caller only), the submitted notes are also returned (AC6) — newest first.
     */
    @Transactional(readOnly = true)
    public ThanksCountResponse getThanks(String eventCode, boolean includeNotes) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        long count = thanksRepository.countByEventId(event.getId());
        if (!includeNotes) {
            return ThanksCountResponse.ofCount(count);
        }

        List<ThanksNoteResponse> notes = thanksRepository
                .findByEventIdOrderByCreatedAtDesc(event.getId())
                .stream()
                .map(t -> new ThanksNoteResponse(t.getNote(), t.getThankedByUsername(), t.getCreatedAt()))
                .toList();
        return new ThanksCountResponse(count, notes);
    }

    private Event loadThankableEvent(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        if (!THANKABLE_STATES.contains(event.getWorkflowState())) {
            throw new ThanksNotAllowedException(
                    "Thank-yous open only after the event is live or completed (event " + eventCode
                            + " is " + event.getWorkflowState() + ").");
        }
        return event;
    }
}
