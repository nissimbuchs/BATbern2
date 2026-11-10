package ch.batbern.events.service;

import ch.batbern.events.config.ReminderProperties;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerReminderLog;
import ch.batbern.events.notification.NotificationService;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerReminderLogRepository;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerReminderService - Story 6.5.
 *
 * Tests service logic with mocked repositories and external services.
 * Covers: AC2 (deadline detection), AC3 (deduplication), AC5 (smart skipping),
 * AC7 (escalation), AC8 (manual trigger).
 */
@ExtendWith(MockitoExtension.class)
class SpeakerReminderServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SpeakerReminderLogRepository reminderLogRepository;

    @Mock
    private OutreachHistoryRepository outreachHistoryRepository;

    @Mock
    private SessionContentHistoryRepository sessionContentHistoryRepository;

    @Mock
    private SpeakerReminderEmailService reminderEmailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PrimarySpeakerResolver primarySpeakerResolver;

    @Mock
    private ch.batbern.events.client.UserApiClient userApiClient;

    private ReminderProperties reminderProperties;

    @InjectMocks
    private SpeakerReminderService speakerReminderService;

    private Event testEvent;
    private SpeakerPool testSpeaker;
    private UUID eventId;
    private UUID speakerPoolId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        speakerPoolId = UUID.randomUUID();

        // Set up reminder properties
        reminderProperties = new ReminderProperties();
        reminderProperties.setEnabled(true);
        reminderProperties.setEscalateAfterTier3(true);
        reminderProperties.setTiers(List.of(
                new ReminderProperties.TierConfig("TIER_1", 14),
                new ReminderProperties.TierConfig("TIER_2", 7),
                new ReminderProperties.TierConfig("TIER_3", 3)
        ));

        // Recreate service with properties (since @InjectMocks doesn't handle this)
        speakerReminderService = new SpeakerReminderService(
                speakerPoolRepository, eventRepository, reminderLogRepository,
                outreachHistoryRepository, sessionContentHistoryRepository,
                reminderEmailService,
                notificationService, reminderProperties,
                primarySpeakerResolver, userApiClient
        );

        testEvent = Event.builder()
                .id(eventId)
                .eventCode("BATbern99")
                .title("Test Event")
                .date(Instant.now().plusSeconds(86400 * 30)) // 30 days from now
                .build();

        testSpeaker = SpeakerPool.builder()
                .id(speakerPoolId)
                .eventId(eventId)
                .speakerName("John Doe")
                .status(SpeakerWorkflowState.INVITED)
                .responseDeadline(LocalDate.now().plusDays(14)) // exactly TIER_1
                .remindersDisabled(false)
                .build();

        // Phase B: contactability is now resolved via PrimarySpeakerResolver. Default to
        // the speaker's pool email so existing assertions about reminder-eligible
        // speakers continue to hold. Individual tests can override to simulate
        // "no resolvable recipient". lenient() because pure-utility tests (findMatchingTier,
        // autoDetectTier) never touch the resolver.
        org.mockito.Mockito.lenient().when(primarySpeakerResolver.resolveEmail(any()))
                .thenReturn(Optional.of("john@example.com"));
        org.mockito.Mockito.lenient().when(primarySpeakerResolver.resolve(any()))
                .thenReturn(Optional.of(
                        new PrimarySpeakerResolver.PrimarySpeakerProfile(
                                "john.doe", "john@example.com", "John", "Doe", "TestCo")));
    }

    @Nested
    @DisplayName("findMatchingTier")
    class FindMatchingTier {

        @Test
        @DisplayName("should return TIER_1 when exactly 14 days before deadline")
        void shouldReturnTier1_when14DaysBeforeDeadline() {
            LocalDate deadline = LocalDate.now().plusDays(14);
            String tier = speakerReminderService.findMatchingTier(LocalDate.now(), deadline);
            assertThat(tier).isEqualTo("TIER_1");
        }

        @Test
        @DisplayName("should return TIER_2 when exactly 7 days before deadline")
        void shouldReturnTier2_when7DaysBeforeDeadline() {
            LocalDate deadline = LocalDate.now().plusDays(7);
            String tier = speakerReminderService.findMatchingTier(LocalDate.now(), deadline);
            assertThat(tier).isEqualTo("TIER_2");
        }

        @Test
        @DisplayName("should return TIER_3 when exactly 3 days before deadline")
        void shouldReturnTier3_when3DaysBeforeDeadline() {
            LocalDate deadline = LocalDate.now().plusDays(3);
            String tier = speakerReminderService.findMatchingTier(LocalDate.now(), deadline);
            assertThat(tier).isEqualTo("TIER_3");
        }

        @Test
        @DisplayName("should return null when no tier matches")
        void shouldReturnNull_whenNoTierMatches() {
            LocalDate deadline = LocalDate.now().plusDays(10); // Not 14, 7, or 3
            String tier = speakerReminderService.findMatchingTier(LocalDate.now(), deadline);
            assertThat(tier).isNull();
        }
    }

    @Nested
    @DisplayName("autoDetectTier")
    class AutoDetectTier {

        @Test
        @DisplayName("should detect closest tier for manual trigger")
        void shouldDetectClosestTier() {
            LocalDate deadline = LocalDate.now().plusDays(8); // closest to TIER_2 (7)
            String tier = speakerReminderService.autoDetectTier(LocalDate.now(), deadline);
            assertThat(tier).isEqualTo("TIER_2");
        }

        @Test
        @DisplayName("should detect TIER_1 for 12 days before deadline")
        void shouldDetectTier1For12Days() {
            LocalDate deadline = LocalDate.now().plusDays(12); // closest to TIER_1 (14)
            String tier = speakerReminderService.autoDetectTier(LocalDate.now(), deadline);
            assertThat(tier).isEqualTo("TIER_1");
        }

        @Test
        @DisplayName("should detect TIER_3 for 1 day before deadline")
        void shouldDetectTier3For1Day() {
            LocalDate deadline = LocalDate.now().plusDays(1); // closest to TIER_3 (3)
            String tier = speakerReminderService.autoDetectTier(LocalDate.now(), deadline);
            assertThat(tier).isEqualTo("TIER_3");
        }
    }

    @Nested
    @DisplayName("shouldSendReminder")
    class ShouldSendReminder {

        @Test
        @DisplayName("should return true when all conditions met")
        void shouldReturnTrue_whenAllConditionsMet() {
            when(reminderLogRepository.existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
                    speakerPoolId, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline(), "SYSTEM"))
                    .thenReturn(false);

            boolean result = speakerReminderService.shouldSendReminder(
                    testSpeaker, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should skip when PrimarySpeakerResolver returns no email")
        void shouldSkip_whenNoResolvableEmail() {
            // Phase B: contactability is now derived from PrimarySpeakerResolver
            // (session_users + UserApiClient), not the pool.email column.
            when(primarySpeakerResolver.resolveEmail(testSpeaker))
                    .thenReturn(java.util.Optional.empty());
            boolean result = speakerReminderService.shouldSendReminder(
                    testSpeaker, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline());
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should skip when resolved email is blank — defensive filter")
        void shouldSkip_whenResolvedEmailBlank() {
            // resolveEmail's filter() drops blank strings; emulate by returning empty.
            when(primarySpeakerResolver.resolveEmail(testSpeaker))
                    .thenReturn(java.util.Optional.empty());
            boolean result = speakerReminderService.shouldSendReminder(
                    testSpeaker, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline());
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should skip when reminders disabled")
        void shouldSkip_whenRemindersDisabled() {
            testSpeaker.setRemindersDisabled(true);
            boolean result = speakerReminderService.shouldSendReminder(
                    testSpeaker, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline());
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should skip when already sent (dedup)")
        void shouldSkip_whenAlreadySent() {
            when(reminderLogRepository.existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
                    speakerPoolId, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline(), "SYSTEM"))
                    .thenReturn(true);

            boolean result = speakerReminderService.shouldSendReminder(
                    testSpeaker, "RESPONSE", "TIER_1", testSpeaker.getResponseDeadline());
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("processReminders")
    class ProcessReminders {

        @Test
        @DisplayName("should return empty result when reminders disabled")
        void shouldReturnEmpty_whenDisabled() {
            reminderProperties.setEnabled(false);

            var result = speakerReminderService.processReminders();

            assertThat(result.responseReminders()).isZero();
            assertThat(result.contentReminders()).isZero();
            assertThat(result.skipped()).isZero();
        }

        @Test
        @DisplayName("should process response reminder for INVITED speaker at TIER_1")
        void shouldProcessResponseReminder() {
            when(eventRepository.findByDateAfter(any(Instant.class))).thenReturn(List.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(testSpeaker));
            when(reminderLogRepository.existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
                    any(), any(), any(), any(), any())).thenReturn(false);

            var result = speakerReminderService.processReminders();

            assertThat(result.responseReminders()).isEqualTo(1);
            assertThat(result.contentReminders()).isZero();
            verify(reminderEmailService).sendReminderEmail(
                    eq(testSpeaker), eq(testEvent), eq("RESPONSE"), eq("TIER_1"),
                    eq(testSpeaker.getResponseDeadline()), eq(Locale.GERMAN), any());
            verify(reminderLogRepository).save(any(SpeakerReminderLog.class));
            verify(outreachHistoryRepository).save(any(OutreachHistory.class));
        }

        @Test
        @DisplayName("should send the reminder in English when the speaker prefers English (#1119)")
        void shouldUseSpeakerLanguagePreference() {
            when(eventRepository.findByDateAfter(any(Instant.class))).thenReturn(List.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(testSpeaker));
            when(reminderLogRepository.existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
                    any(), any(), any(), any(), any())).thenReturn(false);
            when(primarySpeakerResolver.resolve(testSpeaker)).thenReturn(Optional.of(
                    new PrimarySpeakerResolver.PrimarySpeakerProfile(
                            "john.doe", "john@example.com", "John", "Doe", null)));
            when(userApiClient.getPreferredLanguage("john.doe")).thenReturn("en");

            speakerReminderService.processReminders();

            verify(reminderEmailService).sendReminderEmail(
                    eq(testSpeaker), eq(testEvent), eq("RESPONSE"), eq("TIER_1"),
                    eq(testSpeaker.getResponseDeadline()), eq(Locale.ENGLISH), any());
        }

        @Test
        @DisplayName("should process content reminder for ACCEPTED speaker with PENDING content")
        void shouldProcessContentReminder() {
            testSpeaker.setStatus(SpeakerWorkflowState.ACCEPTED);
            testSpeaker.setContentDeadline(LocalDate.now().plusDays(7)); // TIER_2
            testSpeaker.setResponseDeadline(null); // no response deadline

            when(eventRepository.findByDateAfter(any(Instant.class))).thenReturn(List.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(testSpeaker));
            when(reminderLogRepository.existsBySpeakerPoolIdAndReminderTypeAndTierAndDeadlineDateAndTriggeredBy(
                    any(), any(), any(), any(), any())).thenReturn(false);

            var result = speakerReminderService.processReminders();

            assertThat(result.responseReminders()).isZero();
            assertThat(result.contentReminders()).isEqualTo(1);
            verify(reminderEmailService).sendReminderEmail(
                    eq(testSpeaker), eq(testEvent), eq("CONTENT"), eq("TIER_2"),
                    eq(testSpeaker.getContentDeadline()), eq(Locale.GERMAN), any());
        }

        @Test
        @DisplayName("should skip speaker with non-matching deadline")
        void shouldSkipNonMatchingDeadline() {
            testSpeaker.setResponseDeadline(LocalDate.now().plusDays(10)); // No tier matches

            when(eventRepository.findByDateAfter(any(Instant.class))).thenReturn(List.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(testSpeaker));

            var result = speakerReminderService.processReminders();

            assertThat(result.responseReminders()).isZero();
            assertThat(result.skipped()).isZero();
            verify(reminderEmailService, never()).sendReminderEmail(
                    any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("manual reminders (Mail Center composer, Story 16.8)")
    class ManualReminders {

        @Test
        @DisplayName("sendManualReminders: explicit tier, outreach logged as manual_email by the organizer")
        void shouldSendWithExplicitTier_andLogOutreach() {
            List<String> keys = speakerReminderService.sendManualReminders(testEvent, "RESPONSE", "TIER_2",
                    List.of(testSpeaker), MailTrigger.organizer("organizer1"), "organizer1");

            assertThat(keys).containsExactly("speaker-reminder-response-tier2");
            verify(reminderEmailService).sendReminderEmail(
                    eq(testSpeaker), eq(testEvent), eq("RESPONSE"), eq("TIER_2"),
                    eq(testSpeaker.getResponseDeadline()), eq(Locale.GERMAN), any());
            ArgumentCaptor<OutreachHistory> outreachCaptor = ArgumentCaptor.forClass(OutreachHistory.class);
            verify(outreachHistoryRepository).save(outreachCaptor.capture());
            assertThat(outreachCaptor.getValue().getOrganizerUsername()).isEqualTo("organizer1");
            assertThat(outreachCaptor.getValue().getContactMethod()).isEqualTo("manual_email");
        }

        @Test
        @DisplayName("sendManualReminders: no tier → each speaker's own tier from their deadline")
        void shouldAutoDetectTier() {
            testSpeaker.setResponseDeadline(LocalDate.now().plusDays(8)); // closest to TIER_2

            List<String> keys = speakerReminderService.sendManualReminders(testEvent, "RESPONSE", null,
                    List.of(testSpeaker), MailTrigger.organizer("organizer1"), "organizer1");

            assertThat(keys).containsExactly("speaker-reminder-response-tier2");
        }

        @Test
        @DisplayName("sendManualReminders: a speaker with reminders disabled is not mailed; positive control mailed")
        void shouldSkipRemindersDisabled() {
            SpeakerPool muted = SpeakerPool.builder().id(UUID.randomUUID()).eventId(eventId)
                    .speakerName("Mia Muted").status(SpeakerWorkflowState.INVITED)
                    .responseDeadline(LocalDate.now().plusDays(14)).remindersDisabled(true).build();

            speakerReminderService.sendManualReminders(testEvent, "RESPONSE", "TIER_1",
                    List.of(muted, testSpeaker), MailTrigger.organizer("organizer1"), "organizer1");

            verify(reminderEmailService).sendReminderEmail(eq(testSpeaker), any(), any(), any(), any(), any(), any());
            verify(reminderEmailService, never()).sendReminderEmail(eq(muted), any(), any(), any(), any(), any(),
                    any());
        }

        @Test
        @DisplayName("manualAudience: RESPONSE → INVITED only; CONTENT → ACCEPTED without submitted content")
        void shouldPickTheAudienceByType() {
            UUID submittedSession = UUID.randomUUID();
            SpeakerPool accepted = SpeakerPool.builder().id(UUID.randomUUID()).eventId(eventId)
                    .speakerName("Anna Accepted").status(SpeakerWorkflowState.ACCEPTED).build();
            SpeakerPool submitted = SpeakerPool.builder().id(UUID.randomUUID()).eventId(eventId)
                    .speakerName("Sam Submitted").status(SpeakerWorkflowState.ACCEPTED)
                    .sessionId(submittedSession).build();
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(testSpeaker, accepted, submitted));
            when(sessionContentHistoryRepository.existsBySessionId(submittedSession)).thenReturn(true);

            assertThat(speakerReminderService.manualAudience(testEvent, "RESPONSE")).containsExactly(testSpeaker);
            assertThat(speakerReminderService.manualAudience(testEvent, "CONTENT")).containsExactly(accepted);
        }

        @Test
        @DisplayName("deadlineFor: no CONTENT or RESPONSE deadline → the event date (regression)")
        void shouldFallBackToEventDate_whenDeadlineIsNull() {
            LocalDate eventDay = testEvent.getDate().atZone(ZoneId.of("Europe/Zurich")).toLocalDate();
            testSpeaker.setContentDeadline(null);
            testSpeaker.setResponseDeadline(null);

            assertThat(speakerReminderService.deadlineFor(testSpeaker, testEvent, "CONTENT")).isEqualTo(eventDay);
            assertThat(speakerReminderService.deadlineFor(testSpeaker, testEvent, "RESPONSE")).isEqualTo(eventDay);
        }
    }
}
