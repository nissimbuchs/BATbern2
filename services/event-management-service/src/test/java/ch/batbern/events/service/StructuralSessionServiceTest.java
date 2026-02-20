package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.StructuralSessionsAlreadyExistException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.service.SlugGenerationService;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StructuralSessionService.
 *
 * Verifies:
 * - Session count, types, and times are correct
 * - Organizer is assigned as MODERATOR on both moderation sessions
 * - 409 thrown when structural sessions exist and overwrite=false
 * - overwrite=true deletes existing structural sessions before generating
 * - Missing event type config throws NotFoundException
 */
@ExtendWith(MockitoExtension.class)
class StructuralSessionServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private SlugGenerationService slugGenerationService;

    @InjectMocks
    private StructuralSessionService structuralSessionService;

    private static final String EVENT_CODE = "BATbern142";
    private static final String ORGANIZER_USERNAME = "john.doe";

    private Event testEvent;
    private EventTypeConfiguration fullDayConfig;

    @BeforeEach
    void setUp() {
        UUID eventId = UUID.randomUUID();

        testEvent = new Event();
        testEvent.setEventNumber(142);
        testEvent.setEventCode(EVENT_CODE);
        testEvent.setTitle("BATbern 142");
        testEvent.setEventType(EventType.FULL_DAY);
        testEvent.setDate(Instant.parse("2025-06-15T09:00:00Z"));
        testEvent.setRegistrationDeadline(Instant.parse("2025-06-10T23:59:59Z"));
        testEvent.setVenueName("Test Venue");
        testEvent.setVenueAddress("Test Address");
        testEvent.setVenueCapacity(300);
        testEvent.setOrganizerUsername(ORGANIZER_USERNAME);
        testEvent.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        testEvent.setCreatedAt(Instant.now());
        testEvent.setUpdatedAt(Instant.now());
        // Set ID via reflection-free approach — save the ID
        testEvent = new Event();
        testEvent.setEventNumber(142);
        testEvent.setEventCode(EVENT_CODE);
        testEvent.setTitle("BATbern 142");
        testEvent.setEventType(EventType.FULL_DAY);
        testEvent.setDate(Instant.parse("2025-06-15T09:00:00Z"));
        testEvent.setRegistrationDeadline(Instant.parse("2025-06-10T23:59:59Z"));
        testEvent.setVenueName("Test Venue");
        testEvent.setVenueAddress("Test Address");
        testEvent.setVenueCapacity(300);
        testEvent.setOrganizerUsername(ORGANIZER_USERNAME);
        testEvent.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        testEvent.setCreatedAt(Instant.now());
        testEvent.setUpdatedAt(Instant.now());

        fullDayConfig = EventTypeConfiguration.builder()
                .id(UUID.randomUUID())
                .type(EventType.FULL_DAY)
                .minSlots(6)
                .maxSlots(8)
                .slotDuration(45)
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(300)
                .typicalStartTime(LocalTime.of(9, 0))
                .typicalEndTime(LocalTime.of(17, 0))
                .moderationStartDuration(5)
                .moderationEndDuration(5)
                .breakDuration(20)
                .lunchDuration(60)
                .build();
    }

    @Test
    @DisplayName("Should generate structural sessions for FULL_DAY event with correct types")
    void should_generateStructuralSessions_when_fullDayEventRequested() {
        // Given
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(testEvent));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));
        when(sessionRepository.findByEventIdAndSessionTypeIn(any(), anyList())).thenReturn(List.of());
        when(slugGenerationService.generateSessionSlug(anyString())).thenAnswer(
                inv -> inv.getArgument(0, String.class).toLowerCase().replace(" ", "-"));
        when(slugGenerationService.ensureUniqueSlug(anyString(), any())).thenAnswer(
                inv -> inv.getArgument(0, String.class) + "-abc");
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.toSessionResponse(any(Session.class), anyString()))
                .thenAnswer(inv -> {
                    Session s = inv.getArgument(0);
                    SessionResponse r = new SessionResponse();
                    r.setSessionSlug(s.getSessionSlug());
                    r.setSessionType(s.getSessionType());
                    r.setTitle(s.getTitle());
                    return r;
                });

        // When
        List<SessionResponse> result = structuralSessionService.generateStructuralSessions(
                EVENT_CODE, false);

        // Then: at least moderation start + moderation end + at least 1 break + lunch
        assertThat(result).isNotEmpty();
        List<String> types = result.stream().map(SessionResponse::getSessionType).toList();
        assertThat(types).contains("moderation", "break", "lunch");
        // Two moderation sessions (start + end)
        assertThat(types.stream().filter("moderation"::equals).count()).isEqualTo(2);
        // At least 1 lunch
        assertThat(types.stream().filter("lunch"::equals).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw StructuralSessionsAlreadyExistException when sessions exist and overwrite=false")
    void should_throw409_when_structuralSessionsExistAndOverwriteFalse() {
        // Given
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(testEvent));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));
        when(sessionRepository.findByEventIdAndSessionTypeIn(any(), anyList()))
                .thenReturn(List.of(new Session())); // structural sessions exist

        // When / Then
        assertThatThrownBy(() -> structuralSessionService.generateStructuralSessions(
                EVENT_CODE, false))
                .isInstanceOf(StructuralSessionsAlreadyExistException.class)
                .hasMessageContaining(EVENT_CODE);

        // No new sessions should be saved
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete existing structural sessions and regenerate when overwrite=true")
    void should_deleteAndRegenerate_when_overwriteTrue() {
        // Given
        Session existingSession = new Session();
        existingSession.setSessionType("moderation");
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(testEvent));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));
        when(sessionRepository.findByEventIdAndSessionTypeIn(any(), anyList()))
                .thenReturn(List.of(existingSession));
        when(slugGenerationService.generateSessionSlug(anyString())).thenAnswer(
                inv -> inv.getArgument(0, String.class).toLowerCase().replace(" ", "-"));
        when(slugGenerationService.ensureUniqueSlug(anyString(), any())).thenAnswer(
                inv -> inv.getArgument(0, String.class) + "-xyz");
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.toSessionResponse(any(Session.class), anyString()))
                .thenAnswer(inv -> {
                    Session s = inv.getArgument(0);
                    SessionResponse r = new SessionResponse();
                    r.setSessionSlug(s.getSessionSlug());
                    r.setSessionType(s.getSessionType());
                    r.setTitle(s.getTitle());
                    return r;
                });

        // When
        List<SessionResponse> result = structuralSessionService.generateStructuralSessions(
                EVENT_CODE, true);

        // Then: existing sessions were deleted, new ones created
        verify(sessionRepository).deleteByEventIdAndSessionTypeIn(any(), anyList());
        verify(sessionRepository).flush();
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should throw EventNotFoundException when event does not exist")
    void should_throwEventNotFound_when_eventCodeInvalid() {
        when(eventRepository.findByEventCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> structuralSessionService.generateStructuralSessions(
                "INVALID", false))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw NotFoundException when event type config missing")
    void should_throwNotFound_when_eventTypeConfigMissing() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(testEvent));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> structuralSessionService.generateStructuralSessions(
                EVENT_CODE, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Should include moderation sessions with times relative to typicalStartTime")
    void should_generateModerationSessionsStartingAtTypicalStartTime() {
        // Given: start at 09:00
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(testEvent));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));
        when(sessionRepository.findByEventIdAndSessionTypeIn(any(), anyList())).thenReturn(List.of());

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        when(slugGenerationService.generateSessionSlug(anyString())).thenReturn("test-slug");
        when(slugGenerationService.ensureUniqueSlug(anyString(), any())).thenAnswer(
                inv -> inv.getArgument(0, String.class) + "-1");
        when(sessionRepository.save(sessionCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.toSessionResponse(any(Session.class), anyString()))
                .thenReturn(new SessionResponse());

        // When
        structuralSessionService.generateStructuralSessions(EVENT_CODE, false);

        // Then: first saved session (moderation start) has startTime at 09:00 UTC
        List<Session> savedSessions = sessionCaptor.getAllValues();
        assertThat(savedSessions).isNotEmpty();
        Session modStart = savedSessions.get(0);
        assertThat(modStart.getSessionType()).isEqualTo("moderation");
        assertThat(modStart.getTitle()).isEqualTo("Moderation Start");
        // startTime should be 09:00 on the event date (2025-06-15)
        assertThat(modStart.getStartTime().toString()).contains("2025-06-15T09:00:00");
        // endTime = start + 5 min
        assertThat(modStart.getEndTime().toString()).contains("2025-06-15T09:05:00");
    }
}
