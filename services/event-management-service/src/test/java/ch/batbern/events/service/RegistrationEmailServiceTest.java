package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.repository.EventAgendaConfigRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @Mock
    private EventAgendaConfigRepository eventAgendaConfigRepository;

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
        // Real resolver over mocked repositories: no per-event override (Mockito returns
        // Optional.empty), so times come from the eventTypeRepository stubs below.
        eventTimeResolver = new EventTimeResolver(
                new AgendaConfigResolver(eventAgendaConfigRepository, eventTypeRepository), sessionRepository);
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
    @DisplayName("should_sendPendingEmailWithoutCalendar_when_registrationCreated")
    void should_sendPendingEmailWithoutCalendar_when_registrationCreated() throws InterruptedException {
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
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
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
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.GERMAN);

        // Wait for async operation (in test mode, email service returns immediately)
        Thread.sleep(100);

        // Then — the registration is NOT valid yet, so the mail must not look like a booking:
        // no calendar invite (it rendered an "Accept" button that people took for the
        // confirmation) and a subject that asks for the confirmation.
        verify(emailService, times(1)).sendHtmlEmail(
                emailCaptor.capture(),
                anyList(), // Story 10.32: cc parameter
                subjectCaptor.capture(),
                htmlBodyCaptor.capture()
        );
        verify(emailService, never()).sendHtmlEmailWithAttachments(
                anyString(), anyList(), anyString(), any(), anyList());
        verify(icsCalendarService, never()).generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString());

        assertThat(emailCaptor.getValue()).isEqualTo("john.doe@example.com");
        assertThat(subjectCaptor.getValue()).startsWith("Bitte bestätigen");
        assertThat(subjectCaptor.getValue()).doesNotContain("Registrierungsbestätigung");
        // Inbound reply routing (Story 10.17) extracts BATbern\\d+ from the subject
        assertThat(subjectCaptor.getValue()).contains("BATbern142");
        assertThat(subjectCaptor.getValue()).contains("BATbern Architekten Treffen #142");
        assertThat(htmlBodyCaptor.getValue()).contains("{{confirmationUrl}}");
        assertThat(htmlBodyCaptor.getValue()).doesNotContain(".ics");
        assertThat(htmlBodyCaptor.getValue()).contains("erst gültig, wenn Sie sie bestätigen");
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
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.ENGLISH);

        // Wait for async operation
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmail(
                emailCaptor.capture(),
                anyList(), // Story 10.32: cc parameter
                subjectCaptor.capture(),
                htmlBodyCaptor.capture()
        );

        assertThat(emailCaptor.getValue()).isEqualTo("jane.smith@example.com");
        assertThat(subjectCaptor.getValue()).startsWith("Please confirm your registration");
        assertThat(subjectCaptor.getValue()).contains("BATbern142");
        assertThat(htmlBodyCaptor.getValue()).doesNotContain(".ics");
        assertThat(subjectCaptor.getValue()).contains("BATbern Architects Meeting #142");
    }

    @Test
    @DisplayName("should_generateCalendarFile_when_sendingConfirmedEmail")
    void should_generateCalendarFile_when_sendingConfirmedEmail() throws InterruptedException {
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
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // When
        registrationEmailService.sendRegistrationConfirmed(registration, userProfile, event, Locale.GERMAN);

        // Wait for async operation
        Thread.sleep(100);

        // Then — ORGANIZER address is the non-forwarded calendar mailbox, not the public events@ alias.
        // Reason: iMIP REPLYs from mail clients on Accept/Decline must not fan out to organizers.
        ArgumentCaptor<IcsCalendarService.IcsEventData> icsEvent =
                ArgumentCaptor.forClass(IcsCalendarService.IcsEventData.class);
        verify(icsCalendarService, times(1)).generateIcsFile(
                icsEvent.capture(),
                eq("calendar@batbern.ch"),
                eq("BATbern Team")
        );
        assertThat(icsEvent.getValue().title()).isEqualTo("Test Event");
        assertThat(icsEvent.getValue().description()).contains("Test Event");
        assertThat(icsEvent.getValue().location()).isEqualTo("Test Address");
        // Story 16.0 AC7: stable per-registration UID, never the registration code (a credential)
        assertThat(icsEvent.getValue().uid()).isEqualTo("reg-" + registration.getCalendarUid());
        assertThat(icsEvent.getValue().uid()).doesNotContain(registration.getRegistrationCode());
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
        ArgumentCaptor<IcsCalendarService.IcsEventData> icsCaptor =
                ArgumentCaptor.forClass(IcsCalendarService.IcsEventData.class);
        byte[] mockIcsFile = "mock-ics".getBytes();
        when(icsCalendarService.generateIcsFile(icsCaptor.capture(), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        registrationEmailService.sendRegistrationConfirmed(registration, userProfile, event, Locale.GERMAN);
        Thread.sleep(100);

        // Then — start time should be 13:00 Swiss, NOT 02:00
        ZonedDateTime capturedStart = icsCaptor.getValue().start();
        assertThat(capturedStart.getHour()).isEqualTo(13);
        assertThat(capturedStart.getMinute()).isEqualTo(0);
        assertThat(capturedStart.getZone()).isEqualTo(ZoneId.of("Europe/Zurich"));

        // End time should be 19:00 Swiss (from config), NOT start + 4h
        ZonedDateTime capturedEnd = icsCaptor.getValue().end();
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

        ArgumentCaptor<IcsCalendarService.IcsEventData> icsCaptor =
                ArgumentCaptor.forClass(IcsCalendarService.IcsEventData.class);
        byte[] mockIcsFile = "mock-ics".getBytes();
        when(icsCalendarService.generateIcsFile(icsCaptor.capture(), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        registrationEmailService.sendRegistrationConfirmed(registration, userProfile, event, Locale.GERMAN);
        Thread.sleep(100);

        // Then — should use earliest session start (13:30) and latest session end (15:15)
        ZonedDateTime capturedStart = icsCaptor.getValue().start();
        assertThat(capturedStart.getHour()).isEqualTo(13);
        assertThat(capturedStart.getMinute()).isEqualTo(30);

        ZonedDateTime capturedEnd = icsCaptor.getValue().end();
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

        ArgumentCaptor<IcsCalendarService.IcsEventData> icsCaptor =
                ArgumentCaptor.forClass(IcsCalendarService.IcsEventData.class);
        byte[] mockIcsFile = "mock-ics".getBytes();
        when(icsCalendarService.generateIcsFile(icsCaptor.capture(), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        when(emailService.replaceVariables(anyString(), any(Map.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        registrationEmailService.sendRegistrationConfirmed(registration, userProfile, event, Locale.GERMAN);
        Thread.sleep(100);

        // Then — should use event type times (AFTERNOON: 13:00–19:00), NOT session times
        ZonedDateTime capturedStart = icsCaptor.getValue().start();
        assertThat(capturedStart.getHour()).isEqualTo(13);
        assertThat(capturedStart.getMinute()).isEqualTo(0);

        ZonedDateTime capturedEnd = icsCaptor.getValue().end();
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
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        doThrow(new RuntimeException("Email sending failed"))
                .when(emailService).sendHtmlEmail(anyString(), anyList(), anyString(), any());

        // When & Then - should not throw exception
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, "test-token", "http://localhost:8100/deregister?token=test-deregister-token", Locale.GERMAN);

        // Wait for async operation
        Thread.sleep(100);

        // Verify the attempt was made
        verify(emailService, times(1)).sendHtmlEmail(anyString(), anyList(), anyString(), any());
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
                        .email("info@batbern.ch")
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

        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn("ics".getBytes());

        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        org.mockito.ArgumentCaptor<List<String>> ccCaptor = org.mockito.ArgumentCaptor.forClass(List.class);

        // When
        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event,
                "tok", "http://x/deregister?token=t", Locale.GERMAN);
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmail(
                eq("nissim.buchs@elca.ch"),
                ccCaptor.capture(),
                anyString(),
                anyString()
        );
        assertThat(ccCaptor.getValue()).containsExactly("info@batbern.ch");
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

        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn("ics".getBytes());

        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        org.mockito.ArgumentCaptor<List<String>> ccCaptor = org.mockito.ArgumentCaptor.forClass(List.class);

        registrationEmailService.sendRegistrationConfirmation(
                registration, userProfile, event,
                "tok", "http://x/deregister?token=t", Locale.GERMAN);
        Thread.sleep(100);

        verify(emailService, times(1)).sendHtmlEmail(
                eq("anon@example.com"),
                ccCaptor.capture(),
                anyString(),
                anyString()
        );
        assertThat(ccCaptor.getValue()).isEmpty();
    }

    // ===========================================================
    // "Registration confirmed" mail: sent once the attendee has confirmed.
    // It is the only registration mail that carries the calendar invite.
    // ===========================================================

    private Registration confirmedRegistration(String username) {
        return Registration.builder()
                .registrationCode("BATbern142-reg-ok1")
                .eventId(UUID.randomUUID())
                .eventCode("BATbern142")
                .attendeeUsername(username)
                .status("confirmed")
                .deregistrationToken(UUID.fromString("11111111-2222-3333-4444-555555555555"))
                .build();
    }

    private Event eventFor(Registration registration) {
        return Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architekten Treffen #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .eventType(EventType.EVENING)
                .build();
    }

    @Test
    @DisplayName("should_sendConfirmedEmailWithCalendar_when_registrationConfirmed")
    void should_sendConfirmedEmailWithCalendar_when_registrationConfirmed() {
        Registration registration = confirmedRegistration("john.doe");
        Event event = eventFor(registration);
        UserResponse userProfile = new UserResponse()
                .id("john.doe").firstName("John").lastName("Doe").email("john.doe@example.com");

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);
        ArgumentCaptor<Map<String, String>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        when(emailService.replaceVariables(anyString(), varsCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        registrationEmailService.sendRegistrationConfirmed(registration, userProfile, event, Locale.GERMAN);

        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                emailCaptor.capture(),
                anyList(),
                subjectCaptor.capture(),
                htmlBodyCaptor.capture(),
                attachmentsCaptor.capture()
        );
        assertThat(emailCaptor.getValue()).isEqualTo("john.doe@example.com");
        assertThat(subjectCaptor.getValue()).startsWith("Anmeldung bestätigt");
        assertThat(subjectCaptor.getValue()).contains("BATbern142");
        assertThat(htmlBodyCaptor.getValue()).contains("{{deregistrationUrl}}");
        assertThat(htmlBodyCaptor.getValue()).doesNotContain("{{confirmationUrl}}");
        assertThat(varsCaptor.getValue()).containsEntry("deregistrationUrl",
                "https://batbern.ch/deregister?token=11111111-2222-3333-4444-555555555555");

        List<EmailService.EmailAttachment> attachments = attachmentsCaptor.getValue();
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).filename()).isEqualTo("event.ics");
        assertThat(attachments.get(0).mimeType()).contains("text/calendar");
        assertThat(attachments.get(0).content()).isEqualTo(mockIcsFile);
    }

    @Test
    @DisplayName("should_sendEnglishConfirmedEmail_when_localeIsEnglish")
    void should_sendEnglishConfirmedEmail_when_localeIsEnglish() {
        Registration registration = confirmedRegistration("jane.smith");
        UserResponse userProfile = new UserResponse()
                .id("jane.smith").firstName("Jane").lastName("Smith").email("jane.smith@example.com");
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn("ics".getBytes());
        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));

        registrationEmailService.sendRegistrationConfirmed(
                registration, userProfile, eventFor(registration), Locale.ENGLISH);

        verify(emailService).sendHtmlEmailWithAttachments(
                anyString(), anyList(), subjectCaptor.capture(), anyString(), anyList());
        assertThat(subjectCaptor.getValue()).startsWith("Registration confirmed");
        assertThat(subjectCaptor.getValue()).contains("BATbern142");
    }

    @Test
    @DisplayName("should_ccAdditionalEmails_when_sendingConfirmedEmail")
    void should_ccAdditionalEmails_when_sendingConfirmedEmail() {
        Registration registration = confirmedRegistration("multi.mail");
        UserResponse userProfile = new UserResponse()
                .id("multi.mail").firstName("Multi").lastName("Mail").email("multi@example.com")
                .additionalEmails(List.of(new ch.batbern.events.dto.generated.users.AdditionalEmail()
                        .email("second@example.com")));
        when(icsCalendarService.generateIcsFile(any(IcsCalendarService.IcsEventData.class), anyString(), anyString()))
                .thenReturn("ics".getBytes());
        when(emailService.replaceVariables(anyString(), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        ArgumentCaptor<List<String>> ccCaptor = ArgumentCaptor.forClass(List.class);

        registrationEmailService.sendRegistrationConfirmed(
                registration, userProfile, eventFor(registration), Locale.GERMAN);

        verify(emailService).sendHtmlEmailWithAttachments(
                eq("multi@example.com"), ccCaptor.capture(), anyString(), anyString(), anyList());
        assertThat(ccCaptor.getValue()).containsExactly("second@example.com");
    }

    @Test
    @DisplayName("should_notThrow_when_confirmedEmailSendingFails")
    void should_notThrow_when_confirmedEmailSendingFails() {
        Registration registration = confirmedRegistration("fail.user");
        UserResponse userProfile = new UserResponse()
                .id("fail.user").firstName("Fail").lastName("User").email("fail@example.com");
        doThrow(new RuntimeException("SES down"))
                .when(emailService).sendHtmlEmailWithAttachments(anyString(), anyList(), anyString(), any(), anyList());

        registrationEmailService.sendRegistrationConfirmed(
                registration, userProfile, eventFor(registration), Locale.GERMAN);

        verify(emailService).sendHtmlEmailWithAttachments(anyString(), anyList(), anyString(), any(), anyList());
    }

    @Test
    @DisplayName("should_keepCalendarOutOfPendingTemplates_and_inConfirmedTemplates")
    void should_keepCalendarOutOfPendingTemplates_and_inConfirmedTemplates() throws Exception {
        for (String locale : List.of("de", "en")) {
            String pending = new org.springframework.core.io.ClassPathResource(
                    "email-templates/registration-confirmation-" + locale + ".html")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(pending).doesNotContain(".ics");
            assertThat(pending).contains("{{confirmationUrl}}");
            assertThat(pending).doesNotContain("Registrierungsbestätigung")
                    .doesNotContain("Registration Confirmation");

            String confirmed = new org.springframework.core.io.ClassPathResource(
                    "email-templates/registration-confirmed-" + locale + ".html")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            // Subject comment is seeded into the DB by EmailTemplateSeedService and must keep the
            // event code for inbound reply routing (Story 10.17).
            assertThat(confirmed).startsWith("<!-- subject: ").contains("{{eventCode}}");
            assertThat(confirmed).contains(".ics").contains("{{deregistrationUrl}}");
            assertThat(confirmed).doesNotContain("{{confirmationUrl}}");
        }
    }
}
