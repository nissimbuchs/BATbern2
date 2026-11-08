package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.mailcenter.render.ModeratorReplyAddress;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service for rendering and sending speaker deadline reminder emails.
 * Story 6.5: Automated Deadline Reminders (AC4)
 *
 * Follows the same template loading pattern as SpeakerInvitationEmailService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerReminderEmailService {

    private final EmailService emailService;
    private final MailPipeline mailPipeline;
    private final PrimarySpeakerResolver primarySpeakerResolver;
    private final ModeratorReplyAddress moderatorReplyAddress;
    private final SessionRepository sessionRepository;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;


    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Send a reminder email to a speaker.
     *
     * <p>Story 11.F.1: dropped the {@code portalToken} parameter — speakers now
     * authenticate via Cognito; the dashboard link no longer carries a
     * {@code ?token=} magic-link query parameter.
     *
     * @param speaker the speaker pool entry
     * @param event the event
     * @param reminderType RESPONSE or CONTENT
     * @param tier TIER_1, TIER_2, or TIER_3
     * @param deadline the deadline date
     * @param locale email language (defaults to German)
     * @param mailTrigger the scheduler run or organizer request this reminder belongs to (Story 16.1a)
     */
    public void sendReminderEmail(
            SpeakerPool speaker,
            Event event,
            String reminderType,
            String tier,
            LocalDate deadline,
            Locale locale,
            MailTrigger mailTrigger
    ) {
        Optional<RenderedReminder> rendered = render(speaker, event, reminderType, tier, deadline, locale);
        if (rendered.isEmpty()) {
            log.warn("Skipping reminder for speaker pool {} — no resolvable primary speaker email "
                    + "(session unset, no PRIMARY_SPEAKER session_user, or CUMS degraded)",
                    speaker.getId());
            return;
        }
        RenderedReminder mail = rendered.get();
        try {
            // Story 16.1a AC2: one recorded mail per run (or manual click), event and template.
            MailContext context = MailContext.of(mailTrigger, event.getEventCode(), MailContext.Audience.SPEAKERS,
                    mail.templateKey());
            emailService.sendHtmlEmail(context, mail.to(), mail.cc(), mail.subject(), mail.html(), mail.replyTo());

            log.info("Reminder email sent: type={}, tier={}, speaker={}, event={}, ccCount={}",
                    reminderType, tier, LoggingUtils.maskEmail(mail.to()), event.getEventCode(), mail.cc().size());

        } catch (Exception e) {
            log.error("Failed to send reminder email: type={}, tier={}, speaker={}",
                    reminderType, tier, LoggingUtils.maskEmail(mail.to()), e);
            throw new RuntimeException("Failed to send reminder email", e);
        }
    }

    /**
     * A reminder as the speaker gets it (Story 16.8: the send and the composer's preview both
     * render here). Empty when the speaker has no resolvable address.
     *
     * @param to          the primary speaker's address
     * @param cc          the speaker's additional addresses (Story 10.32)
     * @param subject     subject as sent
     * @param html        body as sent
     * @param templateKey e.g. {@code speaker-reminder-response-tier1}
     * @param replyTo     the event's moderator address (Story 16.0 AC1)
     */
    public record RenderedReminder(String to, List<String> cc, String subject, String html, String templateKey,
            String replyTo) {
    }

    public Optional<RenderedReminder> render(SpeakerPool speaker, Event event, String reminderType, String tier,
            LocalDate deadline, Locale locale) {
        // Phase B of the post-Epic-11 cleanup: recipient routing resolves through session_users +
        // UserApiClient, not the stale speaker_pool.email column, so a reassigned session's
        // PRIMARY_SPEAKER gets the next reminder.
        Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> primary =
                primarySpeakerResolver.resolve(speaker);
        String recipientEmail = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::email)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
        if (recipientEmail == null) {
            return Optional.empty();
        }
        String recipientName = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::fullName)
                .filter(n -> !n.isEmpty())
                .orElse(speaker.getSpeakerName());
        Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;
        EmailContent content = loadReminderTemplate(emailLocale, recipientName, sessionTitle(speaker), event,
                reminderType, tier, deadline);
        // Story 10.32: CC speaker's additional emails (empty list = unchanged behaviour)
        List<String> cc = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::additionalEmails)
                .orElse(List.of());
        // Story 16.0 AC1: the speaker's answer reaches the moderator, not replies@.
        return Optional.of(new RenderedReminder(recipientEmail, cc == null ? List.of() : cc, content.subject(),
                content.html(), templateKey(reminderType, tier), moderatorReplyAddress.forEvent(event)));
    }

    private String buildSubject(Locale locale, String reminderType, String tier, String eventTitle) {
        boolean isGerman = locale.getLanguage().equals("de");
        String typeLabel;
        String urgency;

        if ("RESPONSE".equals(reminderType)) {
            typeLabel = isGerman ? "Einladung" : "Invitation";
        } else {
            typeLabel = isGerman ? "Materialien" : "Materials";
        }

        switch (tier) {
            case "TIER_1":
                urgency = isGerman ? "Erinnerung" : "Reminder";
                break;
            case "TIER_2":
                urgency = isGerman ? "Dringende Erinnerung" : "Urgent Reminder";
                break;
            case "TIER_3":
                urgency = isGerman ? "Letzte Erinnerung" : "Final Reminder";
                break;
            default:
                urgency = isGerman ? "Erinnerung" : "Reminder";
        }

        return urgency + ": " + typeLabel + " - " + eventTitle;
    }

    private record EmailContent(String html, String subject) {}

    /** e.g. RESPONSE + TIER_1 → {@code speaker-reminder-response-tier1}. */
    public static String templateKey(String reminderType, String tier) {
        return "speaker-reminder-" + reminderType.toLowerCase() + "-" + tier.toLowerCase().replace("_", "");
    }

    private EmailContent loadReminderTemplate(
            Locale locale,
            String speakerName,
            String sessionTitle,
            Event event,
            String reminderType,
            String tier,
            LocalDate deadline
    ) {
        String lang = locale.getLanguage().equals("de") ? "de" : "en";
        String templateKey = templateKey(reminderType, tier);
        Map<String, String> variables = variables(speakerName, sessionTitle, event, deadline, lang);

        // Story 16.11: same pipeline as every other mail (was a private merge-then-replace copy).
        RenderedMail mail = mailPipeline.renderNotice(mailPipeline.template(templateKey, lang), variables);
        String html = mail.html();
        String subject = mail.subject() != null ? mail.subject()
                : buildSubject(locale, reminderType, tier, event.getTitle());
        return new EmailContent(html, subject);
    }

    /**
     * The variables a reminder to {@code speaker} is rendered with (Story 16.11 AC9). The name
     * resolves as in {@link #render}.
     */
    public Map<String, String> variables(SpeakerPool speaker, Event event, LocalDate deadline, Locale locale) {
        Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> primary = primarySpeakerResolver.resolve(speaker);
        String recipientName = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::fullName)
                .filter(n -> !n.isEmpty())
                .orElse(speaker.getSpeakerName());
        String lang = locale != null && "de".equals(locale.getLanguage()) ? "de" : "en";
        return variables(recipientName, sessionTitle(speaker), event, deadline, lang);
    }

    /** Story 16.5 AC12: the title of the speaker's session, empty when none. */
    private String sessionTitle(SpeakerPool speaker) {
        return Optional.ofNullable(speaker.getSessionId()).flatMap(sessionRepository::findById)
                .map(Session::getTitle).orElse("");
    }

    private Map<String, String> variables(String speakerName, String sessionTitle, Event event, LocalDate deadline,
            String lang) {
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        // Story 11.F.1: Cognito-only dashboard link (no magic-link token query param).
        String portalLink = baseUrl + "/speaker-portal/dashboard";

        return mailPipeline.variables().event(event, lang)
                .put(MailVariable.SPEAKER_NAME, speakerName)
                .put(MailVariable.SESSION_TITLE, sessionTitle)
                .put(MailVariable.DEADLINE, deadline.format(DATE_FORMATTER))
                .put(MailVariable.DAYS_REMAINING, Math.max(0, daysRemaining))
                .put(MailVariable.PORTAL_LINK, portalLink)
                .asMap();
    }
}
