package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerAcceptanceEmailService.
 * Story 6.2a AC9: Acceptance Confirmation Email
 *
 * Tests:
 * - Test 2.10: should_sendConfirmationEmail_when_speakerAccepts
 * - Test 2.11: should_includeProfileUrl_in_confirmationEmail
 * - Test 2.12: should_includeContentUrl_in_confirmationEmail
 * - Test 2.13: should_useCorrectLanguage_in_confirmationEmail
 * - Test 2.14: should_notSendEmail_when_speakerDeclines (not applicable - service only called on accept)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpeakerAcceptanceEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private EmailTemplateService emailTemplateService;

    private SpeakerAcceptanceEmailService acceptanceEmailService;

    private SpeakerPool speaker;
    private Event event;
    private Session session;
    private String viewToken;

    @BeforeEach
    void setUp() {
        acceptanceEmailService = new SpeakerAcceptanceEmailService(emailService, sessionRepository, emailTemplateService);
        ReflectionTestUtils.setField(acceptanceEmailService, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(acceptanceEmailService, "organizerName", "BATbern Team");
        ReflectionTestUtils.setField(acceptanceEmailService, "organizerEmail", "events@batbern.ch");

        // Set up test data
        UUID speakerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        event = new Event();
        event.setId(eventId);
        event.setEventCode("BAT2026");
        event.setTitle("BATbern 2026");
        // Create Instant for March 15, 2026 at 18:00 Swiss time
        ZonedDateTime eventDateTime = ZonedDateTime.of(2026, 3, 15, 18, 0, 0, 0, ZoneId.of("Europe/Zurich"));
        event.setDate(eventDateTime.toInstant());
        event.setVenueName("Kursaal Bern");
        event.setVenueAddress("Kornhausstrasse 3, 3013 Bern");

        session = new Session();
        session.setId(sessionId);
        session.setTitle("Cloud Architecture Patterns");
        session.setDescription("Best practices for cloud architecture");

        speaker = new SpeakerPool();
        speaker.setId(speakerId);
        speaker.setSpeakerName("John Doe");
        speaker.setEmail("john.doe@example.com");
        speaker.setEventId(eventId);
        speaker.setSessionId(sessionId);
        speaker.setContentDeadline(LocalDate.of(2026, 2, 15));

        viewToken = "test-view-token-12345";

        // Default: no DB template — use classpath fallback
        when(emailTemplateService.findByKeyAndLocale(anyString(), anyString())).thenReturn(Optional.empty());

        // Mock email service to return template as-is for variable replacement
        when(emailService.replaceVariables(anyString(), any())).thenAnswer(invocation -> {
            String template = invocation.getArgument(0);
            java.util.Map<String, String> vars = invocation.getArgument(1);
            String result = template;
            for (var entry : vars.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        });
    }

    @Nested
    @DisplayName("AC9: Acceptance Confirmation Email")
    class AcceptanceConfirmationEmailTests {

        @Test
        @DisplayName("Test 2.10: should send confirmation email when speaker accepts")
        void should_sendConfirmationEmail_when_speakerAccepts() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(
                    eq("john.doe@example.com"),
                    contains("Speaker Confirmation"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Test 2.11: should include profile URL in confirmation email")
        void should_includeProfileUrl_in_confirmationEmail() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("/speaker-portal/profile?token=" + viewToken);
        }

        @Test
        @DisplayName("Test 2.12: should include content URL in confirmation email")
        void should_includeContentUrl_in_confirmationEmail() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("/speaker-portal/content?token=" + viewToken);
        }

        @Test
        @DisplayName("should include dashboard link in confirmation email")
        void should_includeDashboardLink_in_confirmationEmail() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("/speaker-portal/dashboard?token=" + viewToken);
        }

        @Test
        @DisplayName("Test 2.13: should use correct language (German) in confirmation email")
        void should_useCorrectLanguage_when_germanLocale() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.GERMAN);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            String subject = subjectCaptor.getValue();
            assertThat(subject).contains("Bestätigung");
        }

        @Test
        @DisplayName("Test 2.13b: should use correct language (English) in confirmation email")
        void should_useCorrectLanguage_when_englishLocale() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            String subject = subjectCaptor.getValue();
            assertThat(subject).contains("Confirmation");
        }

        @Test
        @DisplayName("should include event details in confirmation email")
        void should_includeEventDetails_in_confirmationEmail() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("BATbern 2026");
            assertThat(emailBody).contains("15.03.2026");
        }

        @Test
        @DisplayName("should include session title when assigned")
        void should_includeSessionTitle_when_assigned() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("Cloud Architecture Patterns");
        }

        @Test
        @DisplayName("should include content deadline when set")
        void should_includeContentDeadline_when_set() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("15.02.2026");
        }

        @Test
        @DisplayName("should not throw when email sending fails")
        void should_notThrow_when_emailSendingFails() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            doThrow(new RuntimeException("Email server unavailable"))
                    .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

            // When/Then - should not throw
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);
        }

        @Test
        @DisplayName("should use DB template when available")
        void should_useDbTemplate_whenAvailable() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            EmailTemplate dbTemplate = new EmailTemplate();
            dbTemplate.setHtmlBody("<p>DB template for {{speakerName}}</p>");
            when(emailTemplateService.findByKeyAndLocale("speaker-acceptance", "en"))
                    .thenReturn(Optional.of(dbTemplate));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("DB template for John Doe");
        }

        @Test
        @DisplayName("should merge with layout when layoutKey is set")
        void should_mergeLayout_whenLayoutKeySet() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            EmailTemplate dbTemplate = new EmailTemplate();
            dbTemplate.setHtmlBody("<p>Content block</p>");
            dbTemplate.setLayoutKey("batbern-default");
            when(emailTemplateService.findByKeyAndLocale("speaker-acceptance", "en"))
                    .thenReturn(Optional.of(dbTemplate));
            when(emailTemplateService.mergeWithLayout("<p>Content block</p>", "batbern-default", "en"))
                    .thenReturn("<html><body><p>Content block</p></body></html>");

            // When
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.ENGLISH);

            // Then
            verify(emailTemplateService).mergeWithLayout("<p>Content block</p>", "batbern-default", "en");
        }
    }
}
