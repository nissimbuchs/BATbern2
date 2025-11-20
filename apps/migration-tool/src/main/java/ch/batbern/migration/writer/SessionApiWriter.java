package ch.batbern.migration.writer;

import ch.batbern.migration.model.target.SessionDto;
import ch.batbern.migration.model.target.SessionResponse;
import ch.batbern.migration.model.target.SessionUserDto;
import ch.batbern.migration.service.EntityIdMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Writer for session migration
 * Posts sessions to Event Management API and creates SessionUser junctions
 *
 * Story: 3.2.1 - AC6, AC12: Session Migration + SessionUser Creation
 */
@Component
public class SessionApiWriter implements ItemWriter<SessionDto> {

    private static final Logger log = LoggerFactory.getLogger(SessionApiWriter.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private EntityIdMappingService idMappingService;

    @Value("${migration.target-api.event-management.base-url}")
    private String eventApiUrl;

    @Override
    public void write(Chunk<? extends SessionDto> chunk) throws Exception {
        for (SessionDto session : chunk) {
            try {
                // POST to Event Management API
                HttpEntity<SessionDto> request = new HttpEntity<>(session);
                ResponseEntity<SessionResponse> response = restTemplate.exchange(
                        eventApiUrl + "/api/sessions",
                        HttpMethod.POST,
                        request,
                        SessionResponse.class
                );

                UUID newSessionId = response.getBody().getId();

                // Store ID mapping for SessionUser creation
                String legacyId = session.getEventId() + "_" + session.getOrderInProgram();
                idMappingService.storeMapping("Session", legacyId, newSessionId);

                log.info("Migrated session: {} → UUID: {}", session.getTitle(), newSessionId);

                // Create SessionUser junctions (AC12)
                if (session.getSpeakerNames() != null && !session.getSpeakerNames().isEmpty()) {
                    createSessionUsers(newSessionId, session.getSpeakerNames());
                }

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("Session already exists: {}", session.getTitle());
                    // Idempotency: Session already migrated, skip
                } else {
                    log.error("Failed to create session: {}", session.getTitle(), e);
                    throw e;
                }
            }
        }
    }

    /**
     * Create SessionUser junction records for speakers
     * AC12: Establish SessionUser relationships
     */
    private void createSessionUsers(UUID sessionId, Iterable<String> speakerNames) {
        for (String speakerName : speakerNames) {
            try {
                // Generate username from speaker name for lookup
                String username = generateUsername(speakerName);

                // Lookup userId from entity_id_mapping (User must be migrated first)
                UUID userId = idMappingService.getNewId("User", username);

                // Create SessionUser junction
                SessionUserDto sessionUser = new SessionUserDto();
                sessionUser.setSessionId(sessionId);
                sessionUser.setUserId(userId);
                sessionUser.setRole("SPEAKER");

                HttpEntity<SessionUserDto> request = new HttpEntity<>(sessionUser);
                ResponseEntity<Void> response = restTemplate.exchange(
                        eventApiUrl + "/api/session-users",
                        HttpMethod.POST,
                        request,
                        Void.class
                );

                log.info("Created SessionUser: sessionId={}, userId={}, speaker={}", sessionId, userId, speakerName);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("SessionUser already exists: sessionId={}, speaker={}", sessionId, speakerName);
                } else {
                    log.error("Failed to create SessionUser: sessionId={}, speaker={}", sessionId, speakerName, e);
                    // Don't throw - continue processing other speakers
                }
            } catch (IllegalStateException e) {
                log.warn("User not found in mapping: {} - skipping SessionUser creation", speakerName);
                // Continue processing - some speakers may not have been migrated yet
            }
        }
    }

    /**
     * Generate username from speaker name (matches UserSpeakerMappingProcessor logic)
     * Example: "Thomas Goetz, Die Mobiliar" → "thomas.goetz"
     */
    private String generateUsername(String speakerName) {
        // Remove company suffix (", Company Name")
        String nameOnly = speakerName.split(",")[0].trim();

        // Normalize German characters
        nameOnly = nameOnly.toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replace("é", "e")
                .replace("è", "e")
                .replace("à", "a");

        // Convert to firstname.lastname format
        String[] parts = nameOnly.split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[parts.length - 1];
        } else {
            return parts[0];  // Single name
        }
    }
}
