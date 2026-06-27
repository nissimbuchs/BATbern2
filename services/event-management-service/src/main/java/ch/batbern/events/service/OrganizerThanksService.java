package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OrganizerThanks;
import ch.batbern.events.core.dto.generated.FeaturedThanksResponse;
import ch.batbern.events.core.dto.generated.ThanksCountResponse;
import ch.batbern.events.core.dto.generated.ThanksNoteResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.ThanksNotAllowedException;
import ch.batbern.events.exception.ThanksNotFeaturableException;
import ch.batbern.events.exception.ThanksNotFoundException;
import ch.batbern.events.exception.ThanksRateLimitedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.FeaturedThanksProjection;
import ch.batbern.events.repository.OrganizerThanksRepository;
import ch.batbern.events.repository.ThanksAuthorProjection;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /** Hard cap on the public featured marquee fetch (Story 7.7, AC2). */
    private static final int MAX_FEATURED = 9;

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
            return new ThanksCountResponse(count);
        }

        List<OrganizerThanks> rows = thanksRepository.findByEventIdOrderByCreatedAtDesc(event.getId());
        // Enrich logged-in notes with the author's display name (Story 7.7) in one batched,
        // anonymous-safe local DB query — same cross-service join the public marquee + Q&A use.
        Set<String> usernames = rows.stream()
                .map(OrganizerThanks::getThankedByUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, ThanksAuthorProjection> portraits = loadAuthorPortraits(usernames);

        List<ThanksNoteResponse> notes = rows.stream()
                .map(t -> {
                    ThanksAuthorProjection a = t.getThankedByUsername() == null
                            ? null : portraits.get(t.getThankedByUsername());
                    boolean showCompany = a != null && !Boolean.FALSE.equals(a.getShowCompany());
                    return noteResponse(t, a, showCompany);
                })
                .toList();
        return new ThanksCountResponse(count).notes(notes);
    }

    /** Build the generated ThanksNoteResponse (Instant → OffsetDateTime at the wire boundary). */
    private static ThanksNoteResponse noteResponse(
            OrganizerThanks t, ThanksAuthorProjection a, boolean showCompany) {
        Instant createdAt = t.getCreatedAt();
        return new ThanksNoteResponse()
                .id(t.getId())
                .note(t.getNote())
                .thankedByUsername(t.getThankedByUsername())
                .thankedByFirstName(a != null ? a.getFirstName() : null)
                .thankedByLastName(a != null ? a.getLastName() : null)
                .thankedByCompanyName(showCompany ? a.getCompanyDisplayName() : null)
                .featured(t.getFeaturedAt() != null)
                .createdAt(createdAt == null ? null : createdAt.atOffset(ZoneOffset.UTC));
    }

    /**
     * PUBLIC featured marquee (Story 7.7, AC2/AC3): up to {@code limit} (capped at {@link
     * #MAX_FEATURED}) RANDOM featured, logged-in thank-yous across ALL events, enriched with the
     * author's first name + company logo. Anonymous notes are structurally excluded (no username);
     * notes by a since-deleted author drop out via the INNER JOIN. Company name/logo are suppressed
     * when the author opted out of showing their company.
     */
    @Transactional(readOnly = true)
    public List<FeaturedThanksResponse> getFeaturedThanks(int limit) {
        int capped = Math.max(1, Math.min(limit, MAX_FEATURED));
        return thanksRepository.findFeaturedRandom(capped).stream()
                .map(this::toFeaturedResponse)
                .toList();
    }

    private FeaturedThanksResponse toFeaturedResponse(FeaturedThanksProjection p) {
        boolean showCompany = !Boolean.FALSE.equals(p.getShowCompany());
        return new FeaturedThanksResponse()
                .note(p.getNote())
                .eventCode(p.getEventCode())
                .thankedByFirstName(p.getFirstName())
                .thankedByLastName(p.getLastName())
                .thankedByCompanyName(showCompany ? p.getCompanyDisplayName() : null)
                .thankedByCompanyLogoUrl(showCompany ? p.getCompanyLogoUrl() : null);
    }

    /**
     * Organizer feature-toggle (Story 7.7, AC1). Marks/un-marks a note for the public marquee.
     * Rejects an anonymous note (no name → not featurable). Returns the updated organizer note view.
     */
    @Transactional
    public ThanksNoteResponse setFeatured(String eventCode, UUID id, boolean featured) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        OrganizerThanks thanks = thanksRepository.findByIdAndEventId(id, event.getId())
                .orElseThrow(() -> new ThanksNotFoundException(
                        "Thank-you " + id + " not found for event " + eventCode));
        if (featured && (thanks.getThankedByUsername() == null || thanks.getThankedByUsername().isBlank())) {
            throw new ThanksNotFeaturableException(
                    "Anonymous thank-yous cannot be featured on the public marquee.");
        }
        thanks.setFeaturedAt(featured ? Instant.now() : null);
        thanksRepository.save(thanks);
        log.info("Thank-you {} for event {} featured={}", id, eventCode, featured);

        ThanksAuthorProjection a = thanks.getThankedByUsername() == null ? null
                : loadAuthorPortraits(Set.of(thanks.getThankedByUsername()))
                        .get(thanks.getThankedByUsername());
        boolean showCompany = a != null && !Boolean.FALSE.equals(a.getShowCompany());
        return noteResponse(thanks, a, showCompany);
    }

    /** Batch-load author portraits keyed by username (empty map for no usernames). */
    private Map<String, ThanksAuthorProjection> loadAuthorPortraits(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return thanksRepository.findThanksAuthorPortraitsByUsernames(usernames).stream()
                .collect(Collectors.toMap(ThanksAuthorProjection::getUsername, p -> p, (a, b) -> a));
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
