package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.export.LegacyAttendeeDto;
import ch.batbern.events.dto.export.LegacyEventDto;
import ch.batbern.events.dto.export.LegacyExportEnvelope;
import ch.batbern.events.dto.export.LegacyImportResult;
import ch.batbern.events.dto.export.LegacySessionDto;
import ch.batbern.events.dto.export.LegacySpeakerDto;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LegacyImportService.
 * Story 10.20: AC7 — TDD, written before implementation (RED phase).
 */
@ExtendWith(MockitoExtension.class)
class LegacyImportServiceTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    SessionRepository sessionRepository;
    @Mock
    RegistrationRepository registrationRepository;
    @Mock
    SpeakerRepository speakerRepository;
    @Mock
    S3Client s3Client;

    @InjectMocks
    LegacyImportService service;

    private LegacyExportEnvelope envelopeWithOneEvent(String eventCode) {
        LegacyEventDto event = LegacyEventDto.builder()
                .bat(57)
                .eventCode(eventCode)
                .title("BATbern 57")
                .date(Instant.parse("2024-11-14T17:00:00Z"))
                .sessions(List.of())
                .build();
        return LegacyExportEnvelope.builder()
                .version("2.0")
                .exportedAt(Instant.now())
                .events(List.of(event))
                .companies(List.of())
                .speakers(List.of())
                .attendees(List.of())
                .build();
    }

    @Test
    @DisplayName("importAll() with 1 new event → eventRepository.save() called once; result.events == 1")
    void importAll_withNewEvent_savesEventAndReturnsCount() {
        // Arrange
        LegacyExportEnvelope envelope = envelopeWithOneEvent("BATbern57");
        when(eventRepository.findByEventCode("BATbern57")).thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getImported().getEvents()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
        verify(eventRepository, times(1)).save(any(Event.class));
    }

    @Test
    @DisplayName("importAll() with existing eventCode → event is UPDATED (upsert), not duplicated")
    void importAll_withExistingEvent_updatesInsteadOfCreating() {
        // Arrange
        UUID existingId = UUID.randomUUID();
        Event existing = Event.builder().id(existingId).eventCode("BATbern57").title("Old Title").build();
        LegacyExportEnvelope envelope = envelopeWithOneEvent("BATbern57");
        when(eventRepository.findByEventCode("BATbern57")).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert — only 1 save (update), no new entity created
        assertThat(result.getImported().getEvents()).isEqualTo(1);
        verify(eventRepository, times(1)).save(argThat(e -> e.getId().equals(existingId)));
    }

    @Test
    @DisplayName("importAll() idempotent → importing same envelope twice gives events == 1 both times")
    void importAll_idempotent_noDuplicatesOnRepeat() {
        // Arrange
        UUID existingId = UUID.randomUUID();
        Event existing = Event.builder().id(existingId).eventCode("BATbern58").title("BATbern 58").build();
        LegacyExportEnvelope envelope = envelopeWithOneEvent("BATbern58");

        // First call: not found → save creates new (we return existing to simulate it was saved)
        when(eventRepository.findByEventCode("BATbern58"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act — import twice
        LegacyImportResult result1 = service.importAll(envelope);
        LegacyImportResult result2 = service.importAll(envelope);

        // Assert — both return events == 1
        assertThat(result1.getImported().getEvents()).isEqualTo(1);
        assertThat(result2.getImported().getEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("importAll() with null events → returns error list, does not throw")
    void importAll_withNullEvents_returnsErrorNotException() {
        // Arrange — envelope with null events list
        LegacyExportEnvelope envelope = LegacyExportEnvelope.builder()
                .version("2.0")
                .exportedAt(Instant.now())
                .events(null)
                .companies(null)
                .speakers(null)
                .attendees(null)
                .build();

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert — graceful: no exception, error recorded
        assertThat(result).isNotNull();
        assertThat(result.getErrors()).isNotNull();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("importAll() with 3 attendees → 3 registrations saved with status='registered'")
    void importAll_withAttendees_savesRegistrationsWithRegisteredStatus() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        Event existingEvent = Event.builder().id(eventId).eventCode("BATbern59").build();
        when(eventRepository.findByEventCode("BATbern59")).thenReturn(Optional.of(existingEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registrationRepository.findByEventIdAndAttendeeUsername(any(), any())).thenReturn(Optional.empty());

        LegacyAttendeeDto a1 = LegacyAttendeeDto.builder().eventCode("BATbern59").username("alice").status("registered").registeredAt(Instant.now()).build();
        LegacyAttendeeDto a2 = LegacyAttendeeDto.builder().eventCode("BATbern59").username("bob").status("registered").registeredAt(Instant.now()).build();
        LegacyAttendeeDto a3 = LegacyAttendeeDto.builder().eventCode("BATbern59").username("carol").status("registered").registeredAt(Instant.now()).build();

        LegacyEventDto eventDto = LegacyEventDto.builder()
                .bat(59).eventCode("BATbern59").title("BATbern 59")
                .date(Instant.now()).sessions(List.of()).build();

        LegacyExportEnvelope envelope = LegacyExportEnvelope.builder()
                .version("2.0").exportedAt(Instant.now())
                .events(List.of(eventDto))
                .companies(List.of()).speakers(List.of())
                .attendees(List.of(a1, a2, a3))
                .build();

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert — 3 registrations saved
        assertThat(result.getImported().getAttendees()).isEqualTo(3);
        ArgumentCaptor<ch.batbern.events.domain.Registration> captor =
                ArgumentCaptor.forClass(ch.batbern.events.domain.Registration.class);
        verify(registrationRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(r -> "registered".equals(r.getStatus()));
    }

    @Test
    @DisplayName("importAll() with 2 sessions (1 new, 1 existing) → sessions count = 2")
    void importAll_withSessions_upsertsSessionsCorrectly() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder().id(eventId).eventCode("BATbern60").build();
        when(eventRepository.findByEventCode("BATbern60")).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        LegacySessionDto newSession = LegacySessionDto.builder()
                .sessionSlug("new-session-abc").title("New Session").sessionType("presentation").build();
        Session existingSession = Session.builder()
                .id(UUID.randomUUID()).sessionSlug("existing-session-def").eventCode("BATbern60")
                .eventId(eventId).title("Old Title").build();
        LegacySessionDto updatedSession = LegacySessionDto.builder()
                .sessionSlug("existing-session-def").title("Updated Title").build();

        when(sessionRepository.findBySessionSlug("new-session-abc")).thenReturn(Optional.empty());
        when(sessionRepository.findBySessionSlug("existing-session-def")).thenReturn(Optional.of(existingSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        LegacyEventDto eventDto = LegacyEventDto.builder()
                .bat(60).eventCode("BATbern60").title("BATbern 60").date(Instant.now())
                .sessions(List.of(newSession, updatedSession)).build();

        LegacyExportEnvelope envelope = LegacyExportEnvelope.builder()
                .version("2.0").exportedAt(Instant.now())
                .events(List.of(eventDto)).companies(List.of())
                .speakers(List.of()).attendees(List.of())
                .build();

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert
        assertThat(result.getImported().getSessions()).isEqualTo(2);
        verify(sessionRepository, times(2)).save(any(Session.class));
    }

    @Test
    @DisplayName("importAll() with 1 new speaker → speakerRepository.save() called; result.speakers == 1")
    void importAll_withNewSpeaker_savesSpeakerAndReturnsCount() {
        // Arrange
        LegacySpeakerDto speakerDto = LegacySpeakerDto.builder()
                .speakerId("anna.schmidt")
                .name("Anna Schmidt")
                .bio("Enterprise architect from Zurich")
                .build();

        LegacyExportEnvelope envelope = LegacyExportEnvelope.builder()
                .version("2.0").exportedAt(Instant.now())
                .events(List.of()).companies(List.of())
                .speakers(List.of(speakerDto)).attendees(List.of())
                .build();

        when(speakerRepository.findByUsername("anna.schmidt")).thenReturn(Optional.empty());
        when(speakerRepository.save(any(Speaker.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert
        assertThat(result.getImported().getSpeakers()).isEqualTo(1);
        ArgumentCaptor<Speaker> captor = ArgumentCaptor.forClass(Speaker.class);
        verify(speakerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("anna.schmidt");
        assertThat(captor.getValue().getFirstName()).isEqualTo("Anna");
        assertThat(captor.getValue().getLastName()).isEqualTo("Schmidt");
    }

    @Test
    @DisplayName("importAll() with companies → companies skipped with message in skipped list")
    void importAll_withCompanies_skipsWithMessage() {
        // Arrange
        ch.batbern.events.dto.export.LegacyCompanyDto company =
                ch.batbern.events.dto.export.LegacyCompanyDto.builder()
                        .id("SBB").displayName("SBB AG").build();

        LegacyExportEnvelope envelope = LegacyExportEnvelope.builder()
                .version("2.0").exportedAt(Instant.now())
                .events(List.of()).companies(List.of(company))
                .speakers(List.of()).attendees(List.of())
                .build();

        // Act
        LegacyImportResult result = service.importAll(envelope);

        // Assert — companies not imported, noted in skipped
        assertThat(result.getImported().getCompanies()).isEqualTo(0);
        assertThat(result.getSkipped()).anyMatch(s -> s.contains("companies"));
        assertThat(result.getSkipped()).anyMatch(s -> s.contains("company-user-management-service"));
    }
}
