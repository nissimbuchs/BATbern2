package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.event.CompanyCreatedEvent;
import ch.batbern.companyuser.event.CompanyDeletedEvent;
import ch.batbern.companyuser.event.CompanyUpdatedEvent;
import ch.batbern.companyuser.event.CompanyVerifiedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.time.Instant;

/**
 * Service for publishing company domain events to AWS EventBridge
 * Implements AC7: Domain Events
 *
 * Key Features:
 * - Publishes CompanyCreatedEvent, CompanyUpdatedEvent, CompanyDeletedEvent, CompanyVerifiedEvent
 * - Integrates with AWS EventBridge for event-driven architecture
 * - Includes company ID and full context in all events
 * - Handles event serialization and error handling
 */
@Service
@Slf4j
public class CompanyEventPublisher {

    private final EventBridgeClient eventBridgeClient;
    private final String eventBusName;
    private final String eventSource;
    private final ObjectMapper objectMapper;

    public CompanyEventPublisher(
            EventBridgeClient eventBridgeClient,
            @Value("${aws.eventbridge.bus-name:batbern-default-event-bus}") String eventBusName,
            @Value("${aws.eventbridge.source:ch.batbern.company}") String eventSource) {
        this.eventBridgeClient = eventBridgeClient;
        this.eventBusName = eventBusName;
        this.eventSource = eventSource;

        // Configure ObjectMapper for event serialization
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Publish CompanyCreatedEvent to EventBridge
     * AC7.1: Publish event when company is created
     *
     * @param company The created company
     */
    public void publishCompanyCreatedEvent(Company company) {
        log.info("Publishing CompanyCreatedEvent for company: {}", company.getId());

        CompanyCreatedEvent event = CompanyCreatedEvent.builder()
                .companyId(company.getId())
                .name(company.getName())
                .displayName(company.getDisplayName())
                .swissUID(company.getSwissUID())
                .website(company.getWebsite())
                .industry(company.getIndustry())
                .description(company.getDescription())
                .createdBy(company.getCreatedBy())
                .createdAt(company.getCreatedAt())
                .eventTimestamp(Instant.now())
                .build();

        publishEvent("CompanyCreated", event);
    }

    /**
     * Publish CompanyUpdatedEvent to EventBridge
     * AC7.2: Publish event when company is updated
     *
     * @param company The updated company
     */
    public void publishCompanyUpdatedEvent(Company company) {
        log.info("Publishing CompanyUpdatedEvent for company: {}", company.getId());

        CompanyUpdatedEvent event = CompanyUpdatedEvent.builder()
                .companyId(company.getId())
                .name(company.getName())
                .displayName(company.getDisplayName())
                .swissUID(company.getSwissUID())
                .website(company.getWebsite())
                .industry(company.getIndustry())
                .description(company.getDescription())
                .updatedAt(company.getUpdatedAt())
                .eventTimestamp(Instant.now())
                .build();

        publishEvent("CompanyUpdated", event);
    }

    /**
     * Publish CompanyDeletedEvent to EventBridge
     * AC7.2: Publish event when company is deleted
     *
     * @param company The deleted company
     */
    public void publishCompanyDeletedEvent(Company company) {
        log.info("Publishing CompanyDeletedEvent for company: {}", company.getId());

        CompanyDeletedEvent event = CompanyDeletedEvent.builder()
                .companyId(company.getId())
                .name(company.getName())
                .deletedAt(Instant.now())
                .eventTimestamp(Instant.now())
                .build();

        publishEvent("CompanyDeleted", event);
    }

    /**
     * Publish CompanyVerifiedEvent to EventBridge
     * AC7.2: Publish event when company is verified
     *
     * @param company The verified company
     */
    public void publishCompanyVerifiedEvent(Company company) {
        log.info("Publishing CompanyVerifiedEvent for company: {}", company.getId());

        CompanyVerifiedEvent event = CompanyVerifiedEvent.builder()
                .companyId(company.getId())
                .name(company.getName())
                .swissUID(company.getSwissUID())
                .isVerified(company.isVerified())
                .verifiedAt(company.getUpdatedAt())
                .eventTimestamp(Instant.now())
                .build();

        publishEvent("CompanyVerified", event);
    }

    /**
     * Publish an event to AWS EventBridge
     * AC7.4: Send events to EventBridge
     *
     * @param detailType The event detail type
     * @param event The event object to serialize
     */
    private void publishEvent(String detailType, Object event) {
        try {
            String eventDetail = objectMapper.writeValueAsString(event);

            PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                    .eventBusName(eventBusName)
                    .source(eventSource)
                    .detailType(detailType)
                    .detail(eventDetail)
                    .time(Instant.now())
                    .build();

            PutEventsRequest request = PutEventsRequest.builder()
                    .entries(entry)
                    .build();

            PutEventsResponse response = eventBridgeClient.putEvents(request);

            if (response.failedEntryCount() > 0) {
                log.error("Failed to publish event to EventBridge. Failed entries: {}",
                        response.failedEntryCount());
                throw new RuntimeException("Failed to publish event to EventBridge");
            }

            log.info("Successfully published {} event to EventBridge", detailType);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", detailType, e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
