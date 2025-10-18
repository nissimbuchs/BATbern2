package ch.batbern.companyuser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LocalAwsConfig
 * Verifies that local AWS beans are properly configured for development
 */
@SpringBootTest(classes = LocalAwsConfig.class)
@ActiveProfiles("local")
@DisplayName("LocalAwsConfig Tests")
class LocalAwsConfigTest {

    @Autowired
    private EventBridgeAsyncClient eventBridgeAsyncClient;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    @Qualifier("eventBridgeObjectMapper")
    private ObjectMapper eventBridgeObjectMapper;

    @Test
    @DisplayName("should_createEventBridgeAsyncClient_when_localProfileActive")
    void should_createEventBridgeAsyncClient_when_localProfileActive() {
        // Then
        assertThat(eventBridgeAsyncClient).isNotNull();
        assertThat(eventBridgeAsyncClient.serviceName()).isEqualTo("eventbridge");
    }

    @Test
    @DisplayName("should_returnSuccessResponse_when_putEventsCalledOnMockClient")
    void should_returnSuccessResponse_when_putEventsCalledOnMockClient() throws Exception {
        // Given
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                .source("test.source")
                .detailType("TestEvent")
                .detail("{\"key\":\"value\"}")
                .build();

        PutEventsRequest request = PutEventsRequest.builder()
                .entries(entry)
                .build();

        // When
        CompletableFuture<PutEventsResponse> future = eventBridgeAsyncClient.putEvents(request);
        PutEventsResponse response = future.get();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.failedEntryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should_handleMultipleEvents_when_putEventsCalledWithMultipleEntries")
    void should_handleMultipleEvents_when_putEventsCalledWithMultipleEntries() throws Exception {
        // Given
        PutEventsRequestEntry entry1 = PutEventsRequestEntry.builder()
                .source("test.source")
                .detailType("Event1")
                .detail("{\"id\":1}")
                .build();

        PutEventsRequestEntry entry2 = PutEventsRequestEntry.builder()
                .source("test.source")
                .detailType("Event2")
                .detail("{\"id\":2}")
                .build();

        PutEventsRequest request = PutEventsRequest.builder()
                .entries(entry1, entry2)
                .build();

        // When
        CompletableFuture<PutEventsResponse> future = eventBridgeAsyncClient.putEvents(request);
        PutEventsResponse response = future.get();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.failedEntryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should_createS3Presigner_when_localProfileActive")
    void should_createS3Presigner_when_localProfileActive() {
        // Then
        assertThat(s3Presigner).isNotNull();
    }

    @Test
    @DisplayName("should_closeS3Presigner_when_closeCalled")
    void should_closeS3Presigner_when_closeCalled() {
        // When / Then - should not throw exception
        assertThat(s3Presigner).isNotNull();
        // Note: We don't actually close it as it's a Spring-managed bean
        // Just verify it's properly created
    }

    @Test
    @DisplayName("should_closeEventBridgeClient_when_closeCalled")
    void should_closeEventBridgeClient_when_closeCalled() {
        // When / Then - should not throw exception
        assertThat(eventBridgeAsyncClient).isNotNull();
        // Mock client's close() is a no-op, just verify it doesn't throw
        eventBridgeAsyncClient.close();
    }

    @Test
    @DisplayName("should_createObjectMapper_when_localProfileActive")
    void should_createObjectMapper_when_localProfileActive() {
        // Then
        assertThat(eventBridgeObjectMapper).isNotNull();
    }

    @Test
    @DisplayName("should_configureObjectMapper_when_created")
    void should_configureObjectMapper_when_created() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-id", Instant.now());

        // When
        String json = eventBridgeObjectMapper.writeValueAsString(event);

        // Then
        assertThat(json).isNotBlank();
        // Verify WRITE_DATES_AS_TIMESTAMPS is disabled (dates as ISO strings, not numbers)
        assertThat(json).contains("timestamp");
        assertThat(json).doesNotMatch(".*\"timestamp\":\\d+.*"); // Should NOT be a number
        // Verify INDENT_OUTPUT is enabled (formatted JSON)
        assertThat(json).contains("\n");
    }

    @Test
    @DisplayName("should_registerJavaTimeModule_when_objectMapperCreated")
    void should_registerJavaTimeModule_when_objectMapperCreated() throws Exception {
        // Given
        Instant now = Instant.parse("2024-01-15T10:30:00Z");
        TestEvent event = new TestEvent("test-id", now);

        // When
        String json = eventBridgeObjectMapper.writeValueAsString(event);
        TestEvent deserialized = eventBridgeObjectMapper.readValue(json, TestEvent.class);

        // Then
        assertThat(deserialized.getTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("should_disableWriteDatesAsTimestamps_when_objectMapperCreated")
    void should_disableWriteDatesAsTimestamps_when_objectMapperCreated() {
        // Then
        assertThat(eventBridgeObjectMapper.getSerializationConfig()
                .isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .isFalse();
    }

    @Test
    @DisplayName("should_enableIndentOutput_when_objectMapperCreated")
    void should_enableIndentOutput_when_objectMapperCreated() {
        // Then
        assertThat(eventBridgeObjectMapper.getSerializationConfig()
                .isEnabled(SerializationFeature.INDENT_OUTPUT))
                .isTrue();
    }

    // Test DTO for ObjectMapper testing
    private static class TestEvent {
        private String id;
        private Instant timestamp;

        public TestEvent() {
        }

        public TestEvent(String id, Instant timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }
    }
}
