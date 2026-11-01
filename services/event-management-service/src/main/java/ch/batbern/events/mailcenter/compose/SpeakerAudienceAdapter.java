package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.service.PrimarySpeakerResolver;
import ch.batbern.events.service.PrimarySpeakerResolver.PrimarySpeakerProfile;
import ch.batbern.events.service.SpeakerReminderEmailService;
import ch.batbern.events.service.SpeakerReminderEmailService.RenderedReminder;
import ch.batbern.events.service.SpeakerReminderService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Speaker reminders (Story 16.8 AC7). The template list is the six
 * {@code speaker-reminder-{response,content}-tier{1,2,3}} keys; the SPEAKER category also holds the
 * invitation, acceptance and Q&amp;A mails, so it is not used as the allow-list. "Automatic"
 * ({@code templateKey = null}) gives each speaker the tier of their own deadline, so one send can
 * mix tiers: one recorded mail per template key. Delivery is the existing per-speaker reminder,
 * run server-side under one trigger, with reminder log, outreach history and escalation.
 */
@Component
@RequiredArgsConstructor
public class SpeakerAudienceAdapter implements AudienceAdapter {

    static final String RESPONSE = "RESPONSE";
    static final String CONTENT = "CONTENT";
    private static final Pattern REMINDER_KEY = Pattern.compile("speaker-reminder-(response|content)-tier([123])");

    private final SpeakerReminderService reminderService;
    private final SpeakerReminderEmailService reminderEmailService;
    private final PrimarySpeakerResolver primarySpeakerResolver;

    /** Reminder type and, for a chosen template, its tier; tier null = automatic. */
    record Choice(String reminderType, String tier) {
    }

    static Choice choice(ComposeRequest request) {
        if (request.templateKey() != null) {
            Matcher matcher = REMINDER_KEY.matcher(request.templateKey());
            if (matcher.matches()) {
                return new Choice(matcher.group(1).toUpperCase(Locale.ROOT), "TIER_" + matcher.group(2));
            }
        }
        return new Choice(request.speakerReminderType(), null);
    }

    @Override
    public MailContext.Audience audience() {
        return MailContext.Audience.SPEAKERS;
    }

    @Override
    public boolean personalized() {
        return true;
    }

    @Override
    public Set<String> allowedCategories() {
        return Set.of("SPEAKER");
    }

    @Override
    public boolean allowsTemplate(String templateKey) {
        return templateKey == null || REMINDER_KEY.matcher(templateKey).matches();
    }

    @Override
    public Set<String> allowedVariables() {
        return Set.of("speakerName", "eventTitle", "eventDate", "deadline", "daysRemaining", "portalLink",
                "organizerName", "organizerEmail", "currentYear", "logoUrl");
    }

    @Override
    public Set<String> credentialVariables() {
        return Set.of();
    }

    @Override
    public void validate(ComposeRequest request) {
        String type = choice(request).reminderType();
        if (type == null) {
            throw new IllegalArgumentException(
                    "speakerReminderType (RESPONSE or CONTENT) is required when no template is chosen");
        }
        if (!RESPONSE.equals(type) && !CONTENT.equals(type)) {
            throw new IllegalArgumentException("speakerReminderType must be RESPONSE or CONTENT");
        }
    }

    @Override
    public Recipients resolveRecipients(ComposeContext context) {
        String type = choice(context.request()).reminderType();
        List<ComposeRecipient> recipients = new ArrayList<>();
        List<SkippedRecipient> skipped = new ArrayList<>();
        for (SpeakerPool speaker : reminderService.manualAudience(context.event(), type)) {
            if (Boolean.TRUE.equals(speaker.getRemindersDisabled())) {
                skipped.add(new SkippedRecipient(speaker.getSpeakerName(), SkippedRecipient.REMINDERS_DISABLED));
                continue;
            }
            Optional<PrimarySpeakerProfile> profile = primarySpeakerResolver.resolve(speaker)
                    .filter(p -> p.email() != null && !p.email().isBlank());
            if (profile.isEmpty()) {
                skipped.add(new SkippedRecipient(speaker.getSpeakerName(), SkippedRecipient.NO_EMAIL));
                continue;
            }
            String name = Optional.ofNullable(profile.get().fullName()).filter(n -> !n.isBlank())
                    .orElse(speaker.getSpeakerName());
            recipients.add(new ComposeRecipient(name, profile.get().email(), profile.get().additionalEmails(),
                    speaker));
        }
        recipients.sort(Comparator.comparing(ComposeRecipient::displayName, String.CASE_INSENSITIVE_ORDER));
        return new Recipients(recipients, skipped);
    }

    /** Speaker reminders carry no credential, so a test rendering equals the real one. */
    @Override
    public Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test) {
        SpeakerPool speaker = (SpeakerPool) recipient.key();
        Choice choice = choice(context.request());
        LocalDate deadline = reminderService.deadlineFor(speaker, context.event(), choice.reminderType());
        String tier = choice.tier() != null ? choice.tier() : reminderService.autoDetectTier(LocalDate.now(), deadline);
        Locale locale = reminderService.speakerLocale(speaker);
        RenderedReminder mail = reminderEmailService.render(speaker, context.event(), choice.reminderType(), tier,
                        deadline, locale)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker " + recipient.displayName() + " has no address"));
        return new Rendering(mail.subject(), mail.html(), mail.templateKey(),
                "de".equals(locale.getLanguage()) ? "de" : "en", mail.replyTo(), List.of(), List.of());
    }

    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        Choice choice = choice(context.request());
        // Render first: the first rendering per template key opens that key's record.
        Map<String, Rendering> firstByKey = new LinkedHashMap<>();
        for (ComposeRecipient recipient : recipients.recipients()) {
            Rendering rendering = render(context, recipient, false);
            firstByKey.putIfAbsent(rendering.templateKey(), rendering);
        }
        List<SpeakerPool> speakers = recipients.recipients().stream().map(r -> (SpeakerPool) r.key()).toList();
        List<String> keys = reminderService.sendManualReminders(context.event(), choice.reminderType(),
                choice.tier(), speakers, trigger, context.organizerUsername());
        Rendering fallback = firstByKey.values().iterator().next();
        return keys.stream()
                .map(key -> new DeliveredMail(
                        MailContext.of(trigger, context.event().getEventCode(), MailContext.Audience.SPEAKERS, key),
                        firstByKey.getOrDefault(key, fallback)))
                .toList();
    }
}
