package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.UserProfileDTO;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistrationEmailService.
 *
 * Story 2.2a Task B12: Email confirmation service tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationEmailService Tests")
class RegistrationEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private IcsCalendarService icsCalendarService;

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
        // Set configuration values
        ReflectionTestUtils.setField(registrationEmailService, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(registrationEmailService, "organizerName", "BATbern Team");
        ReflectionTestUtils.setField(registrationEmailService, "organizerEmail", "events@batbern.ch");
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

        UserProfileDTO userProfile = UserProfileDTO.builder()
                .username("john.doe")
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .companyId("test-company")
                .build();

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architekten Treffen #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, Locale.GERMAN);

        // Wait for async operation (in test mode, email service returns immediately)
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                emailCaptor.capture(),
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

        UserProfileDTO userProfile = UserProfileDTO.builder()
                .username("jane.smith")
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .build();

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("BATbern Architects Meeting #142")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, Locale.ENGLISH);

        // Wait for async operation
        Thread.sleep(100);

        // Then
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(
                emailCaptor.capture(),
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

        UserProfileDTO userProfile = UserProfileDTO.builder()
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .build();

        Instant eventDate = Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS);
        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("Test Event")
                .date(eventDate)
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        // When
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, Locale.GERMAN);

        // Wait for async operation
        Thread.sleep(100);

        // Then
        verify(icsCalendarService, times(1)).generateIcsFile(
                eq("Test Event"),
                contains("Test Event"),
                eq("Test Address"),
                any(ZonedDateTime.class), // Start time
                any(ZonedDateTime.class), // End time (start + 4 hours)
                eq("events@batbern.ch"),
                eq("BATbern Team")
        );
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

        UserProfileDTO userProfile = UserProfileDTO.builder()
                .username("fail.user")
                .firstName("Fail")
                .lastName("User")
                .email("fail@example.com")
                .build();

        Event event = Event.builder()
                .id(registration.getEventId())
                .eventCode("BATbern142")
                .title("Test Event")
                .date(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .build();

        byte[] mockIcsFile = "mock-ics-content".getBytes();
        when(icsCalendarService.generateIcsFile(anyString(), anyString(), anyString(),
                any(ZonedDateTime.class), any(ZonedDateTime.class), anyString(), anyString()))
                .thenReturn(mockIcsFile);

        doThrow(new RuntimeException("Email sending failed"))
                .when(emailService).sendHtmlEmailWithAttachments(anyString(), anyString(), anyString(), anyList());

        // When & Then - should not throw exception
        registrationEmailService.sendRegistrationConfirmation(registration, userProfile, event, Locale.GERMAN);

        // Wait for async operation
        Thread.sleep(100);

        // Verify the attempt was made
        verify(emailService, times(1)).sendHtmlEmailWithAttachments(anyString(), anyString(), anyString(), anyList());
    }
}
