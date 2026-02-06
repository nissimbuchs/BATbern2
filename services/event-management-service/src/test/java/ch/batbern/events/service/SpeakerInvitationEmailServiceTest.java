package ch.batbern.events.service;

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
 * Unit tests for SpeakerInvitationEmailService.
 * Story 6.1b: Speaker Invitation System (AC3, AC4)
 *
 * Tests:
 * - Dashboard link included in email body
 * - Accept/decline magic links included
 * - Event details populated correctly
 * - Session info included when assigned
 * - i18n support (German/English)
 * - Deadline display
 * - Error handling (email failure doesn't throw)
 */
@ExtendWith(MockitoExtension.class)
class SpeakerInvitationEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SessionRepository sessionRepository;

    private SpeakerInvitationEmailService invitationEmailService;

    private SpeakerPool speaker;
    private Event event;
    private Session session;
    private String respondToken;
    private String dashboardToken;

    @BeforeEach
    void setUp() {
        invitationEmailService = new SpeakerInvitationEmailService(emailService, sessionRepository);
        ReflectionTestUtils.setField(invitationEmailService, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(invitationEmailService, "organizerName", "BATbern Team");
        ReflectionTestUtils.setField(invitationEmailService, "organizerEmail", "events@batbern.ch");

        UUID speakerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        event = new Event();
        event.setId(eventId);
        event.setEventCode("BAT2026");
        event.setTitle("BATbern 2026");
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
        speaker.setResponseDeadline(LocalDate.of(2026, 2, 28));
        speaker.setContentDeadline(LocalDate.of(2026, 3, 10));

        respondToken = "test-respond-token-abc";
        dashboardToken = "test-dashboard-token-xyz";

        // Mock email service to perform actual variable replacement
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
    @DisplayName("AC3: Invitation Email Content")
    class InvitationEmailContentTests {

        @Test
        @DisplayName("should send invitation email to speaker's email address")
        void should_sendEmail_when_invitationTriggered() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(
                    eq("john.doe@example.com"),
                    contains("Speaker Invitation"),
                    anyString()
            );
        }

        @Test
        @DisplayName("should include dashboard link in invitation email")
        void should_includeDashboardLink_when_emailSent() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains(
                    "https://batbern.ch/speaker-portal/dashboard?token=" + dashboardToken);
        }

        @Test
        @DisplayName("should include accept link with respond token")
        void should_includeAcceptLink_when_emailSent() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains(
                    "/speaker-portal/respond?token=" + respondToken + "&action=accept");
        }

        @Test
        @DisplayName("should include decline link with respond token")
        void should_includeDeclineLink_when_emailSent() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains(
                    "/speaker-portal/respond?token=" + respondToken + "&action=decline");
        }

        @Test
        @DisplayName("should use separate tokens for dashboard and respond links")
        void should_useSeparateTokens_when_differentActionsRequired() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            // Dashboard uses the VIEW token
            assertThat(emailBody).contains("dashboard?token=" + dashboardToken);
            // Accept/decline use the RESPOND token
            assertThat(emailBody).contains("respond?token=" + respondToken);
            // Tokens are different
            assertThat(respondToken).isNotEqualTo(dashboardToken);
        }

        @Test
        @DisplayName("should include event details in email")
        void should_includeEventDetails_when_emailSent() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("BATbern 2026");
            assertThat(emailBody).contains("15.03.2026");
            assertThat(emailBody).contains("Kursaal Bern");
            assertThat(emailBody).contains("Kornhausstrasse 3, 3013 Bern");
        }

        @Test
        @DisplayName("should include session title when assigned")
        void should_includeSessionTitle_when_speakerHasSession() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("Cloud Architecture Patterns");
        }

        @Test
        @DisplayName("should include response deadline when set")
        void should_includeResponseDeadline_when_set() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("28.02.2026");
        }

        @Test
        @DisplayName("should include content deadline when set")
        void should_includeContentDeadline_when_set() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("10.03.2026");
        }

        @Test
        @DisplayName("should include speaker name in email body")
        void should_includeSpeakerName_when_emailSent() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("John Doe");
        }
    }

    @Nested
    @DisplayName("AC4: i18n Support")
    class InternationalizationTests {

        @Test
        @DisplayName("should use English subject when English locale")
        void should_useEnglishSubject_when_englishLocale() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Speaker Invitation");
        }

        @Test
        @DisplayName("should use German subject when German locale")
        void should_useGermanSubject_when_germanLocale() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.GERMAN);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Einladung als Referent");
        }

        @Test
        @DisplayName("should include dashboard link in German template")
        void should_includeDashboardLink_when_germanLocale() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.GERMAN);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains(
                    "https://batbern.ch/speaker-portal/dashboard?token=" + dashboardToken);
            assertThat(emailBody).contains("Referenten-Dashboard");
        }

        @Test
        @DisplayName("should default to German when locale is null")
        void should_defaultToGerman_when_localeIsNull() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, null);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Einladung als Referent");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("should not throw when email sending fails")
        void should_notThrow_when_emailSendingFails() {
            // Given
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            doThrow(new RuntimeException("Email server unavailable"))
                    .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

            // When/Then - should not throw
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);
        }

        @Test
        @DisplayName("should handle speaker without session assignment")
        void should_handleMissingSession_when_noSessionAssigned() {
            // Given - speaker has no session
            speaker.setSessionId(null);
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then - email should still be sent without session info
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("John Doe");
            assertThat(emailBody).contains("BATbern 2026");
            assertThat(emailBody).doesNotContain("Cloud Architecture Patterns");
        }

        @Test
        @DisplayName("should use TBA when venue is not set")
        void should_useTba_when_venueNotSet() {
            // Given
            event.setVenueName(null);
            event.setVenueAddress(null);
            speaker.setSessionId(null);
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("TBA");
        }

        @Test
        @DisplayName("should handle speaker without deadlines")
        void should_handleMissingDeadlines_when_notSet() {
            // Given
            speaker.setResponseDeadline(null);
            speaker.setContentDeadline(null);
            speaker.setSessionId(null);
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            // When
            invitationEmailService.sendInvitationEmail(
                    speaker, event, respondToken, dashboardToken, Locale.ENGLISH);

            // Then - email should still be sent
            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String emailBody = bodyCaptor.getValue();
            assertThat(emailBody).contains("John Doe");
            // Dashboard link should always be present regardless of deadlines
            assertThat(emailBody).contains("dashboard?token=" + dashboardToken);
        }
    }
}
