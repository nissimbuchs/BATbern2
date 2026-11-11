package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.mapper.SessionMapper;
import ch.batbern.events.sessions.dto.generated.CreateSessionRequest;
import ch.batbern.events.sessions.dto.generated.MaterialsStatus;
import ch.batbern.events.sessions.dto.generated.SessionMaterialResponse;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import ch.batbern.events.sessions.dto.generated.SessionSpeaker;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for Session business logic
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 * Story 5.9: Session Materials Upload
 * Story BAT-90 Phase 2: Uses SessionMapper for basic field mapping
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionMapper sessionMapper;
    private final SessionUserService sessionUserService;
    private final SessionMaterialsService sessionMaterialsService;

    /**
     * Convert Session entity to SessionResponse DTO
     * Story 1.15a.1b: Enriches with speaker data
     * Story 5.9: Enriches with materials data
     * Story BAT-90 Phase 2: Uses SessionMapper for basic field mapping
     *
     * @param session Session entity
     * @param eventCode Event code (for response)
     * @param includeSpeakers Whether to include speakers array
     * @return SessionResponse DTO
     */
    @Transactional(readOnly = true)
    public SessionResponse toSessionResponse(Session session, String eventCode, boolean includeSpeakers) {
        // Use SessionMapper for basic field mapping
        SessionResponse baseResponse = sessionMapper.toDto(session);

        // Build enriched response with speakers and materials
        SessionResponse.Builder builder = SessionResponse.builder()
                .sessionSlug(baseResponse.getSessionSlug())
                .eventCode(eventCode) // Override with provided eventCode
                .title(baseResponse.getTitle())
                .description(baseResponse.getDescription())
                .sessionType(baseResponse.getSessionType())
                .startTime(baseResponse.getStartTime())
                .endTime(baseResponse.getEndTime())
                .room(baseResponse.getRoom())
                .capacity(baseResponse.getCapacity())
                .language(baseResponse.getLanguage())
                .createdAt(baseResponse.getCreatedAt())
                .updatedAt(baseResponse.getUpdatedAt());

        // Include speakers if requested (Story 1.15a.1b)
        if (includeSpeakers) {
            List<SessionSpeaker> speakers = sessionUserService.getSessionSpeakers(session.getId());
            builder.speakers(speakers);
        }

        // Story 5.9: Always include materials data
        List<SessionMaterialResponse> materials = sessionMaterialsService
                .getMaterialsBySession(session.getSessionSlug());
        builder.materials(materials);
        builder.materialsCount(materials.size());

        // Calculate materials status: NONE or COMPLETE
        MaterialsStatus materialsStatus = calculateMaterialsStatus(materials);
        builder.materialsStatus(materialsStatus);

        return builder.build();
    }

    /**
     * Calculate materials status based on uploaded materials
     * Story 5.9: Materials status indicator
     *
     * @param materials List of session materials
     * @return NONE without materials, COMPLETE as soon as the session has one (any type, #1123)
     */
    private MaterialsStatus calculateMaterialsStatus(List<SessionMaterialResponse> materials) {
        return materials.isEmpty() ? MaterialsStatus.NONE : MaterialsStatus.COMPLETE;
    }

    /**
     * Convert Session entity to SessionResponse DTO (with speakers by default)
     */
    @Transactional(readOnly = true)
    public SessionResponse toSessionResponse(Session session, String eventCode) {
        return toSessionResponse(session, eventCode, true);
    }

    /**
     * The mode of a create (Story 4.10b): {@code true} for an import, {@code false} for a single
     * session whose required fields are all there; anything else is a 400.
     */
    public boolean isImport(CreateSessionRequest request) {
        boolean anySingleField = request.getTitle() != null || request.getDescription() != null
                || request.getSessionType() != null || request.getStartTime() != null
                || request.getEndTime() != null || request.getRoom() != null
                || request.getCapacity() != null || request.getLanguage() != null;
        boolean importMode = request.getImports() != null;
        if (importMode == anySingleField) {
            throw new ValidationException(importMode
                    ? "Give either the session fields or imports, not both"
                    : "Give the session fields (title, sessionType, startTime, endTime) or imports");
        }
        if (importMode) {
            int items = request.getImports().size();
            if (items < 1 || items > 100) {
                throw new ValidationException("imports must hold 1 to 100 sessions, got " + items);
            }
            return true;
        }
        List<String> missing = new ArrayList<>();
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            missing.add("title");
        }
        if (request.getSessionType() == null) {
            missing.add("sessionType");
        }
        if (request.getStartTime() == null) {
            missing.add("startTime");
        }
        if (request.getEndTime() == null) {
            missing.add("endTime");
        }
        if (!missing.isEmpty()) {
            throw new ValidationException("Missing required session fields: " + String.join(", ", missing));
        }
        return false;
    }
}
