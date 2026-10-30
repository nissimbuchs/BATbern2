package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.forwarding.ModeratorReplyAddress;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.0 AC1: the speaker mails without their own unit test (tiered reminder, quality-review
 * revision request) carry Reply-To {@code batbern{N}-moderator@}. Invitation and acceptance are
 * covered in their own test classes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Speaker mails reply to the moderator")
class SpeakerModeratorReplyToTest {

    private static final String MODERATOR = "batbern60-moderator@batbern.ch";

    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private PrimarySpeakerResolver primarySpeakerResolver;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private SpeakerPoolRepository speakerPoolRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionContentHistoryRepository sessionContentHistoryRepository;
    @Mock
    private SpeakerWorkflowService speakerWorkflowService;

    private final ModeratorReplyAddress moderatorReplyAddress =
            new ModeratorReplyAddress(new EmailForwardingProperties(false, null, null, null, null, 0));
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
    @DisplayName("should_replyToModeratorAlias_when_tieredReminderSent")
    void should_replyToModeratorAlias_when_tieredReminderSent() {
        SpeakerReminderEmailService service = new SpeakerReminderEmailService(
                emailService, emailTemplateService, primarySpeakerResolver, moderatorReplyAddress);
        ReflectionTestUtils.setField(service, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(service, "organizerName", "BATbern Team");
        ReflectionTestUtils.setField(service, "organizerEmail", "events@batbern.ch");

        service.sendReminderEmail(speaker, event, "RESPONSE", "TIER_1", LocalDate.now().plusDays(7),
                Locale.ENGLISH);

        verify(emailService).sendHtmlEmail(eq("speaker@example.com"), anyList(), anyString(), anyString(),
                eq(MODERATOR));
    }

    @Test
    @DisplayName("should_replyToModeratorAlias_when_qualityReviewRequestsRevision")
    void should_replyToModeratorAlias_when_qualityReviewRequestsRevision() {
        QualityReviewService service = new QualityReviewService(eventRepository, speakerPoolRepository,
                sessionRepository, sessionContentHistoryRepository, emailService, speakerWorkflowService,
                primarySpeakerResolver, moderatorReplyAddress);
        when(speakerPoolRepository.findById(speaker.getId())).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        service.rejectContent(speaker.getId().toString(), "Please shorten the abstract", "organizer");

        verify(emailService).sendHtmlEmail(eq("speaker@example.com"), anyList(), anyString(), anyString(),
                eq(MODERATOR));
    }
}
