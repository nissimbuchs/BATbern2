package ch.batbern.events.mapper;

import ch.batbern.events.core.dto.generated.Event;
import ch.batbern.events.core.dto.generated.EventDetail;
import ch.batbern.events.core.dto.generated.EventTopic;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.core.dto.generated.EventWorkflowState;
import ch.batbern.events.core.dto.generated.Venue;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.sessions.dto.generated.Session;
import ch.batbern.events.sessions.dto.generated.SessionMaterialResponse;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import ch.batbern.events.sessions.dto.generated.SessionSpeaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure boundary mapper from the internal hand {@link EventResponse} working model to the
 * generated wire DTOs {@link Event} (list items / mutations) and {@link EventDetail}
 * (single-event GET). API-consolidation Phase 7 (2026-06-29, EventController wiring).
 *
 * <p><b>Why a boundary mapper (not a delete-the-hand-DTO consolidation):</b> {@code EventController}
 * is the 2.5k-line central, public-facing controller, and its {@code EventResponse} enrichment
 * (topic/venue/sessions/metrics, the batch cross-service portrait join, the {@code ?include=}
 * sparse-fieldset logic) is proven, working code. Rewriting all of it to emit typed objects would
 * be high-risk. Instead the enrichment is left byte-identical and this mapper converts at the
 * controller return boundary — the same pattern chosen for {@code TimetableResponse}.
 *
 * <p><b>Sessions, lean vs rich (owner-chosen design, 2026-06-29):</b>
 * <ul>
 *   <li>{@link #toEvent} (list) emits the lean {@link Session} (speakers + {@code materialsStatus},
 *       NO materials array) — keeps the list payload small.</li>
 *   <li>{@link #toEventDetail} (single GET) emits {@link SessionResponse} (materials + enriched
 *       speakers) — the two views that render material files (public archive program, organizer
 *       SessionEditModal) both load the single-event detail.</li>
 * </ul>
 *
 * <p>The two paths populate {@code EventResponse.sessions} (a {@code List<Map>}) differently: the
 * list path (buildSessionMapBatch) stores nested {@code Map} speakers and {@code Instant} times;
 * the detail path (expandSessions) stores already-typed {@code SessionSpeaker}/
 * {@code SessionMaterialResponse} and {@code OffsetDateTime} times. This mapper tolerates both
 * (see {@link #toOffset(Object)} and the speaker/material coercions).
 */
@Component
@Slf4j
public class EventGeneratedMapper {

    /** Map the internal response to the generated {@link Event} (lean sessions, no materials). */
    @SuppressWarnings("unchecked")
    public Event toEvent(EventResponse r) {
        if (r == null) {
            return null;
        }
        Event e = new Event()
                .eventCode(r.getEventCode())
                .title(r.getTitle())
                .eventNumber(r.getEventNumber())
                .date(toOffset(r.getDate()))
                .registrationDeadline(toOffset(r.getRegistrationDeadline()))
                .venueName(r.getVenueName())
                .venueAddress(r.getVenueAddress())
                .venueCapacity(r.getVenueCapacity())
                .organizerUsername(r.getOrganizerUsername())
                .currentAttendeeCount(r.getCurrentAttendeeCount())
                .publishedAt(toOffset(r.getPublishedAt()))
                .metadata(r.getMetadata())
                .description(r.getDescription())
                .createdAt(toOffset(r.getCreatedAt()))
                .updatedAt(toOffset(r.getUpdatedAt()))
                .createdBy(r.getCreatedBy())
                .updatedBy(r.getUpdatedBy())
                .themeImageUrl(toUri(r.getThemeImageUrl()))
                .themeImageUploadId(r.getThemeImageUploadId())
                .eventType(eventType(r.getEventType()))
                .typicalStartTime(r.getTypicalStartTime())
                .typicalEndTime(r.getTypicalEndTime())
                .topicCode(r.getTopicCode())
                .topicSelectionNote(r.getTopicSelectionNote())
                .workflowState(workflowState(r.getWorkflowState()))
                .qnaEnabled(r.getQnaEnabled())
                .qnaOpenTrigger(qnaOpenTrigger(r.getQnaOpenTrigger()))
                .qnaWindowDays(r.getQnaWindowDays())
                .teaserImages(r.getTeaserImages())
                .topic(topic(r.getTopic()))
                .venue(venue(r.getVenue()))
                .registrationCapacity(r.getRegistrationCapacity())
                .confirmedCount(r.getConfirmedCount())
                .waitlistCount(r.getWaitlistCount())
                .spotsRemaining(r.getSpotsRemaining())
                .realAttendeeCount(r.getRealAttendeeCount())
                .confirmedSpeakersCount(r.getConfirmedSpeakersCount())
                .speakersWithCompleteInfoCount(r.getSpeakersWithCompleteInfoCount())
                .pendingMaterialsCount(r.getPendingMaterialsCount())
                .maxSpeakerSlots(r.getMaxSpeakerSlots())
                .sessionsWithMaterialsCount(r.getSessionsWithMaterialsCount())
                .totalSessionsCount(r.getTotalSessionsCount());
        if (r.getSessions() != null) {
            e.setSessions(r.getSessions().stream()
                    .map(m -> leanSession((Map<String, Object>) m))
                    .collect(Collectors.toList()));
        }
        return e;
    }

    /** Map the internal response to the generated {@link EventDetail} (rich sessions w/ materials). */
    @SuppressWarnings("unchecked")
    public EventDetail toEventDetail(EventResponse r) {
        if (r == null) {
            return null;
        }
        EventDetail e = new EventDetail()
                .eventCode(r.getEventCode())
                .title(r.getTitle())
                .eventNumber(r.getEventNumber())
                .date(toOffset(r.getDate()))
                .registrationDeadline(toOffset(r.getRegistrationDeadline()))
                .venueName(r.getVenueName())
                .venueAddress(r.getVenueAddress())
                .venueCapacity(r.getVenueCapacity())
                .organizerUsername(r.getOrganizerUsername())
                .currentAttendeeCount(r.getCurrentAttendeeCount())
                .publishedAt(toOffset(r.getPublishedAt()))
                .metadata(r.getMetadata())
                .description(r.getDescription())
                .createdAt(toOffset(r.getCreatedAt()))
                .updatedAt(toOffset(r.getUpdatedAt()))
                .createdBy(r.getCreatedBy())
                .updatedBy(r.getUpdatedBy())
                .themeImageUrl(toUri(r.getThemeImageUrl()))
                .themeImageUploadId(r.getThemeImageUploadId())
                .eventType(eventType(r.getEventType()))
                .typicalStartTime(r.getTypicalStartTime())
                .typicalEndTime(r.getTypicalEndTime())
                .topicCode(r.getTopicCode())
                .topicSelectionNote(r.getTopicSelectionNote())
                .workflowState(workflowState(r.getWorkflowState()))
                .qnaEnabled(r.getQnaEnabled())
                .qnaOpenTrigger(detailQnaOpenTrigger(r.getQnaOpenTrigger()))
                .qnaWindowDays(r.getQnaWindowDays())
                .teaserImages(r.getTeaserImages())
                .topic(topic(r.getTopic()))
                .venue(venue(r.getVenue()))
                .currentPublishedPhase(currentPublishedPhase(r.getCurrentPublishedPhase()))
                .registrationCapacity(r.getRegistrationCapacity())
                .confirmedCount(r.getConfirmedCount())
                .waitlistCount(r.getWaitlistCount())
                .spotsRemaining(r.getSpotsRemaining())
                .realAttendeeCount(r.getRealAttendeeCount())
                .confirmedSpeakersCount(r.getConfirmedSpeakersCount())
                .speakersWithCompleteInfoCount(r.getSpeakersWithCompleteInfoCount())
                .pendingMaterialsCount(r.getPendingMaterialsCount())
                .maxSpeakerSlots(r.getMaxSpeakerSlots())
                .sessionsWithMaterialsCount(r.getSessionsWithMaterialsCount())
                .totalSessionsCount(r.getTotalSessionsCount());
        if (r.getSessions() != null) {
            e.setSessions(r.getSessions().stream()
                    .map(m -> detailSession((Map<String, Object>) m))
                    .collect(Collectors.toList()));
        }
        return e;
    }

    // ---- session mapping -------------------------------------------------

    @SuppressWarnings("unchecked")
    private Session leanSession(Map<String, Object> m) {
        Session s = new Session()
                .sessionSlug((String) m.get("sessionSlug"))
                .eventCode((String) m.get("eventCode"))
                .title((String) m.get("title"))
                .description((String) m.get("description"))
                .sessionType((String) m.get("sessionType"))
                .startTime(toOffset(m.get("startTime")))
                .endTime(toOffset(m.get("endTime")))
                .room((String) m.get("room"))
                .capacity((Integer) m.get("capacity"))
                .createdAt(toOffset(m.get("createdAt")))
                .updatedAt(toOffset(m.get("updatedAt")))
                .materialsStatus((String) m.get("materialsStatus"));
        String language = (String) m.get("language");
        if (language != null) {
            s.setLanguage(language);
        }
        s.setSpeakers(speakers(m.get("speakers")));
        return s;
    }

    @SuppressWarnings("unchecked")
    private SessionResponse detailSession(Map<String, Object> m) {
        SessionResponse s = new SessionResponse()
                .sessionSlug((String) m.get("sessionSlug"))
                .eventCode((String) m.get("eventCode"))
                .title((String) m.get("title"))
                .description((String) m.get("description"))
                .sessionType((String) m.get("sessionType"))
                .startTime(toOffset(m.get("startTime")))
                .endTime(toOffset(m.get("endTime")))
                .room((String) m.get("room"))
                .capacity((Integer) m.get("capacity"))
                .createdAt(toOffset(m.get("createdAt")))
                .updatedAt(toOffset(m.get("updatedAt")))
                .materialsStatus((String) m.get("materialsStatus"))
                .materialsCount((Integer) m.get("materialsCount"));
        String language = (String) m.get("language");
        if (language != null) {
            s.setLanguage(language);
        }
        s.setSpeakers(speakers(m.get("speakers")));
        s.setMaterials(materials(m.get("materials")));
        return s;
    }

    /**
     * Coerce the {@code speakers} value to {@code List<SessionSpeaker>}. The detail path already
     * stores typed {@link SessionSpeaker}; the list path stores nested {@code Map}s.
     */
    @SuppressWarnings("unchecked")
    private List<SessionSpeaker> speakers(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return value == null ? null : List.of();
        }
        if (list.get(0) instanceof SessionSpeaker) {
            return (List<SessionSpeaker>) value;
        }
        return list.stream()
                .map(o -> speakerFromMap((Map<String, Object>) o))
                .collect(Collectors.toList());
    }

    private SessionSpeaker speakerFromMap(Map<String, Object> m) {
        return new SessionSpeaker()
                .username((String) m.get("username"))
                .firstName((String) m.get("firstName"))
                .lastName((String) m.get("lastName"))
                .company((String) m.get("company"))
                .companyDisplayName((String) m.get("companyDisplayName"))
                .profilePictureUrl(toUri((String) m.get("profilePictureUrl")))
                .companyLogoUrl(toUri((String) m.get("companyLogoUrl")))
                .bio((String) m.get("bio"))
                .speakerRole(speakerRole((String) m.get("speakerRole")))
                .presentationTitle((String) m.get("presentationTitle"))
                .isConfirmed((Boolean) m.get("isConfirmed"));
    }

    /** Detail-path materials are already typed {@link SessionMaterialResponse}. */
    @SuppressWarnings("unchecked")
    private List<SessionMaterialResponse> materials(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        if (list.isEmpty() || list.get(0) instanceof SessionMaterialResponse) {
            return (List<SessionMaterialResponse>) value;
        }
        // Unexpected (the list path drops materials); skip rather than risk a bad cast.
        return List.of();
    }

    // ---- topic / venue ---------------------------------------------------

    private EventTopic topic(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        return new EventTopic()
                .code((String) m.get("code"))
                .name((String) m.get("name"))
                .description((String) m.get("description"))
                .category(category((String) m.get("category")));
    }

    private Venue venue(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        return new Venue()
                .id((String) m.get("id"))
                .name((String) m.get("name"))
                .address((String) m.get("address"))
                .capacity((Integer) m.get("capacity"));
    }

    // ---- scalar coercions ------------------------------------------------

    /** Accept either {@link Instant} (list path) or {@link OffsetDateTime} (detail path). */
    private OffsetDateTime toOffset(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt;
        }
        if (o instanceof Instant ins) {
            return ins.atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private URI toUri(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return URI.create(s);
        } catch (IllegalArgumentException ex) {
            log.warn("Dropping malformed URI '{}': {}", s, ex.getMessage());
            return null;
        }
    }

    private EventType eventType(String v) {
        if (v == null) {
            return null;
        }
        try {
            return EventType.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown eventType '{}' — emitting null", v);
            return null;
        }
    }

    private EventWorkflowState workflowState(String v) {
        if (v == null) {
            return null;
        }
        try {
            return EventWorkflowState.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown workflowState '{}' — emitting null", v);
            return null;
        }
    }

    private Event.QnaOpenTriggerEnum qnaOpenTrigger(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Event.QnaOpenTriggerEnum.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown qnaOpenTrigger '{}' — emitting null", v);
            return null;
        }
    }

    private EventDetail.QnaOpenTriggerEnum detailQnaOpenTrigger(String v) {
        if (v == null) {
            return null;
        }
        try {
            return EventDetail.QnaOpenTriggerEnum.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown qnaOpenTrigger '{}' — emitting null", v);
            return null;
        }
    }

    private EventDetail.CurrentPublishedPhaseEnum currentPublishedPhase(String v) {
        if (v == null) {
            return null;
        }
        try {
            return EventDetail.CurrentPublishedPhaseEnum.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown currentPublishedPhase '{}' — emitting null", v);
            return null;
        }
    }

    private EventTopic.CategoryEnum category(String v) {
        if (v == null) {
            return null;
        }
        try {
            return EventTopic.CategoryEnum.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown topic category '{}' — emitting null", v);
            return null;
        }
    }

    private SessionSpeaker.SpeakerRoleEnum speakerRole(String v) {
        if (v == null) {
            return null;
        }
        try {
            return SessionSpeaker.SpeakerRoleEnum.fromValue(v);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown speakerRole '{}' — emitting null", v);
            return null;
        }
    }
}
