package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.forwarding.ForwardingModeratorReplyAddress;
import ch.batbern.events.mailcenter.render.ModeratorReplyAddress;
import ch.batbern.events.mailcenter.render.MailPipelines;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Speaker reminder variables (Story 16.5 AC12). The Story 16.0 AC1 Reply-To cases left this mock
 * test in Story 4.10 (touch rule): the tiered reminder's moderator Reply-To is asserted on the
 * SesRecorder in ManualSpeakerReminderIntegrationTest, the quality-review revision request's in
 * SpeakerCrossEventScopingIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Speaker reminder variables")
class SpeakerModeratorReplyToTest {

    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private PrimarySpeakerResolver primarySpeakerResolver;
    @Mock
    private SessionRepository sessionRepository;

    private final ModeratorReplyAddress moderatorReplyAddress =
            new ForwardingModeratorReplyAddress(new EmailForwardingProperties(false, null, null, null, null, 0));
    private SpeakerPool speaker;
    private Event event;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventCode("BATbern60");
        event.setTitle("Test Event");
        event.setDate(Instant.parse("2026-12-01T17:00:00Z"));
        speaker = new SpeakerPool();
        speaker.setId(UUID.randomUUID());
        speaker.setEventId(event.getId());
        speaker.setSpeakerName("Test Speaker");
        when(primarySpeakerResolver.resolve(any())).thenReturn(Optional.of(
                new PrimarySpeakerResolver.PrimarySpeakerProfile("test.speaker", "speaker@example.com",
                        "Test", "Speaker", "ExampleCo", List.of())));
        when(emailTemplateService.findByKeyAndLocale(anyString(), anyString())).thenReturn(Optional.empty());
        when(emailTemplateService.resolveSubject(anyString(), anyString())).thenReturn(Optional.empty());
        when(emailService.replaceVariables(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Story 16.5 AC12: reminders carry the speaker's session title as sessionTitle, empty without one")
    void should_offerSessionTitle_inReminderVariables() {
        SpeakerReminderEmailService service = new SpeakerReminderEmailService(
                emailService, MailPipelines.over(emailService, emailTemplateService), primarySpeakerResolver,
                moderatorReplyAddress, sessionRepository);
        ReflectionTestUtils.setField(service, "baseUrl", "https://batbern.ch");
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setTitle("Clean Architecture in Practice");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(service.variables(speaker, event, LocalDate.of(2026, 11, 20), Locale.GERMAN))
                .containsEntry("sessionTitle", "");
        speaker.setSessionId(sessionId);
        assertThat(service.variables(speaker, event, LocalDate.of(2026, 11, 20), Locale.GERMAN))
                .containsEntry("sessionTitle", "Clean Architecture in Practice")
                .containsEntry("speakerName", "Test Speaker");
    }
}
