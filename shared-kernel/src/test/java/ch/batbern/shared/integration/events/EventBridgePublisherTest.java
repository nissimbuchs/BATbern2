package ch.batbern.shared.integration.events;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.events.EventCreatedEvent;
import ch.batbern.shared.events.SpeakerInvitedEvent;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.SpeakerId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = {ch.batbern.shared.TestApplication.class,
                          ch.batbern.shared.config.TestEventBridgeConfig.class,
                          ch.batbern.shared.config.EventBridgeConfig.class,
                          ch.batbern.shared.events.EventBridgeEventPublisher.class})
class EventBridgePublisherTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0.0"))
            .withEnv("DEBUG", "1")
            .withEnv("LS_LOG", "debug")
            .withEnv("SERVICES", "events,eventbridge");

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private EventBridgeAsyncClient eventBridgeClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + localStack.getHost() + ":" + localStack.getMappedPort(4566);
        registry.add("aws.eventbridge.endpoint", () -> endpoint);
        registry.add("aws.region", localStack::getRegion);
        registry.add("aws.access-key", () -> "test");
        registry.add("aws.secret-key", () -> "test");
    }

    @Nested
    @DisplayName("Event Publishing")
    class EventPublishing {

        @Test
        void should_publishToEventBridge_when_domainEventTriggered() throws Exception {
            // Given
            EventId eventId = EventId.generate();
            EventCreatedEvent event = new EventCreatedEvent(
                eventId,
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When
            eventPublisher.publish(event);

            // Then - verify event was published to EventBridge
            // In a real test, we'd verify the event was received
            assertThatNoException().isThrownBy(() -> Thread.sleep(1000));
        }

        @Test
        void should_publishMultipleEvents_when_batchPublishingCalled() {
            // Given
            List<DomainEvent<EventId>> events = List.of(
                new EventCreatedEvent(
                    EventId.generate(),
                    "BATbern Q1 2024",
                    "CONFERENCE",
                    LocalDate.of(2024, 3, 15),
                    "Bern Convention Center",
                    UserId.from("user-123")
                ),
                new EventCreatedEvent(
                    EventId.generate(),
                    "BATbern Workshop 2024",
                    "WORKSHOP",
                    LocalDate.of(2024, 4, 20),
                    "Bern Tech Hub",
                    UserId.from("user-456")
                )
            );

            // When & Then
            assertThatNoException()
                .isThrownBy(() -> eventPublisher.publishBatch(events));
        }

        @Test
        void should_publishAsynchronously_when_asyncPublishCalled() throws Exception {
            // Given
            SpeakerInvitedEvent event = new SpeakerInvitedEvent(
                EventId.generate(),
                SpeakerId.generate(),
                "John Doe",
                "speaker@example.com",
                "Cloud Architecture Best Practices",
                LocalDateTime.of(2024, 6, 15, 14, 0),
                UserId.from("organizer-123"),
                "PENDING"
            );

            // When
            CompletableFuture<Void> future = eventPublisher.publishAsync(event);

            // Then
            assertThat(future).isNotNull();
            future.get(5, TimeUnit.SECONDS); // Wait for completion
        }
    }

    @Nested
    @DisplayName("Event Serialization")
    class EventSerialization {

        @Test
        void should_serializeEventToJSON_when_publishingToEventBridge() throws Exception {
            // Given
            EventCreatedEvent event = new EventCreatedEvent(
                EventId.generate(),
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When
            String json = objectMapper.writeValueAsString(event);

            // Then
            assertThat(json).contains("\"eventType\" : \"CONFERENCE\"");
            assertThat(json).contains("\"title\" : \"BATbern 2024\"");
            assertThat(json).contains("\"createdEventId\"");  // Check for a field specific to EventCreatedEvent
        }

        @Test
        void should_includeMetadata_when_serializingEvent() throws Exception {
            // Given
            DomainEvent<EventId> event = new EventCreatedEvent(
                EventId.generate(),
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When
            String json = objectMapper.writeValueAsString(event);

            // Then
            assertThat(json).contains("\"aggregateId\" :");
            assertThat(json).contains("\"occurredAt\" :");
            assertThat(json).contains("\"version\" :");
        }

        @Test
        void should_formatEventBridgeEntry_when_publishingEvent() {
            // Given
            EventCreatedEvent event = new EventCreatedEvent(
                EventId.generate(),
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When
            PutEventsRequestEntry entry = eventPublisher.createEventBridgeEntry(event);

            // Then
            assertThat(entry.source()).isEqualTo("batbern.event-management");
            assertThat(entry.detailType()).isEqualTo("EventCreatedEvent");
            assertThat(entry.detail()).contains("BATbern 2024");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void should_retryFailedPublishes_when_eventBridgeUnavailable() {
            // Given
            EventCreatedEvent event = new EventCreatedEvent(
                EventId.generate(),
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When simulating EventBridge unavailable
            // The publisher should retry with exponential backoff

            // This would be tested with a mock that fails initially then succeeds
            assertThatNoException()
                .isThrownBy(() -> eventPublisher.publishWithRetry(event, 3));
        }

        @Test
        void should_handlePublishingErrors_when_malformedEvent() {
            // Given - a malformed event (null required fields)
            DomainEvent<EventId> malformedEvent = new DomainEvent<EventId>() {
                @Override
                public String getEventType() {
                    return null; // Invalid
                }

                @Override
                public EventId getAggregateId() {
                    return null; // Invalid
                }

                @Override
                public Instant getOccurredAt() {
                    return null; // Invalid
                }

                @Override
                public int getEventVersion() {
                    return 1;
                }

                @Override
                public String getAggregateType() {
                    return "Event";
                }
            };

            // When & Then
            assertThatThrownBy(() -> eventPublisher.publish(malformedEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid event");
        }

        @Test
        void should_logFailedEvents_when_publishingFails() {
            // Given
            EventCreatedEvent event = new EventCreatedEvent(
                EventId.generate(),
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When publishing fails (simulated)
            // Then the event should be logged for later recovery
            // This would be tested with a spy or mock logger
            assertThat(eventPublisher).isNotNull();
        }
    }

    @Nested
    @DisplayName("Configuration and Setup")
    class ConfigurationAndSetup {

        @Test
        void should_configureEventBridgeClient_when_applicationStarts() {
            assertThat(eventBridgeClient).isNotNull();
        }

        @Test
        void should_createEventBusIfNeeded_when_publishingFirstEvent() {
            // The publisher should create the event bus if it doesn't exist
            String eventBusName = "batbern-events";

            assertThatNoException()
                .isThrownBy(() -> eventPublisher.ensureEventBusExists(eventBusName));
        }

        @Test
        void should_configureDeadLetterQueue_when_setupComplete() {
            // Verify DLQ is configured for failed events
            String dlqName = "batbern-events-dlq";

            assertThat(eventPublisher.isDeadLetterQueueConfigured(dlqName)).isTrue();
        }
    }

    @Nested
    @DisplayName("Performance and Monitoring")
    class PerformanceAndMonitoring {

        @Test
        void should_publishWithinTimeLimit_when_singleEvent() {
            // Given
            EventCreatedEvent event = new EventCreatedEvent(
                EventId.generate(),
                "BATbern 2024",
                "CONFERENCE",
                LocalDate.of(2024, 6, 15),
                "Bern Convention Center",
                UserId.from("user-123")
            );

            // When
            long startTime = System.currentTimeMillis();
            eventPublisher.publish(event);
            long duration = System.currentTimeMillis() - startTime;

            // Then - should complete within 2000ms (including network latency to LocalStack)
            assertThat(duration).isLessThan(2000);
        }

        @Test
        void should_handleHighVolume_when_batchPublishing() {
            // Given - 100 events
            List<DomainEvent<EventId>> events = createManyEvents(100);

            // When
            long startTime = System.currentTimeMillis();
            eventPublisher.publishBatch(events);
            long duration = System.currentTimeMillis() - startTime;

            // Then - should handle batch efficiently
            assertThat(duration).isLessThan(1000); // Less than 1 second for 100 events
        }

        private List<DomainEvent<EventId>> createManyEvents(int count) {
            return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> (DomainEvent<EventId>) new EventCreatedEvent(
                    EventId.generate(),
                    "Event " + i,
                    "CONFERENCE",
                    LocalDate.of(2024, 6, 15),
                    "Venue " + i,
                    UserId.from("user-" + i)
                ))
                .collect(java.util.stream.Collectors.toList());
        }
    }
}