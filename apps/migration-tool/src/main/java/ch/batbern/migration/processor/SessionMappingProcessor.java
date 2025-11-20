package ch.batbern.migration.processor;

import ch.batbern.migration.model.legacy.LegacySession;
import ch.batbern.migration.model.legacy.LegacySpeaker;
import ch.batbern.migration.model.target.SessionDto;
import ch.batbern.migration.service.EntityIdMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Processor for session migration
 * Maps legacy session to target SessionDto with eventId lookup
 *
 * Story: 3.2.1 - AC6: Session Migration
 */
@Component
public class SessionMappingProcessor implements ItemProcessor<LegacySession, SessionDto> {

    private static final Logger log = LoggerFactory.getLogger(SessionMappingProcessor.class);

    @Autowired
    private EntityIdMappingService idMappingService;

    @Override
    public SessionDto process(LegacySession legacy) throws Exception {
        // Lookup eventId from entity_id_mapping (Event must be migrated first)
        UUID eventId = idMappingService.getNewId("Event", String.valueOf(legacy.getBat()));

        SessionDto session = new SessionDto();
        session.setEventId(eventId);
        session.setTitle(legacy.getTitle());
        session.setDescription(legacy.getSessionAbstract());

        // orderInProgram: Use a generated order based on BAT number since legacy doesn't have explicit order
        // In production, this could be enhanced with actual ordering logic
        session.setOrderInProgram(1);  // Placeholder - will be enhanced in refactoring phase

        // Store PDF filename for file migration (Task 6)
        if (legacy.getPdf() != null && !legacy.getPdf().isEmpty()) {
            // S3 key pattern will be: presentations/{eventNumber}/{filename}
            String s3Key = String.format("presentations/%d/%s", legacy.getBat(), legacy.getPdf());
            session.setPresentationFileS3Key(s3Key);
        }

        // Extract speaker names for SessionUser creation (AC12)
        if (legacy.getReferenten() != null && !legacy.getReferenten().isEmpty()) {
            List<String> speakerNames = legacy.getReferenten().stream()
                    .map(LegacySpeaker::getName)
                    .collect(Collectors.toList());
            session.setSpeakerNames(speakerNames);
        }

        log.debug("Mapped session '{}' for BAT {} (eventId: {})", legacy.getTitle(), legacy.getBat(), eventId);

        return session;
    }
}
