package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.event.CompanyCreatedEvent;
import ch.batbern.companyuser.event.CompanyDeletedEvent;
import ch.batbern.companyuser.event.CompanyUpdatedEvent;
import ch.batbern.companyuser.event.CompanyVerifiedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CompanyEventPublisher
 * Tests AC7: Domain Events
 * - AC7.1: Publish CompanyCreatedEvent when company created
 * - AC7.2: Publish CompanyUpdatedEvent when company updated
 * - AC7.3: Include company ID in all events
 * - AC7.4: Send events to EventBridge
 */
@ExtendWith(MockitoExtension.class)
class CompanyEventPublisherTest {

    @Mock
    private EventBridgeClient eventBridgeClient;

    private CompanyEventPublisher companyEventPublisher;

    private static final String EVENT_BUS_NAME = "batbern-test-event-bus";
    private static final String EVENT_SOURCE = "ch.batbern.company";
    private UUID testCompanyId;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        testCompanyId = UUID.randomUUID();
        testCompany = Company.builder()
                .id(testCompanyId)
                .name("Test Company")
                .displayName("Test Company Ltd")
                .swissUID("CHE-123.456.789")
                .website("https://test.com")
                .industry("Technology")
                .description("Test company description")
                .isVerified(false)
                .createdBy("test-user-123")
                .build();
        testCompany.onCreate(); // Set timestamps

        // Initialize publisher with configuration values
        companyEventPublisher = new CompanyEventPublisher(
                eventBridgeClient,
                EVENT_BUS_NAME,
                EVENT_SOURCE
        );
    }

    // AC7.1: Test CompanyCreatedEvent publishing
    @Test
    void should_publishCompanyCreatedEvent_when_companyCreated() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequest request = requestCaptor.getValue();
        assertThat(request.entries()).hasSize(1);

        PutEventsRequestEntry entry = request.entries().get(0);
        assertThat(entry.eventBusName()).isEqualTo(EVENT_BUS_NAME);
        assertThat(entry.source()).isEqualTo(EVENT_SOURCE);
        assertThat(entry.detailType()).isEqualTo("CompanyCreated");
        assertThat(entry.detail()).contains(testCompanyId.toString());
        assertThat(entry.detail()).contains("Test Company");
    }

    @Test
    void should_includeAllCompanyFields_when_companyCreatedEventPublished() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        String eventDetail = requestCaptor.getValue().entries().get(0).detail();
        assertThat(eventDetail).contains(testCompanyId.toString());
        assertThat(eventDetail).contains("Test Company");
        assertThat(eventDetail).contains("CHE-123.456.789");
        assertThat(eventDetail).contains("https://test.com");
        assertThat(eventDetail).contains("Technology");
    }

    // AC7.2: Test CompanyUpdatedEvent publishing
    @Test
    void should_publishCompanyUpdatedEvent_when_companyUpdated() {
        // Given
        testCompany.setDisplayName("Updated Company Name");
        testCompany.onUpdate(); // Update timestamp

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyUpdatedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequest request = requestCaptor.getValue();
        assertThat(request.entries()).hasSize(1);

        PutEventsRequestEntry entry = request.entries().get(0);
        assertThat(entry.eventBusName()).isEqualTo(EVENT_BUS_NAME);
        assertThat(entry.source()).isEqualTo(EVENT_SOURCE);
        assertThat(entry.detailType()).isEqualTo("CompanyUpdated");
        assertThat(entry.detail()).contains(testCompanyId.toString());
        assertThat(entry.detail()).contains("Updated Company Name");
    }

    @Test
    void should_publishCompanyDeletedEvent_when_companyDeleted() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyDeletedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequest request = requestCaptor.getValue();
        assertThat(request.entries()).hasSize(1);

        PutEventsRequestEntry entry = request.entries().get(0);
        assertThat(entry.eventBusName()).isEqualTo(EVENT_BUS_NAME);
        assertThat(entry.source()).isEqualTo(EVENT_SOURCE);
        assertThat(entry.detailType()).isEqualTo("CompanyDeleted");
        assertThat(entry.detail()).contains(testCompanyId.toString());
    }

    @Test
    void should_publishCompanyVerifiedEvent_when_companyVerified() {
        // Given
        testCompany.markAsVerified();

        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyVerifiedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequest request = requestCaptor.getValue();
        assertThat(request.entries()).hasSize(1);

        PutEventsRequestEntry entry = request.entries().get(0);
        assertThat(entry.eventBusName()).isEqualTo(EVENT_BUS_NAME);
        assertThat(entry.source()).isEqualTo(EVENT_SOURCE);
        assertThat(entry.detailType()).isEqualTo("CompanyVerified");
        assertThat(entry.detail()).contains(testCompanyId.toString());
        assertThat(entry.detail()).contains("true"); // isVerified field
    }

    // AC7.3: Test that company ID is included in all events
    @Test
    void should_includeCompanyId_when_eventPublished() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When - Test all event types
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);
        companyEventPublisher.publishCompanyUpdatedEvent(testCompany);
        companyEventPublisher.publishCompanyDeletedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient, times(3)).putEvents(requestCaptor.capture());

        for (PutEventsRequest request : requestCaptor.getAllValues()) {
            String eventDetail = request.entries().get(0).detail();
            assertThat(eventDetail).contains(testCompanyId.toString());
        }
    }

    // AC7.4: Test EventBridge integration
    @Test
    void should_sendToEventBridge_when_eventCreated() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);

        // Then
        verify(eventBridgeClient).putEvents(any(PutEventsRequest.class));
    }

    @Test
    void should_useCorrectEventBusName_when_publishingEvent() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequestEntry entry = requestCaptor.getValue().entries().get(0);
        assertThat(entry.eventBusName()).isEqualTo(EVENT_BUS_NAME);
    }

    @Test
    void should_useCorrectEventSource_when_publishingEvent() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequestEntry entry = requestCaptor.getValue().entries().get(0);
        assertThat(entry.source()).isEqualTo(EVENT_SOURCE);
    }

    @Test
    void should_throwException_when_eventBridgePublishingFails() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(1)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When & Then
        assertThatThrownBy(() -> companyEventPublisher.publishCompanyCreatedEvent(testCompany))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish event");
    }

    @Test
    void should_includeTimestamp_when_eventPublished() {
        // Given
        PutEventsResponse mockResponse = PutEventsResponse.builder()
                .failedEntryCount(0)
                .build();
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(mockResponse);

        // When
        companyEventPublisher.publishCompanyCreatedEvent(testCompany);

        // Then
        ArgumentCaptor<PutEventsRequest> requestCaptor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(requestCaptor.capture());

        PutEventsRequestEntry entry = requestCaptor.getValue().entries().get(0);
        assertThat(entry.time()).isNotNull();
    }
}
