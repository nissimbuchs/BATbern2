package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerPoolService - Story 6.3: Speaker Account Linking.
 *
 * Tests service logic for manual speaker pool linking.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerPoolServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private SpeakerService speakerService;

    private SpeakerPoolService speakerPoolService;

    private Event mockEvent;
    private SpeakerPool mockSpeakerPool;
    private Speaker mockSpeaker;

    @BeforeEach
    void setUp() {
        speakerPoolService = new SpeakerPoolService(
                speakerPoolRepository,
                eventRepository,
                eventPublisher,
                securityContextHelper,
                speakerService
        );

        UUID eventId = UUID.randomUUID();
        mockEvent = new Event();
        mockEvent.setId(eventId);
        mockEvent.setEventCode("BAT2025");

        mockSpeakerPool = new SpeakerPool();
        mockSpeakerPool.setId(UUID.randomUUID());
        mockSpeakerPool.setEventId(eventId);
        mockSpeakerPool.setSpeakerName("John Doe");
        mockSpeakerPool.setEmail("john@example.com");
        mockSpeakerPool.setStatus(SpeakerWorkflowState.ACCEPTED);

        mockSpeaker = Speaker.builder()
                .id(UUID.randomUUID())
                .username("john.doe")
                .workflowState(SpeakerWorkflowState.ACCEPTED)
                .availability(SpeakerAvailability.AVAILABLE)
                .build();
    }

    // linkToUser tests - Story 6.3

    @Test
    void should_linkSpeakerPoolToUser_when_validRequest() {
        // Given
        when(eventRepository.findByEventCode("BAT2025"))
                .thenReturn(Optional.of(mockEvent));
        when(speakerPoolRepository.findById(mockSpeakerPool.getId()))
                .thenReturn(Optional.of(mockSpeakerPool));
        when(speakerPoolRepository.save(any(SpeakerPool.class)))
                .thenReturn(mockSpeakerPool);
        when(speakerService.ensureSpeakerExists("john.doe"))
                .thenReturn(mockSpeaker);

        // When
        SpeakerPoolResponse response = speakerPoolService.linkToUser(
                "BAT2025", mockSpeakerPool.getId().toString(), "john.doe");

        // Then
        assertThat(mockSpeakerPool.getUsername()).isEqualTo("john.doe");
        verify(speakerPoolRepository).save(mockSpeakerPool);
        verify(speakerService).ensureSpeakerExists("john.doe");
    }

    @Test
    void should_throwEventNotFoundException_when_eventNotFound() {
        // Given
        when(eventRepository.findByEventCode("NONEXISTENT"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> speakerPoolService.linkToUser(
                "NONEXISTENT", mockSpeakerPool.getId().toString(), "john.doe"))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("NONEXISTENT");

        verify(speakerPoolRepository, never()).save(any());
        verify(speakerService, never()).ensureSpeakerExists(any());
    }

    @Test
    void should_throwException_when_speakerPoolNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(eventRepository.findByEventCode("BAT2025"))
                .thenReturn(Optional.of(mockEvent));
        when(speakerPoolRepository.findById(nonExistentId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> speakerPoolService.linkToUser(
                "BAT2025", nonExistentId.toString(), "john.doe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Speaker not found in pool");

        verify(speakerPoolRepository, never()).save(any());
        verify(speakerService, never()).ensureSpeakerExists(any());
    }

    @Test
    void should_throwException_when_speakerPoolBelongsToDifferentEvent() {
        // Given - speaker pool belongs to different event
        UUID differentEventId = UUID.randomUUID();
        mockSpeakerPool.setEventId(differentEventId);

        when(eventRepository.findByEventCode("BAT2025"))
                .thenReturn(Optional.of(mockEvent));
        when(speakerPoolRepository.findById(mockSpeakerPool.getId()))
                .thenReturn(Optional.of(mockSpeakerPool));

        // When/Then
        assertThatThrownBy(() -> speakerPoolService.linkToUser(
                "BAT2025", mockSpeakerPool.getId().toString(), "john.doe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Speaker does not belong to event");

        verify(speakerPoolRepository, never()).save(any());
        verify(speakerService, never()).ensureSpeakerExists(any());
    }

    @Test
    void should_throwException_when_alreadyLinkedToDifferentUser() {
        // Given - already linked to different user
        mockSpeakerPool.setUsername("existing.user");

        when(eventRepository.findByEventCode("BAT2025"))
                .thenReturn(Optional.of(mockEvent));
        when(speakerPoolRepository.findById(mockSpeakerPool.getId()))
                .thenReturn(Optional.of(mockSpeakerPool));

        // When/Then
        assertThatThrownBy(() -> speakerPoolService.linkToUser(
                "BAT2025", mockSpeakerPool.getId().toString(), "john.doe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already linked to user: existing.user");

        verify(speakerPoolRepository, never()).save(any());
        verify(speakerService, never()).ensureSpeakerExists(any());
    }

    @Test
    void should_beIdempotent_when_alreadyLinkedToSameUser() {
        // Given - already linked to same user
        mockSpeakerPool.setUsername("john.doe");

        when(eventRepository.findByEventCode("BAT2025"))
                .thenReturn(Optional.of(mockEvent));
        when(speakerPoolRepository.findById(mockSpeakerPool.getId()))
                .thenReturn(Optional.of(mockSpeakerPool));

        // When
        SpeakerPoolResponse response = speakerPoolService.linkToUser(
                "BAT2025", mockSpeakerPool.getId().toString(), "john.doe");

        // Then - no save, no ensureSpeakerExists called (idempotent)
        assertThat(response.getUsername()).isEqualTo("john.doe");
        verify(speakerPoolRepository, never()).save(any());
        verify(speakerService, never()).ensureSpeakerExists(any());
    }

    @Test
    void should_ensureSpeakerExists_when_linking() {
        // Given
        when(eventRepository.findByEventCode("BAT2025"))
                .thenReturn(Optional.of(mockEvent));
        when(speakerPoolRepository.findById(mockSpeakerPool.getId()))
                .thenReturn(Optional.of(mockSpeakerPool));
        when(speakerPoolRepository.save(any(SpeakerPool.class)))
                .thenReturn(mockSpeakerPool);
        when(speakerService.ensureSpeakerExists("john.doe"))
                .thenReturn(mockSpeaker);

        // When
        speakerPoolService.linkToUser("BAT2025", mockSpeakerPool.getId().toString(), "john.doe");

        // Then - verify ensureSpeakerExists is called to create Speaker entity
        verify(speakerService).ensureSpeakerExists("john.doe");
    }
}
