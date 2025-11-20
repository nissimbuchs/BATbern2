package ch.batbern.migration.writer;

import ch.batbern.migration.model.target.EventDto;
import ch.batbern.migration.model.target.EventResponse;
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
 * Writes events to Event Management API
 * Implements idempotency handling for 409 Conflict responses
 */
@Slf4j
@Component
public class EventApiWriter implements ItemWriter<EventDto> {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private EntityIdMappingService idMappingService;

    @Value("${migration.target-api.event-management.base-url}")
    private String eventApiUrl;

    @Override
    public void write(Chunk<? extends EventDto> chunk) throws Exception {
        for (EventDto event : chunk.getItems()) {
            try {
                createEvent(event);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("Event already exists: {} (BAT {})", event.getEventCode(), event.getEventNumber());
                    handleExistingEvent(event);
                } else {
                    log.error("Failed to create event: {} - {}", event.getEventCode(), e.getMessage());
                    throw e; // Let retry/skip policy handle
                }
            }
        }
    }

    private void createEvent(EventDto event) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<EventDto> request = new HttpEntity<>(event, headers);

        ResponseEntity<EventResponse> response = restTemplate.exchange(
            eventApiUrl + "/api/events",
            HttpMethod.POST,
            request,
            EventResponse.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            UUID eventId = response.getBody().getId();

            // Store mapping: BAT number → Event UUID
            idMappingService.storeMapping("Event", String.valueOf(event.getEventNumber()), eventId);

            log.info("Created event: {} → UUID: {}", event.getEventCode(), eventId);
        }
    }

    /**
     * Handle existing event (idempotency)
     * Query existing event to get UUID and store mapping
     */
    private void handleExistingEvent(EventDto event) {
        try {
            // Query by event code to get UUID
            ResponseEntity<EventResponse> response = restTemplate.getForEntity(
                eventApiUrl + "/api/events/by-code/" + event.getEventCode(),
                EventResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UUID eventId = response.getBody().getId();
                idMappingService.storeMapping("Event", String.valueOf(event.getEventNumber()), eventId);
                log.info("Found existing event: {} → UUID: {}", event.getEventCode(), eventId);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve existing event UUID for: {}", event.getEventCode(), e);
        }
    }
}
