package ch.batbern.migration.writer;

import ch.batbern.migration.model.target.SpeakerDto;
import ch.batbern.migration.model.target.SpeakerResponse;
import ch.batbern.migration.model.target.UserDto;
import ch.batbern.migration.model.target.UserResponse;
import ch.batbern.migration.processor.UserSpeakerMappingProcessor;
import ch.batbern.migration.service.EntityIdMappingService;
import lombok.extern.slf4j.Slf4j;
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
 * Writes User + Speaker to respective APIs
 * Two-step process:
 * 1. Create User in Company Management API
 * 2. Create Speaker in Speaker Coordination API (using userId from step 1)
 *
 * Implements idempotency handling for 409 Conflict responses
 */
@Slf4j
@Component
public class UserSpeakerApiWriter implements ItemWriter<UserSpeakerMappingProcessor.UserSpeakerPair> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private EntityIdMappingService idMappingService;

    @Value("${migration.target-api.company-management.base-url}")
    private String companyApiUrl;

    @Value("${migration.target-api.speaker-coordination.base-url}")
    private String speakerApiUrl;

    @Override
    public void write(Chunk<? extends UserSpeakerMappingProcessor.UserSpeakerPair> chunk) throws Exception {
        for (UserSpeakerMappingProcessor.UserSpeakerPair pair : chunk.getItems()) {
            try {
                // Step 1: Create User
                UUID userId = createUser(pair.getUser(), pair.getUsername());

                // Step 2: Create Speaker (with userId from step 1)
                createSpeaker(pair.getSpeaker(), userId, pair.getUsername());

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("User already exists: {}", pair.getUsername());
                    handleExistingUser(pair);
                } else {
                    log.error("Failed to create user/speaker: {} - {}", pair.getUsername(), e.getMessage());
                    throw e; // Let retry/skip policy handle
                }
            }
        }
    }

    /**
     * Create User in Company Management API
     * Returns User UUID for Speaker creation
     */
    private UUID createUser(UserDto user, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<UserDto> request = new HttpEntity<>(user, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange(
            companyApiUrl + "/api/users",
            HttpMethod.POST,
            request,
            UserResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            UUID userId = response.getBody().getId();

            // Store mapping: username → User UUID (for SessionUser creation later)
            idMappingService.storeMapping("User", username, userId);

            log.info("Created user: {} → UUID: {}", username, userId);
            return userId;
        }

        throw new RuntimeException("Failed to create user: " + username);
    }

    /**
     * Create Speaker in Speaker Coordination API
     */
    private void createSpeaker(SpeakerDto speaker, UUID userId, String username) {
        // Set userId from User creation
        speaker.setUserId(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SpeakerDto> request = new HttpEntity<>(speaker, headers);

        ResponseEntity<SpeakerResponse> response = restTemplate.exchange(
            speakerApiUrl + "/api/speakers",
            HttpMethod.POST,
            request,
            SpeakerResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            UUID speakerId = response.getBody().getId();

            // Store mapping: username → Speaker UUID
            idMappingService.storeMapping("Speaker", username, speakerId);

            log.info("Created speaker: {} → Speaker UUID: {}", username, speakerId);
        }
    }

    /**
     * Handle existing user (idempotency)
     * Query existing user to get UUID and create speaker if needed
     */
    private void handleExistingUser(UserSpeakerMappingProcessor.UserSpeakerPair pair) {
        try {
            // Query by username to get UUID
            ResponseEntity<UserResponse> response = restTemplate.getForEntity(
                companyApiUrl + "/api/users/by-username/" + pair.getUsername(),
                UserResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UUID userId = response.getBody().getId();
                idMappingService.storeMapping("User", pair.getUsername(), userId);

                // Try to create speaker (may also already exist)
                try {
                    createSpeaker(pair.getSpeaker(), userId, pair.getUsername());
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        log.info("Speaker also already exists: {}", pair.getUsername());
                    } else {
                        throw e;
                    }
                }

                log.info("Found existing user: {} → UUID: {}", pair.getUsername(), userId);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve existing user UUID for: {}", pair.getUsername(), e);
        }
    }
}
