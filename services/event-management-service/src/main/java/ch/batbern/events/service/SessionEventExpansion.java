package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.UserPortraitProjection;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The sessions of an event read ({@code include=sessions}, {@code include=speakers}; sessions carry
 * their speakers), sessions module. Organizers get every session, the public view the sessions its
 * publishing state allows ({@link SessionVisibility}), non-organizers on the organizer endpoints none.
 * Moved from {@code EventController} unchanged.
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class SessionEventExpansion implements EventResponseContributor {

    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final SessionService sessionService;
    private final SessionVisibility sessionVisibility;
    private final SessionMaterialsService sessionMaterialsService;

    @Override
    public Set<String> includes() {
        return Set.of("sessions", "speakers");
    }

    @Override
    public void expand(String include, Event event, EventResponse response, View view) {
        // Sessions include speakers automatically
        if (view != View.NONE) {
            response.setSessions(expandSessions(event, view));
        }
    }

    /**
     * Batch: sessions + session_users, portraits, materials in three queries for the whole page.
     *
     * <p>Speaker names are read from the cached session_users.speaker_first_name/last_name columns
     * (populated during assignment, V38 migration). profilePictureUrl comes from the batch portrait
     * join; the frontend lazy-loads full speaker data via GET /api/v1/speakers/{username}.
     */
    @Override
    public void expandBatch(Set<String> includes, List<Event> events, List<EventResponse> responses, View view) {
        if (view == View.NONE || !(includes.contains("sessions") || includes.contains("speakers"))) {
            return;
        }
        Set<UUID> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        // Query 3: all sessions for all events with session_users eagerly loaded
        List<Session> allSessions = sessionRepository.findByEventIdInWithSpeakers(eventIds);

        // Group sessions by event ID; the public view keeps only what the publishing state allows
        Map<UUID, List<Session>> grouped = allSessions.stream()
                .collect(Collectors.groupingBy(Session::getEventId));
        Map<UUID, List<Session>> sessionsByEventId = view == View.PUBLIC
                ? sessionVisibility.publicSessions(events, grouped)
                : grouped;

        // Query 4 (intentional architecture break): cross-service join into user_profiles
        // (owned by company-user-management-service) to get portrait URLs and company names.
        // Both services share the same PostgreSQL DB in this monorepo deployment.
        Set<String> allUsernames = allSessions.stream()
                .flatMap(s -> s.getSessionUsers().stream())
                .map(SessionUser::getUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, UserPortraitProjection> portraitByUsername = Collections.emptyMap();
        if (!allUsernames.isEmpty()) {
            portraitByUsername = sessionUserRepository.findUserPortraitsByUsernames(allUsernames)
                    .stream()
                    .collect(Collectors.toMap(
                            UserPortraitProjection::getUsername,
                            p -> p,
                            (a, b) -> a // keep first on duplicate username
                    ));
        }

        // Query 5: all materials for all sessions
        Set<UUID> sessionIds = allSessions.stream()
                .map(Session::getId)
                .collect(Collectors.toSet());

        Map<UUID, List<SessionMaterial>> materialsBySessionId = Collections.emptyMap();
        if (!sessionIds.isEmpty()) {
            materialsBySessionId = sessionMaterialsRepository.findBySessionIdIn(sessionIds)
                    .stream()
                    .collect(Collectors.groupingBy(SessionMaterial::getSessionId));
        }

        // Assemble session maps per event entirely in-memory — 0 HTTP calls
        final Map<String, UserPortraitProjection> finalPortraits = portraitByUsername;
        final Map<UUID, List<SessionMaterial>> finalMaterials = materialsBySessionId;

        for (int i = 0; i < events.size(); i++) {
            UUID eventId = events.get(i).getId();
            String eventCode = events.get(i).getEventCode();
            List<Session> eventSessions = sessionsByEventId.getOrDefault(eventId, List.of());

            // The public view names public materials only; otherwise what the caller may see (#1260)
            List<Map<String, Object>> sessionMaps = eventSessions.stream()
                    .map(s -> buildSessionMapBatch(
                            s, eventCode,
                            SessionMaterialsService.visible(finalMaterials.getOrDefault(s.getId(), List.of()),
                                    sessionMaterialsService.mayShowNonPublic(s, view == View.PUBLIC)),
                            finalPortraits))
                    .collect(Collectors.toList());

            responses.get(i).setSessions(sessionMaps);
        }
    }

    /**
     * Build a session response map from already-loaded entities (no DB or HTTP calls). The materials
     * are the ones this response may show; count and status follow them.
     *
     * Speaker names, portrait URLs, and company logo URLs all come from the batch
     * cross-service join result (user_profiles + companies tables).
     */
    private Map<String, Object> buildSessionMapBatch(
            Session session,
            String eventCode,
            List<SessionMaterial> materials,
            Map<String, UserPortraitProjection> portraitByUsername) {

        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("id", session.getId());
        sessionMap.put("sessionSlug", session.getSessionSlug());
        sessionMap.put("eventCode", eventCode);
        sessionMap.put("title", session.getTitle());
        sessionMap.put("description", session.getDescription());
        sessionMap.put("sessionType", session.getSessionType());
        sessionMap.put("startTime", session.getStartTime());
        sessionMap.put("endTime", session.getEndTime());
        sessionMap.put("room", session.getRoom());
        sessionMap.put("capacity", session.getCapacity());
        sessionMap.put("language", session.getLanguage());
        sessionMap.put("createdAt", session.getCreatedAt());
        sessionMap.put("updatedAt", session.getUpdatedAt());

        // Materials (batch-loaded, sorted by creation time)
        List<Map<String, Object>> materialMaps = materials.stream()
                .sorted(Comparator.comparing(
                        SessionMaterial::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> {
                    Map<String, Object> mm = new HashMap<>();
                    mm.put("id", m.getId());
                    mm.put("fileName", m.getFileName());
                    mm.put("materialType", m.getMaterialType());
                    mm.put("cloudFrontUrl", m.getCloudFrontUrl());
                    mm.put("fileSize", m.getFileSize());
                    mm.put("createdAt", m.getCreatedAt());
                    return mm;
                })
                .collect(Collectors.toList());

        sessionMap.put("materials", materialMaps);
        sessionMap.put("materialsCount", materialMaps.size());
        sessionMap.put("materialsStatus", materialMaps.isEmpty() ? "NONE" : "COMPLETE");

        // Speakers — portrait URL, company name, and company logo URL all from
        // the cross-service DB join. Zero HTTP calls.
        List<Map<String, Object>> speakerMaps = session.getSessionUsers().stream()
                .map(su -> {
                    UserPortraitProjection portrait =
                            su.getUsername() != null ? portraitByUsername.get(su.getUsername()) : null;
                    Map<String, Object> sm = new HashMap<>();
                    sm.put("username", su.getUsername());
                    sm.put("firstName",
                            su.getSpeakerFirstName() != null ? su.getSpeakerFirstName() : "");
                    sm.put("lastName",
                            su.getSpeakerLastName() != null ? su.getSpeakerLastName() : "");
                    sm.put("speakerRole",
                            su.getSpeakerRole() != null ? su.getSpeakerRole().name() : null);
                    // Story 11.E.8: session_users.presentation_title dropped (V102).
                    sm.put("presentationTitle", null);
                    sm.put("isConfirmed", su.isConfirmed());
                    sm.put("profilePictureUrl", portrait != null ? portrait.getProfilePictureUrl() : null);
                    sm.put("company",            portrait != null ? portrait.getCompanyId() : null);
                    sm.put("companyDisplayName", portrait != null ? portrait.getCompanyDisplayName() : null);
                    sm.put("companyLogoUrl",     portrait != null ? portrait.getCompanyLogoUrl() : null);
                    sm.put("bio", null); // Only needed on detail page
                    return sm;
                })
                .collect(Collectors.toList());

        sessionMap.put("speakers", speakerMaps);
        return sessionMap;
    }

    /**
     * Sessions of one event (Story 1.15a.1, 1.15a.1b): with speakers eagerly loaded (Story 5.5) and
     * materials (Story 5.9), through {@link SessionService#toSessionResponse}.
     */
    private List<Map<String, Object>> expandSessions(Event event, View view) {
        List<Session> all = sessionRepository.findByEventIdWithSpeakers(event.getId());
        List<Session> sessions = view == View.PUBLIC
                ? sessionVisibility.publicSessions(event, all)
                : all;

        // Convert to response format using SessionService (includes materials - Story 5.9)
        return sessions.stream()
                .map(session -> {
                    // The public view carries public materials only, whoever asks (#1260)
                    SessionResponse sessionResponse = sessionService.toSessionResponse(session,
                            event.getEventCode(), true, view == View.PUBLIC);

                    // Convert SessionResponse to Map for backward compatibility
                    Map<String, Object> sessionMap = new HashMap<>();
                    sessionMap.put("id", session.getId()); // Story 5.6: Include UUID for speaker pool matching
                    sessionMap.put("sessionSlug", sessionResponse.getSessionSlug());
                    sessionMap.put("eventCode", sessionResponse.getEventCode());
                    sessionMap.put("title", sessionResponse.getTitle());
                    sessionMap.put("description", sessionResponse.getDescription());
                    sessionMap.put("sessionType", sessionResponse.getSessionType());
                    sessionMap.put("startTime", sessionResponse.getStartTime());
                    sessionMap.put("endTime", sessionResponse.getEndTime());
                    sessionMap.put("room", sessionResponse.getRoom());
                    sessionMap.put("capacity", sessionResponse.getCapacity());
                    sessionMap.put("language", sessionResponse.getLanguage());
                    sessionMap.put("createdAt", sessionResponse.getCreatedAt());
                    sessionMap.put("updatedAt", sessionResponse.getUpdatedAt());

                    // Story 5.9: Include materials data
                    sessionMap.put("materials", sessionResponse.getMaterials());
                    sessionMap.put("materialsCount", sessionResponse.getMaterialsCount());
                    sessionMap.put("materialsStatus", sessionResponse.getMaterialsStatus());

                    // Story 1.15a.1b: Use speakers from SessionResponse
                    sessionMap.put("speakers", sessionResponse.getSpeakers());

                    return sessionMap;
                })
                .collect(Collectors.toList());
    }
}
