package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
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
 * Unit tests for {@link SpeakerInvitationEmailService}.
 *
 * <p>Story 11.E.2 (AC8 item 5): Cognito-flow email rendering replaces the
 * Story 6.1b magic-link signature. The four required cases are:
 * <ol>
 *   <li>{@code should_renderTemporaryPasswordBlock_when_actionIsFreshTempPassword} (English template)</li>
 *   <li>{@code should_renderUseExistingPasswordBlock_when_actionIsUseExistingPassword} (English template)</li>
 *   <li>{@code should_renderInGermanLocale_when_localeIsDeutsch} (German template)</li>
 *   <li>{@code should_includeLoginUrl_when_emailRendered}</li>
 * </ol>
 * Additional edge-case tests cover error-handling resilience and missing-data fallbacks
 * (carried over from the prior 6.1b suite).
 */
@ExtendWith(MockitoExtension.class)
class SpeakerInvitationEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private ch.batbern.events.client.UserApiClient userApiClient;

    private SpeakerInvitationEmailService invitationEmailService;

    private SpeakerPool speaker;
    private Event event;
    private Session session;
    private InvitationCredentialsResponse freshCredentials;
    private InvitationCredentialsResponse useExistingCredentials;
    private String loginUrl;

    @BeforeEach
    void setUp() {
        invitationEmailService = new SpeakerInvitationEmailService(
                emailService, sessionRepository, emailTemplateService, userApiClient);
        // DB lookup defaults to empty so classpath templates load.
        when(emailTemplateService.findByKeyAndLocale(anyString(), anyString()))
                .thenReturn(Optional.empty());
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

        loginUrl = "https://batbern.ch/login";

        freshCredentials = new InvitationCredentialsResponse(
                InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD)
                .temporaryPassword("Tk7!aB2@nx9pQ4#z");
        useExistingCredentials = new InvitationCredentialsResponse(
                InvitationCredentialsResponse.ActionEnum.USE_EXISTING_PASSWORD);

        // Real Mustache-style variable replacement (mirrors EmailService.replaceVariables).
        when(emailService.replaceVariables(anyString(), any())).thenAnswer(invocation -> {
            String template = invocation.getArgument(0);
            java.util.Map<String, String> vars = invocation.getArgument(1);
            String result = template;
            // Positive Mustache conditionals.
            for (var entry : vars.entrySet()) {
                String openTag = "{{#" + entry.getKey() + "}}";
                String closeTag = "{{/" + entry.getKey() + "}}";
                boolean empty = entry.getValue() == null || entry.getValue().isBlank();
                while (result.contains(openTag) && result.contains(closeTag)) {
                    int open = result.indexOf(openTag);
                    int close = result.indexOf(closeTag, open);
                    if (close < 0) {
                        break;
                    }
                    String before = result.substring(0, open);
                    String content = result.substring(open + openTag.length(), close);
                    String after = result.substring(close + closeTag.length());
                    result = empty ? before + after : before + content + after;
                }
            }
            // Simple variable substitution.
            for (var entry : vars.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
            return result;
        });
    }

    // ============================================================
    // AC8 item 5 — the four required cases
    // ============================================================

    @Test
    @DisplayName("AC8 #1: renders temp-password block when action=FRESH_TEMP_PASSWORD (English)")
    void should_renderTemporaryPasswordBlock_when_actionIsFreshTempPassword() {
        when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        invitationEmailService.sendInvitationEmail(
                speaker, event, loginUrl, freshCredentials, Locale.ENGLISH);

        verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        // Temp password value appears.
        assertThat(body).contains("Tk7!aB2@nx9pQ4#z");
        // FRESH branch wording present, USE_EXISTING wording absent.
        assertThat(body).contains("Temporary password");
        assertThat(body).doesNotContain("existing password");
    }

    @Test
    @DisplayName("AC8 #2: renders use-existing-password block when action=USE_EXISTING_PASSWORD (English)")
    void should_renderUseExistingPasswordBlock_when_actionIsUseExistingPassword() {
        when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        invitationEmailService.sendInvitationEmail(
                speaker, event, loginUrl, useExistingCredentials, Locale.ENGLISH);

        verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        // No temp password appears.
        assertThat(body).doesNotContain("Tk7!aB2@nx9pQ4#z");
        // USE_EXISTING wording present, FRESH-only wording absent.
        assertThat(body).contains("existing password");
        assertThat(body).doesNotContain("Temporary password");
    }

    @Test
    @DisplayName("AC8 #3: renders in German locale (Einladung als Referent + de body)")
    void should_renderInGermanLocale_when_localeIsDeutsch() {
        when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        invitationEmailService.sendInvitationEmail(
                speaker, event, loginUrl, freshCredentials, Locale.GERMAN);

        verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).contains("Einladung als Referent");
        // German body must contain the German "temporary password" wording.
        String body = bodyCaptor.getValue();
        assertThat(body).contains("Tk7!aB2@nx9pQ4#z");
        assertThat(body).contains("Temporäres Passwort");
    }

    @Test
    @DisplayName("AC8 #4: includes the configured login URL")
    void should_includeLoginUrl_when_emailRendered() {
        when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        invitationEmailService.sendInvitationEmail(
                speaker, event, loginUrl, freshCredentials, Locale.ENGLISH);

        verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains(loginUrl);
        // Speaker email is the Cognito username.
        assertThat(bodyCaptor.getValue()).contains("john.doe@example.com");
    }

    @Nested
    @DisplayName("Cross-cutting concerns")
    class CrossCutting {

        @Test
        @DisplayName("addresses the recipient to the speaker's email")
        void should_sendEmail_when_invitationTriggered() {
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

            invitationEmailService.sendInvitationEmail(
                    speaker, event, loginUrl, freshCredentials, Locale.ENGLISH);

            verify(emailService).sendHtmlEmail(
                    eq("john.doe@example.com"),
                    contains("Speaker Invitation"),
                    anyString());
        }

        @Test
        @DisplayName("populates event details + speaker name in the body")
        void should_includeEventDetailsAndSpeakerName() {
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            invitationEmailService.sendInvitationEmail(
                    speaker, event, loginUrl, freshCredentials, Locale.ENGLISH);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            String body = bodyCaptor.getValue();
            assertThat(body).contains("BATbern 2026");
            assertThat(body).contains("15.03.2026");
            assertThat(body).contains("Kursaal Bern");
            assertThat(body).contains("John Doe");
            assertThat(body).contains("Cloud Architecture Patterns");
        }

        @Test
        @DisplayName("defaults to German when locale is null")
        void should_defaultToGerman_when_localeIsNull() {
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));

            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
            invitationEmailService.sendInvitationEmail(
                    speaker, event, loginUrl, freshCredentials, null);

            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Einladung als Referent");
        }

        @Test
        @DisplayName("does not throw when email sending fails")
        void should_notThrow_when_emailSendingFails() {
            when(sessionRepository.findById(speaker.getSessionId())).thenReturn(Optional.of(session));
            doThrow(new RuntimeException("Email server unavailable"))
                    .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

            // No exception escapes.
            invitationEmailService.sendInvitationEmail(
                    speaker, event, loginUrl, freshCredentials, Locale.ENGLISH);
        }

        @Test
        @DisplayName("uses TBA when venue is not set")
        void should_useTba_when_venueNotSet() {
            event.setVenueName(null);
            event.setVenueAddress(null);
            speaker.setSessionId(null);

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            invitationEmailService.sendInvitationEmail(
                    speaker, event, loginUrl, freshCredentials, Locale.ENGLISH);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("TBA");
        }

        @Test
        @DisplayName("uses DB template htmlBody when available (Story 10.2)")
        void should_useDbTemplate_whenAvailable() {
            EmailTemplate dbTemplate = new EmailTemplate();
            dbTemplate.setHtmlBody("<p>DB template for {{speakerName}}; password: {{temporaryPassword}}</p>");
            dbTemplate.setLayoutKey(null);
            when(emailTemplateService.findByKeyAndLocale("speaker-invitation", "de"))
                    .thenReturn(Optional.of(dbTemplate));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            invitationEmailService.sendInvitationEmail(
                    speaker, event, loginUrl, freshCredentials, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("John Doe");
            assertThat(bodyCaptor.getValue()).contains("Tk7!aB2@nx9pQ4#z");
        }
    }
}
