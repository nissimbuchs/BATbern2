package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.service.PrimarySpeakerResolver;
import ch.batbern.events.service.SpeakerReminderEmailService;
import ch.batbern.events.service.SpeakerReminderService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 16.8a AC7: the speaker audience. Six reminder templates, automatic tier per speaker,
 * reminder type matched to the speaker's state, disabled and unreachable speakers skipped.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Speaker audience adapter (Story 16.8a)")
class SpeakerAudienceAdapterTest {

    @Mock
    private SpeakerReminderService reminderService;
    @Mock
    private SpeakerReminderEmailService reminderEmailService;
    @Mock
    private PrimarySpeakerResolver primarySpeakerResolver;

    private SpeakerAudienceAdapter adapter;
    private final Event event = Event.builder().id(UUID.randomUUID()).eventCode("BATbern60").title("BATbern60")
            .organizerUsername("organizer1").build();

    @BeforeEach
    void setUp() {
        adapter = new SpeakerAudienceAdapter(reminderService, reminderEmailService, primarySpeakerResolver);
    }

    @Test
    @DisplayName("exactly the six reminder templates are allowed; automatic (null) too")
    void allowsOnlyTheSixReminderTemplates() {
        for (String type : List.of("response", "content")) {
            for (int tier = 1; tier <= 3; tier++) {
                assertThat(adapter.allowsTemplate("speaker-reminder-" + type + "-tier" + tier)).isTrue();
            }
        }
        assertThat(adapter.allowsTemplate(null)).isTrue();
        assertThat(adapter.allowsTemplate("speaker-invitation")).isFalse();
        assertThat(adapter.allowsTemplate("speaker-acceptance")).isFalse();
        assertThat(adapter.allowsTemplate("qna-new-questions")).isFalse();
        assertThat(adapter.allowsTemplate("speaker-reminder-content-tier4")).isFalse();
    }

    @Test
    @DisplayName("automatic mode without a reminder type is a 400")
    void automaticWithoutType_rejected() {
        assertThatThrownBy(() -> adapter.validate(request(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speakerReminderType");
    }

    @Test
    @DisplayName("a chosen template decides type and tier: content template → ACCEPTED speakers")
    void explicitTemplate_decidesTypeAndTier() {
        SpeakerAudienceAdapter.Choice choice = SpeakerAudienceAdapter.choice(request("speaker-reminder-content-tier2", null));

        assertThat(choice.reminderType()).isEqualTo("CONTENT");
        assertThat(choice.tier()).isEqualTo("TIER_2");
    }

    @Test
    @DisplayName("recipients: in name order; disabled → REMINDERS_DISABLED, no email → NO_EMAIL")
    void recipients_skipDisabledAndUnreachable() {
        SpeakerPool zoe = pool("Zoe Zed", false);
        SpeakerPool anna = pool("Anna Alpha", false);
        SpeakerPool muted = pool("Max Muted", true);
        SpeakerPool ghost = pool("Gil Ghost", false);
        when(reminderService.manualAudience(event, "RESPONSE")).thenReturn(List.of(zoe, anna, muted, ghost));
        profile(zoe, "zoe@example.com");
        profile(anna, "anna@example.com");
        profile(muted, "max@example.com");
        when(primarySpeakerResolver.resolve(ghost)).thenReturn(Optional.empty());

        Recipients recipients = adapter.resolveRecipients(context(request(null, "RESPONSE")));

        assertThat(recipients.recipients()).extracting(ComposeRecipient::displayName)
                .containsExactly("Anna Alpha", "Zoe Zed");
        assertThat(recipients.skipped()).extracting(SkippedRecipient::displayName, SkippedRecipient::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Max Muted", SkippedRecipient.REMINDERS_DISABLED),
                        org.assertj.core.groups.Tuple.tuple("Gil Ghost", SkippedRecipient.NO_EMAIL));
    }

    @Test
    @DisplayName("automatic: each speaker gets the tier detected from their own deadline, in their language")
    void automatic_tierPerSpeaker() {
        SpeakerPool anna = pool("Anna Alpha", false);
        profile(anna, "anna@example.com");
        LocalDate deadline = LocalDate.now().plusDays(2);
        when(reminderService.deadlineFor(anna, event, "RESPONSE")).thenReturn(deadline);
        when(reminderService.autoDetectTier(any(), eq(deadline))).thenReturn("TIER_3");
        when(reminderService.speakerLocale(anna)).thenReturn(Locale.ENGLISH);
        when(reminderEmailService.render(anna, event, "RESPONSE", "TIER_3", deadline, Locale.ENGLISH))
                .thenReturn(Optional.of(new SpeakerReminderEmailService.RenderedReminder("anna@example.com",
                        List.of(), "Final Reminder", "<p>Hi Anna</p>", "speaker-reminder-response-tier3",
                        "batbern60-moderator@batbern.ch")));
        ComposeRecipient recipient = new ComposeRecipient("Anna Alpha", "anna@example.com", List.of(), anna);

        Rendering rendering = adapter.render(context(request(null, "RESPONSE")), recipient, false);

        assertThat(rendering.templateKey()).isEqualTo("speaker-reminder-response-tier3");
        assertThat(rendering.locale()).isEqualTo("en");
        assertThat(rendering.subject()).isEqualTo("Final Reminder");
        assertThat(rendering.replyTo()).isEqualTo("batbern60-moderator@batbern.ch");
    }

    @Test
    @DisplayName("deliver: one request, one trigger, one recorded mail per template key used")
    void deliver_oneTriggerForAllSpeakers() {
        SpeakerPool anna = pool("Anna Alpha", false);
        SpeakerPool zoe = pool("Zoe Zed", false);
        ComposeRecipient a = new ComposeRecipient("Anna Alpha", "anna@example.com", List.of(), anna);
        ComposeRecipient z = new ComposeRecipient("Zoe Zed", "zoe@example.com", List.of(), zoe);
        MailTrigger trigger = MailTrigger.organizer("organizer1");
        when(reminderService.sendManualReminders(eq(event), eq("RESPONSE"), isNull(), eq(List.of(anna, zoe)),
                eq(trigger), eq("organizer1"))).thenReturn(List.of("speaker-reminder-response-tier1",
                "speaker-reminder-response-tier2"));
        LocalDate deadline = LocalDate.now().plusDays(7);
        when(reminderService.deadlineFor(any(), eq(event), eq("RESPONSE"))).thenReturn(deadline);
        when(reminderService.autoDetectTier(any(), any())).thenReturn("TIER_1");
        when(reminderService.speakerLocale(any())).thenReturn(Locale.GERMAN);
        when(reminderEmailService.render(any(), eq(event), eq("RESPONSE"), any(), any(), any()))
                .thenReturn(Optional.of(new SpeakerReminderEmailService.RenderedReminder("anna@example.com",
                        List.of(), "Reminder", "<p>Hi</p>", "speaker-reminder-response-tier1", null)));

        List<DeliveredMail> delivered = adapter.deliver(context(request(null, "RESPONSE")),
                new Recipients(List.of(a, z), List.of()), trigger);

        verify(reminderService).sendManualReminders(eq(event), eq("RESPONSE"), isNull(), eq(List.of(anna, zoe)),
                eq(trigger), eq("organizer1"));
        assertThat(delivered).extracting(d -> d.context().templateKey())
                .containsExactly("speaker-reminder-response-tier1", "speaker-reminder-response-tier2");
        assertThat(delivered).allSatisfy(d -> {
            assertThat(d.context().audience()).isEqualTo(MailContext.Audience.SPEAKERS);
            assertThat(d.context().sendGroupId()).startsWith(trigger.runId());
        });
    }

    private ComposeRequest request(String templateKey, String reminderType) {
        return new ComposeRequest(MailContext.Audience.SPEAKERS, MailContext.ContentSource.TEMPLATE, templateKey,
                "de", 0, false, reminderType, List.of(), null);
    }

    private ComposeContext context(ComposeRequest request) {
        return new ComposeContext(event, request, "organizer1");
    }

    private SpeakerPool pool(String name, boolean disabled) {
        return SpeakerPool.builder().id(UUID.randomUUID()).eventId(event.getId()).speakerName(name)
                .remindersDisabled(disabled).build();
    }

    private void profile(SpeakerPool pool, String email) {
        String[] parts = pool.getSpeakerName().split(" ");
        when(primarySpeakerResolver.resolve(pool)).thenReturn(Optional.of(
                new PrimarySpeakerResolver.PrimarySpeakerProfile(parts[0].toLowerCase(), email, parts[0], parts[1],
                        null, List.of())));
    }
}
