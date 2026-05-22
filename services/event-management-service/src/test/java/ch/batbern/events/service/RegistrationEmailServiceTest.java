package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.IcsCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RegistrationEmailService.
 *
 * Story 2.2a Task B12: Email confirmation service tests
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistrationEmailService Tests")
class RegistrationEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private IcsCalendarService icsCalendarService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private SessionRepository sessionRepository;

    private EventTimeResolver eventTimeResolver;

    @InjectMocks
    private RegistrationEmailService registrationEmailService;

    @Captor
    private ArgumentCaptor<String> emailCaptor;

    @Captor
    private ArgumentCaptor<String> subjectCaptor;

    @Captor
    private ArgumentCaptor<String> htmlBodyCaptor;

    @Captor
    private ArgumentCaptor<List<EmailService.EmailAttachment>> attachmentsCaptor;

    @BeforeEach
    void setUp() {
        // Create EventTimeResolver with mocked repos and inject into service
        eventTimeResolver = new EventTimeResolver(eventTypeRepository, sessionRepository);
        ReflectionTestUtils.setField(registrationEmailService, "eventTimeResolver", eventTimeResolver);

        // Set configuration values
        ReflectionTestUtils.setField(registrationEmailService, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(registrationEmailService, "organizerName", "BATbern Team");
        ReflectionTestUtils.setField(registrationEmailService, "organizerEmail", "events@batbern.ch");
        ReflectionTestUtils.setField(registrationEmailService, "calendarOrganizerEmail", "calendar@batbern.ch");

        // Default: no DB template — use classpath fallback
        when(emailTemplateService.findByKeyAndLocale(anyString(), anyString())).thenReturn(Optional.empty());

        // Default event type configurations
        when(eventTypeRepository.findByType(EventType.AFTERNOON)).thenReturn(Optional.of(
                EventTypeConfiguration.builder()
                        .type(EventType.AFTERNOON)
                        .typicalStartTime(LocalTime.of(13, 0))
                        .typicalEndTime(LocalTime.of(19, 0))
                        .minSlots(6).maxSlots(8).slotDuration(45).defaultCapacity(200)
                        .build()));
        when(eventTypeRepository.findByType(EventType.EVENING)).thenReturn(Optional.of(
                EventTypeConfiguration.builder()
                        .type(EventType.EVENING)
                        .typicalStartTime(LocalTime.of(16, 0))
                        .typicalEndTime(LocalTime.of(19, 0))
                        .minSlots(3).maxSlots(4).slotDuration(45).defaultCapacity(200)
                        .build()));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(
                EventTypeConfiguration.builder()
                        .type(EventType.FULL_DAY)
                        .typicalStartTime(LocalTime.of(9, 0))
                        .typicalEndTime(LocalTime.of(16, 0))
                        .minSlots(6).maxSlots(8).slotDuration(45).defaultCapacity(300)
                        .build()));

        // Default: no scheduled sessions
        when(sessionRepository.findByEventIdAndStartTimeIsNotNull(any(UUID.class)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("should_sendConfirmationEmail_when_registrationCreated")
    void should_sendConfirmationEmail_when_registrationCreated() throws InterruptedException {
        // Given
        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-abc123")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("john.doe")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("john.doe")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .companyId("test-company");

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architekten Treffen #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .eventType(EventType.EVENING)
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // Mock template variable replacement
        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    String template = invocation.getArgument(0);
                    // Simple replacement for testing
                    return template
                            .replace("${attendeeName}", "John Doe")
                            .replace("${eventTitle}", "BATbern Architekten Treffen #142")
                            .replace("${registrationCode}", "BATbern142-reg-abc123")
                            .replace("${venueName}", "Kornhausforum")
                            .replace("${eventDate}", "")
                            .replace("${eventTime}", "")
                            .replace("${organizerName}", "BATbern Team");
                });

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "test-cancel-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.GERMAN);

        // Wait for async operation (in test mode, email service returns immediately)
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                emailCaptor.capture(),
                anyList(), // Story 10.32: cc parameter
                subjectCaptor.capture(),
                htmlBodyCaptor.capture(),
                attachmentsCaptor.capture()
        );

        assertThat(emailCaptor.getValue()).isEqualTo("john.doe@example.com");
        assertThat(subjectCaptor.getValue()).contains("Registrierungsbestätigung");
        assertThat(subjectCaptor.getValue()).contains("BATbern Architekten Treffen #142");
        assertThat(htmlBodyCaptor.getValue()).contains("John Doe");
        assertThat(htmlBodyCaptor.getValue()).contains("BATbern142-reg-abc123");
        assertThat(htmlBodyCaptor.getValue()).contains("Kornhausforum");

        List<EmailService.EmailAttachment> attachments = attachmentsCaptor.getValue();
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).filename()).isEqualTo("event.ics");
        assertThat(attachments.get(0).mimeType()).contains("text/calendar");
        assertThat(attachments.get(0).content()).isEqualTo(mockIcsFile);
    }

    @Test
    @DisplayName("should_sendEnglishEmail_when_localeIsEnglish")
    void should_sendEnglishEmail_when_localeIsEnglish() throws InterruptedException {
        // Given
        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-xyz789")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("jane.smith")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("jane.smith")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com");

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architects Meeting #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .eventType(EventType.EVENING)
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "test-cancel-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.ENGLISH);

        // Wait for async operation
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                emailCaptor.capture(),
                anyList(), // Story 10.32: cc parameter
                subjectCaptor.capture(),
                htmlBodyCaptor.capture(),
                attachmentsCaptor.capture()
        );

        assertThat(emailCaptor.getValue()).isEqualTo("jane.smith@example.com");
        assertThat(subjectCaptor.getValue()).contains("Registration Confirmation");
        assertThat(subjectCaptor.getValue()).contains("BATbern Architects Meeting #142");
    }

    @Test
    @DisplayName("should_generateCalendarFile_when_sendingConfirmation")
    void should_generateCalendarFile_when_sendingConfirmation() throws InterruptedException {
        // Given
        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-test123")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("test.user")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("test.user")
                .firstName("Test")
                .lastName("User")
                .email("test@example.com");

        Instant eventDate = Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS);
        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("Test Event")
                .date(eventDate)
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .eventType(EventType.EVENING)
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "test-cancel-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.GERMAN);

        // Wait for async operation
        Thread.sleep(100);

        // Then — ORGANIZER address is the non-forwarded calendar mailbox, not the public events@ alias.
        // Reason: iMIP REPLYs from mail clients on Accept/Decline must not fan out to organizers.
        verify(icsCalendarService, times(1)).generateIcsFile(
                eq("Test Event"),
                contains("Test Event"),
                eq("Test Address"),
                any(ZonedDateTime.class), // Start time
                any(ZonedDateTime.class), // End time (start + 4 hours)
                eq("calendar@batbern.ch"),
                eq("BATbern Team")
        );
    }

    @Test
    @DisplayName("should_useTypicalStartTime_when_afternoonEvent")
    void should_useTypicalStartTime_when_afternoonEvent() throws InterruptedException {
        // Given — event date stored as midnight UTC (no time component)
        // This is the exact bug scenario: date-only input → midnight UTC
        Instant midnightUtc = Instant.parse("2026-06-19T00:00:00Z");

        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-time123")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("david.b")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("david.b")
                .firstName("David")
                .lastName("Baumgartner")
                .email("david@example.com")
                .companyId("test-company");

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("Erste Erfahrungen mit KI-Agenten im Business")
                .date(midnightUtc)
                .venueName("Zentrum Paul Klee")
                .venueAddress("Monument im Fruchtland 3, 3006 Bern")
                .eventType(EventType.AFTERNOON)
                .build();

        // Capture the ZonedDateTime passed to ICS generation
        ArgumentCaptor<ZonedDateTime> startCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> endCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        byte[] mockIcsFile = "mock-ics".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                startCaptor.capture(), endCaptor.capture(), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event, "tok", "ctok", "http://localhost/dereg", Locale.GERMAN);
        Thread.sleep(100);

        // Then — start time should be 13:00 Swiss, NOT 02:00
        ZonedDateTime capturedStart = startCaptor.getValue();
        assertThat(capturedStart.getHour()).isEqualTo(13);
        assertThat(capturedStart.getMinute()).isEqualTo(0);
        assertThat(capturedStart.getZone()).isEqualTo(ZoneId.of("Europe/Zurich"));

        // End time should be 19:00 Swiss (from config), NOT start + 4h
        ZonedDateTime capturedEnd = endCaptor.getValue();
        assertThat(capturedEnd.getHour()).isEqualTo(19);
        assertThat(capturedEnd.getMinute()).isEqualTo(0);
    }

    @Test
    @DisplayName("should_useSessionTimes_when_agendaPublished")
    void should_useSessionTimes_when_agendaPublished() throws InterruptedException {
        // Given — event with AGENDA_PUBLISHED state and scheduled sessions
        Instant midnightUtc = Instant.parse("2026-06-19T00:00:00Z");
        UUID eventId = UUID.randomUUID();

        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-sess123")
                .eventId(eventId)
                .eventCode("BATbern142")
                .attendeeUsername("session.user")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("session.user")
                .firstName("Session")
                .lastName("User")
                .email("session@example.com")
                .companyId("test-company");

        Event event = Event.builder()
                .id(eventId)
                .eventCode("BATbern142")
                .title("Session Timing Test")
                .date(midnightUtc)
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .eventType(EventType.AFTERNOON)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build();

        // Sessions: 13:30–14:15 and 14:30–15:15 Swiss time (CEST = UTC+2)
        Session session1 = Session.builder()
                .startTime(Instant.parse("2026-06-19T11:30:00Z"))  // 13:30 CEST
                .endTime(Instant.parse("2026-06-19T12:15:00Z"))    // 14:15 CEST
                .build();
        Session session2 = Session.builder()
                .startTime(Instant.parse("2026-06-19T12:30:00Z"))  // 14:30 CEST
                .endTime(Instant.parse("2026-06-19T13:15:00Z"))    // 15:15 CEST
                .build();
        when(sessionRepository.findByEventIdAndStartTimeIsNotNull(eventId))
                .thenReturn(List.of(session1, session2));

        ArgumentCaptor<ZonedDateTime> startCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> endCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        byte[] mockIcsFile = "mock-ics".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                startCaptor.capture(), endCaptor.capture(), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event, "tok", "ctok", "http://localhost/dereg", Locale.GERMAN);
        Thread.sleep(100);

        // Then — should use earliest session start (13:30) and latest session end (15:15)
        ZonedDateTime capturedStart = startCaptor.getValue();
        assertThat(capturedStart.getHour()).isEqualTo(13);
        assertThat(capturedStart.getMinute()).isEqualTo(30);

        ZonedDateTime capturedEnd = endCaptor.getValue();
        assertThat(capturedEnd.getHour()).isEqualTo(15);
        assertThat(capturedEnd.getMinute()).isEqualTo(15);
    }

    @Test
    @DisplayName("should_useEventTypeTimes_when_noAgendaPublished")
    void should_useEventTypeTimes_when_noAgendaPublished() throws InterruptedException {
        // Given — event in SLOT_ASSIGNMENT state (sessions exist but agenda not published)
        Instant midnightUtc = Instant.parse("2026-06-19T00:00:00Z");
        UUID eventId = UUID.randomUUID();

        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-pre123")
                .eventId(eventId)
                .eventCode("BATbern142")
                .attendeeUsername("pre.user")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("pre.user")
                .firstName("Pre")
                .lastName("User")
                .email("pre@example.com")
                .companyId("test-company");

        Event event = Event.builder()
                .id(eventId)
                .eventCode("BATbern142")
                .title("Pre-Agenda Test")
                .date(midnightUtc)
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .eventType(EventType.AFTERNOON)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .build();

        ArgumentCaptor<ZonedDateTime> startCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> endCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        byte[] mockIcsFile = "mock-ics".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                startCaptor.capture(), endCaptor.capture(), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event, "tok", "ctok", "http://localhost/dereg", Locale.GERMAN);
        Thread.sleep(100);

        // Then — should use event type times (AFTERNOON: 13:00–19:00), NOT session times
        ZonedDateTime capturedStart = startCaptor.getValue();
        assertThat(capturedStart.getHour()).isEqualTo(13);
        assertThat(capturedStart.getMinute()).isEqualTo(0);

        ZonedDateTime capturedEnd = endCaptor.getValue();
        assertThat(capturedEnd.getHour()).isEqualTo(19);
        assertThat(capturedEnd.getMinute()).isEqualTo(0);

        // Verify session repository was NOT called (wrong workflow state)
        verify(sessionRepository, times(0)).findByEventIdAndStartTimeIsNotNull(any());
    }

    @Test
    @DisplayName("should_notThrowException_when_emailSendingFails")
    void should_notThrowException_when_emailSendingFails() throws InterruptedException {
        // Given
        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-fail123")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("fail.user")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("fail.user")
                .firstName("Fail")
                .lastName("User")
                .email("fail@example.com");

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("Test Event")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .eventType(EventType.EVENING)
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        doThrow(new RuntimeException("Email sending failed"))
                .when(emailService).sendHtmlEmailWithAttachments(anyString(), anyList(), anyString(), any(), anyList());

        // When & Then - should not throw exception
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "test-cancel-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.GERMAN);

        // Wait for async operation
        Thread.sleep(100);

        // Verify the attempt was made
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(anyString(), anyList(), anyString(), any(), anyList());
    }

    // ===========================================================
    // Story 10.32 — Additional emails CC'd on registration confirm
    // ===========================================================

    @Test
    @DisplayName("should_ccAdditionalEmails_when_userHasAdditionalEmails")
    void should_ccAdditionalEmails_when_userHasAdditionalEmails() throws InterruptedException {
        // Given
        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-cc1")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("nissim.buchs")
                .build();

        ch.batbern.events.dto.generated.users.AdditionalEmail hostpoint =
                new ch.batbern.events.dto.generated.users.AdditionalEmail()
                        .email("info@berner-architekten-treffen.ch")
                        .label("Hostpoint shared");

        UserResponse userProfile = new UserResponse()
                .id("nissim.buchs")
                .firstName("Nissim")
                .lastName("Buchs")
                .email("nissim.buchs@elca.ch")
                .additionalEmails(List.of(hostpoint));

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architekten Treffen #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .eventType(EventType.EVENING)
                .build();

        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn("ics".getBytes());

        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        org.mockito.ArgumentCaptor<List<String>> ccCaptor = org.mockito.ArgumentCaptor.forClass(List.class);

        // When
        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event,
                "tok", "cancel", "http://x/deregister?token=t", Locale.GERMAN);
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                eq("nissim.buchs@elca.ch"),
                ccCaptor.capture(),
                anyString(),
                anyString(),
                anyList()
        );
        assertThat(ccCaptor.getValue()).containsExactly("info@berner-architekten-treffen.ch");
    }

    @Test
    @DisplayName("should_passEmptyCc_when_userHasNoAdditionalEmails")
    void should_passEmptyCc_when_userHasNoAdditionalEmails() throws InterruptedException {
        // Given
        Registration registration = Registration.builder()
                .registrationCode("BATbern142-reg-cc2")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername("anon.attendee")
                .build();

        UserResponse userProfile = new UserResponse()
                .id("anon.attendee")
                .firstName("Anon")
                .lastName("Attendee")
                .email("anon@example.com");
                // additionalEmails: not set → null → treated as empty.

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architekten Treffen #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .eventType(EventType.EVENING)
                .build();

        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn("ics".getBytes());

        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        org.mockito.ArgumentCaptor<List<String>> ccCaptor = org.mockito.ArgumentCaptor.forClass(List.class);

        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event,
                "tok", "cancel", "http://x/deregister?token=t", Locale.GERMAN);
        Thread.sleep(100);

        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                eq("anon@example.com"),
                ccCaptor.capture(),
                anyString(),
                anyString(),
                anyList()
        );
        assertThat(ccCaptor.getValue()).isEmpty();
    }
}
