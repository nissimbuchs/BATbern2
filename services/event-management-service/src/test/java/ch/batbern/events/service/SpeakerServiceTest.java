package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.dto.SpeakerRequest;
import ch.batbern.events.dto.SpeakerResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.event.SpeakerCreatedEvent;
import ch.batbern.events.event.SpeakerUpdatedEvent;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for SpeakerService - Story 6.0.
 *
 * Tests service logic with mocked repository and UserApiClient.
 * Validates ADR-004 compliance: User enrichment via HTTP.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerServiceTest {

    @Mock
    private SpeakerRepository speakerRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SpeakerService speakerService;

    private UserResponse mockUserResponse;
    private Speaker mockSpeaker;

    @BeforeEach
    void setUp() {
        mockUserResponse = new UserResponse();
        mockUserResponse.setId("john.doe");
        mockUserResponse.setEmail("john.doe@example.com");
        mockUserResponse.setFirstName("John");
        mockUserResponse.setLastName("Doe");
        mockUserResponse.setBio("Expert architect");
        mockUserResponse.setCompanyId("GoogleZH");

        mockSpeaker = Speaker.builder()
                .id(UUID.randomUUID())
                .username("john.doe")
                .availability(SpeakerAvailability.AVAILABLE)
                .workflowState(SpeakerWorkflowState.IDENTIFIED)
                .expertiseAreas(List.of("Security", "Cloud"))
                .speakingTopics(List.of("AWS", "Kubernetes"))
                .languages(List.of("de", "en"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // Create Speaker tests

    @Test
    void should_createSpeaker_when_validRequestProvided() {
        // Given
        SpeakerRequest request = SpeakerRequest.builder()
                .username("john.doe")
                .availability(SpeakerAvailability.AVAILABLE)
                .expertiseAreas(List.of("Security"))
                .build();

        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);
        when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.empty());
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);

        // When
        SpeakerResponse response = speakerService.createSpeaker(request);

        // Then
        assertThat(response.getUsername()).isEqualTo("john.doe");
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getCompany()).isEqualTo("GoogleZH");
        verify(speakerRepository).save(any(Speaker.class));
    }

    @Test
    void should_throwException_when_speakerAlreadyExists() {
        // Given
        SpeakerRequest request = SpeakerRequest.builder()
                .username("john.doe")
                .build();

        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);
        when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(mockSpeaker));

        // When/Then
        assertThatThrownBy(() -> speakerService.createSpeaker(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(speakerRepository, never()).save(any());
    }

    // Get Speaker tests

    @Test
    void should_getSpeakerByUsername_when_speakerExists() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        SpeakerResponse response = speakerService.getSpeakerByUsername("john.doe");

        // Then - ADR-004: User data enriched via HTTP
        assertThat(response.getUsername()).isEqualTo("john.doe");
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getBio()).isEqualTo("Expert architect");
        assertThat(response.getAvailability()).isEqualTo(SpeakerAvailability.AVAILABLE);
        assertThat(response.getExpertiseAreas()).containsExactly("Security", "Cloud");
    }

    @Test
    void should_throwSpeakerNotFoundException_when_speakerNotFound() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("nonexistent.user"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> speakerService.getSpeakerByUsername("nonexistent.user"))
                .isInstanceOf(SpeakerNotFoundException.class)
                .hasMessageContaining("nonexistent.user");
    }

    // Update Speaker tests

    @Test
    void should_updateSpeaker_when_validRequestProvided() {
        // Given
        SpeakerRequest updateRequest = SpeakerRequest.builder()
                .availability(SpeakerAvailability.BUSY)
                .expertiseAreas(List.of("AI/ML", "Data"))
                .build();

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        SpeakerResponse response = speakerService.updateSpeaker("john.doe", updateRequest);

        // Then
        verify(speakerRepository).save(any(Speaker.class));
        assertThat(mockSpeaker.getAvailability()).isEqualTo(SpeakerAvailability.BUSY);
    }

    @Test
    void should_notUpdateNullFields_when_partialUpdateProvided() {
        // Given - only update availability, keep other fields
        SpeakerRequest updateRequest = SpeakerRequest.builder()
                .availability(SpeakerAvailability.UNAVAILABLE)
                .build();

        List<String> originalExpertise = mockSpeaker.getExpertiseAreas();

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        speakerService.updateSpeaker("john.doe", updateRequest);

        // Then - expertise areas unchanged
        assertThat(mockSpeaker.getExpertiseAreas()).isEqualTo(originalExpertise);
        assertThat(mockSpeaker.getAvailability()).isEqualTo(SpeakerAvailability.UNAVAILABLE);
    }

    // Delete Speaker tests

    @Test
    void should_softDeleteSpeaker_when_speakerExists() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);

        // When
        speakerService.deleteSpeaker("john.doe");

        // Then - soft delete sets deletedAt
        assertThat(mockSpeaker.getDeletedAt()).isNotNull();
        verify(speakerRepository).save(mockSpeaker);
    }

    @Test
    void should_throwException_when_deletingNonexistentSpeaker() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("nonexistent.user"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> speakerService.deleteSpeaker("nonexistent.user"))
                .isInstanceOf(SpeakerNotFoundException.class);

        verify(speakerRepository, never()).save(any());
    }

    // List Speakers tests

    @Test
    @SuppressWarnings("unchecked")
    void should_listSpeakers_when_paginationRequested() {
        // Given
        Page<Speaker> speakerPage = new PageImpl<>(List.of(mockSpeaker));
        when(speakerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(speakerPage);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        Page<SpeakerResponse> result = speakerService.listSpeakers(null, null, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("john.doe");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_filterByAvailability_when_availabilitySpecified() {
        // Given
        Page<Speaker> speakerPage = new PageImpl<>(List.of(mockSpeaker));
        when(speakerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(speakerPage);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        speakerService.listSpeakers(SpeakerAvailability.AVAILABLE, null, PageRequest.of(0, 10));

        // Then - verify specification was built with availability filter
        verify(speakerRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    // Exists check tests

    @Test
    void should_returnTrue_when_speakerExists() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));

        // When
        boolean exists = speakerService.speakerExists("john.doe");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void should_returnFalse_when_speakerDoesNotExist() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("nonexistent.user"))
                .thenReturn(Optional.empty());

        // When
        boolean exists = speakerService.speakerExists("nonexistent.user");

        // Then
        assertThat(exists).isFalse();
    }

    // Domain Event tests - AC7

    @Test
    void should_publishSpeakerCreatedEvent_when_speakerCreated() {
        // Given
        SpeakerRequest request = SpeakerRequest.builder()
                .username("john.doe")
                .availability(SpeakerAvailability.AVAILABLE)
                .expertiseAreas(List.of("Security"))
                .build();

        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);
        when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.empty());
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);

        // When
        speakerService.createSpeaker(request);

        // Then - verify SpeakerCreatedEvent is published
        ArgumentCaptor<SpeakerCreatedEvent> eventCaptor = ArgumentCaptor.forClass(SpeakerCreatedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());

        SpeakerCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getUsername()).isEqualTo("john.doe");
        assertThat(event.getAggregateId()).isEqualTo(mockSpeaker.getId());
    }

    @Test
    void should_publishSpeakerUpdatedEvent_when_speakerUpdated() {
        // Given
        SpeakerRequest updateRequest = SpeakerRequest.builder()
                .availability(SpeakerAvailability.BUSY)
                .expertiseAreas(List.of("AI/ML", "Data"))
                .build();

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        speakerService.updateSpeaker("john.doe", updateRequest);

        // Then - verify SpeakerUpdatedEvent is published
        ArgumentCaptor<SpeakerUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(SpeakerUpdatedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());

        SpeakerUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.getUsername()).isEqualTo("john.doe");
        assertThat(event.getAggregateId()).isEqualTo(mockSpeaker.getId());
    }

    @Test
    void should_notPublishEvent_when_speakerCreationFails() {
        // Given
        SpeakerRequest request = SpeakerRequest.builder()
                .username("john.doe")
                .build();

        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);
        when(speakerRepository.findByUsername("john.doe")).thenReturn(Optional.of(mockSpeaker));

        // When/Then
        assertThatThrownBy(() -> speakerService.createSpeaker(request))
                .isInstanceOf(IllegalArgumentException.class);

        // Verify no event published on failure
        verify(domainEventPublisher, never()).publish(any());
    }

    // getSpeakerEntityByUsername tests - internal method for cross-service use

    @Test
    void should_getSpeakerEntity_when_speakerExistsWithoutEnrichment() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));

        // When
        Speaker entity = speakerService.getSpeakerEntityByUsername("john.doe");

        // Then - returns raw entity without user enrichment
        assertThat(entity.getUsername()).isEqualTo("john.doe");
        assertThat(entity.getAvailability()).isEqualTo(SpeakerAvailability.AVAILABLE);
        // Verify no user API call for entity method
        verify(userApiClient, never()).getUserByUsername(anyString());
    }

    @Test
    void should_throwSpeakerNotFoundException_when_gettingEntityForNonexistent() {
        // Given
        when(speakerRepository.findByUsernameAndDeletedAtIsNull("nonexistent.user"))
                .thenReturn(Optional.empty());

        // When/Then - covers lambda$getSpeakerEntityByUsername$1 exception path
        assertThatThrownBy(() -> speakerService.getSpeakerEntityByUsername("nonexistent.user"))
                .isInstanceOf(SpeakerNotFoundException.class)
                .hasMessageContaining("nonexistent.user");
    }

    @Test
    void should_throwSpeakerNotFoundException_when_updatingNonexistentSpeaker() {
        // Given
        SpeakerRequest updateRequest = SpeakerRequest.builder()
                .availability(SpeakerAvailability.BUSY)
                .build();

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("nonexistent.user"))
                .thenReturn(Optional.empty());

        // When/Then - covers lambda$updateSpeaker$2 exception path
        assertThatThrownBy(() -> speakerService.updateSpeaker("nonexistent.user", updateRequest))
                .isInstanceOf(SpeakerNotFoundException.class)
                .hasMessageContaining("nonexistent.user");

        verify(speakerRepository, never()).save(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    // Update speaker field coverage tests - ensure all update branches are covered

    @Test
    void should_updateAllFields_when_allFieldsProvided() {
        // Given - request with all updatable fields
        SpeakerRequest updateRequest = SpeakerRequest.builder()
                .availability(SpeakerAvailability.BUSY)
                .workflowState(SpeakerWorkflowState.CONFIRMED)
                .expertiseAreas(List.of("AI/ML", "Data"))
                .speakingTopics(List.of("Machine Learning", "Deep Learning"))
                .linkedInUrl("https://linkedin.com/in/johndoe")
                .twitterHandle("@johndoe")
                .certifications(List.of("AWS Solutions Architect", "CKA"))
                .languages(List.of("de", "en", "fr"))
                .build();

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(speakerRepository.save(any(Speaker.class))).thenReturn(mockSpeaker);
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // When
        speakerService.updateSpeaker("john.doe", updateRequest);

        // Then - verify all fields updated
        assertThat(mockSpeaker.getAvailability()).isEqualTo(SpeakerAvailability.BUSY);
        assertThat(mockSpeaker.getWorkflowState()).isEqualTo(SpeakerWorkflowState.CONFIRMED);
        assertThat(mockSpeaker.getExpertiseAreas()).containsExactly("AI/ML", "Data");
        assertThat(mockSpeaker.getSpeakingTopics()).containsExactly("Machine Learning", "Deep Learning");
        assertThat(mockSpeaker.getLinkedInUrl()).isEqualTo("https://linkedin.com/in/johndoe");
        assertThat(mockSpeaker.getTwitterHandle()).isEqualTo("@johndoe");
        assertThat(mockSpeaker.getCertifications()).containsExactly("AWS Solutions Architect", "CKA");
        assertThat(mockSpeaker.getLanguages()).containsExactly("de", "en", "fr");

        // Verify event contains changed fields
        ArgumentCaptor<SpeakerUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(SpeakerUpdatedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getChangedFields())
                .contains("availability", "workflowState", "expertiseAreas", "speakingTopics",
                         "linkedInUrl", "twitterHandle", "certifications", "languages");
    }

    // parseSpeakingHistory exception handling tests

    @Test
    void should_returnEmptySpeakingHistory_when_parseError() throws Exception {
        // Given - speaker with speaking history that fails to parse
        mockSpeaker.setSpeakingHistory("[{\"eventId\":\"evt-1\"}]");

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);

        // Mock ObjectMapper to throw exception
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("Parse error"));

        // When
        SpeakerResponse response = speakerService.getSpeakerByUsername("john.doe", "speakingHistory");

        // Then - should return empty list gracefully, not throw
        assertThat(response.getSpeakingHistory()).isEmpty();
    }

    @Test
    void should_parseValidSpeakingHistory_when_validJson() throws Exception {
        // Given - speaker with valid speaking history
        mockSpeaker.setSpeakingHistory("[{\"eventId\":\"evt-1\",\"sessionTitle\":\"Test\"}]");

        SpeakerResponse.SpeakingHistoryEntry entry = new SpeakerResponse.SpeakingHistoryEntry();
        entry.setEventId("evt-1");
        entry.setSessionTitle("Test");

        when(speakerRepository.findByUsernameAndDeletedAtIsNull("john.doe"))
                .thenReturn(Optional.of(mockSpeaker));
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(mockUserResponse);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(List.of(entry));

        // When
        SpeakerResponse response = speakerService.getSpeakerByUsername("john.doe", "speakingHistory");

        // Then - should return parsed list
        assertThat(response.getSpeakingHistory()).hasSize(1);
        assertThat(response.getSpeakingHistory().get(0).getEventId()).isEqualTo("evt-1");
    }
}
