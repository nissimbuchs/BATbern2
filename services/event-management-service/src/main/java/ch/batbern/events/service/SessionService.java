package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.SessionMaterialResponse;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        SessionResponse.SessionResponseBuilder builder = SessionResponse.builder()
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
            List<SessionSpeakerResponse> speakers = sessionUserService.getSessionSpeakers(session.getId());
            builder.speakers(speakers);
        }

        // Story 5.9: Always include materials data
        List<SessionMaterialResponse> materials = sessionMaterialsService
                .getMaterialsBySession(session.getSessionSlug());
        builder.materials(materials);
        builder.materialsCount(materials.size());

        // Calculate materials status: NONE, PARTIAL, COMPLETE
        String materialsStatus = calculateMaterialsStatus(materials);
        builder.materialsStatus(materialsStatus);

        return builder.build();
    }

    /**
     * Calculate materials status based on uploaded materials
     * Story 5.9: Materials status indicator
     *
     * @param materials List of session materials
     * @return Status: NONE, PARTIAL, or COMPLETE
     */
    private String calculateMaterialsStatus(List<SessionMaterialResponse> materials) {
        if (materials.isEmpty()) {
            return "NONE";
        }
        // For now, any materials = COMPLETE
        // Story 5.10 will add content extraction validation for PARTIAL vs COMPLETE
        return "COMPLETE";
    }

    /**
     * Convert Session entity to SessionResponse DTO (with speakers by default)
     */
    @Transactional(readOnly = true)
    public SessionResponse toSessionResponse(Session session, String eventCode) {
        return toSessionResponse(session, eventCode, true);
    }
}
