package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.SessionSpeakerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for Session business logic
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final SessionUserService sessionUserService;

    /**
     * Convert Session entity to SessionResponse DTO
     * Story 1.15a.1b: Enriches with speaker data
     *
     * @param session Session entity
     * @param eventCode Event code (for response)
     * @param includeSpeakers Whether to include speakers array
     * @return SessionResponse DTO
     */
    @Transactional(readOnly = true)
    public SessionResponse toSessionResponse(Session session, String eventCode, boolean includeSpeakers) {
        SessionResponse.SessionResponseBuilder builder = SessionResponse.builder()
                .sessionSlug(session.getSessionSlug())
                .eventCode(eventCode)
                .title(session.getTitle())
                .description(session.getDescription())
                .sessionType(session.getSessionType())
                .startTime(session.getStartTime() != null ? ISO_FORMATTER.format(session.getStartTime()) : null)
                .endTime(session.getEndTime() != null ? ISO_FORMATTER.format(session.getEndTime()) : null)
                .room(session.getRoom())
                .capacity(session.getCapacity())
                .language(session.getLanguage())
                .createdAt(session.getCreatedAt() != null ? ISO_FORMATTER.format(session.getCreatedAt()) : null)
                .updatedAt(session.getUpdatedAt() != null ? ISO_FORMATTER.format(session.getUpdatedAt()) : null);

        // Include speakers if requested (Story 1.15a.1b)
        if (includeSpeakers) {
            List<SessionSpeakerResponse> speakers = sessionUserService.getSessionSpeakers(session.getId());
            builder.speakers(speakers);
        }

        return builder.build();
    }

    /**
     * Convert Session entity to SessionResponse DTO (with speakers by default)
     */
    @Transactional(readOnly = true)
    public SessionResponse toSessionResponse(Session session, String eventCode) {
        return toSessionResponse(session, eventCode, true);
    }
}
