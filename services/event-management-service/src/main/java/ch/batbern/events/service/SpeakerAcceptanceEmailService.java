package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.forwarding.ModeratorReplyAddress;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service for sending speaker acceptance confirmation emails.
 * Story 6.2a AC9: Acceptance Confirmation Email
 *
 * Features:
 * - Sends confirmation email when speaker accepts invitation
 * - Includes profile and content submission URLs with VIEW token
 * - i18n support (German/English)
 * - Async sending (non-blocking)
 *
 * Rationale: Without this email, speakers see portal URLs only once on
 * the success screen. This email allows them to return later to update
 * profile or submit content.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerAcceptanceEmailService {

    private final EmailService emailService;
    private final SessionRepository sessionRepository;
    private final MailPipeline mailPipeline;
    private final PrimarySpeakerResolver primarySpeakerResolver;
    private final ModeratorReplyAddress moderatorReplyAddress;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;


    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    /**
     * Send acceptance confirmation email asynchronously.
     * AC9: Confirmation email with portal links.
     *
     * <p>Story 11.F.1: dropped the residual {@code viewToken} parameter — the magic-link
     * portal URL was deleted in Story 11.E.3 review (D1) and the parameter was retained
     * on the signature pending Phase F. Portal links now point at Cognito-secured routes
     * (eventCode in the path, Bearer from the session).
     *
     * @param speaker the speaker pool entry
     * @param event   the event
     * @param locale  preferred language (defaults to German)
     */
    @Async
    public void sendAcceptanceConfirmationEmail(
            SpeakerPool speaker,
            Event event,
            Locale locale
    ) {
        // Phase B: route to the live primary speaker via PrimarySpeakerResolver.
        Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> primary =
                primarySpeakerResolver.resolve(speaker);
        String recipientEmail = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::email)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
        if (recipientEmail == null) {
            log.warn("Skipping acceptance confirmation: speaker pool {} has no resolvable "
                    + "primary speaker email", speaker.getId());
            return;
        }
        String recipientName = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::fullName)
                .filter(n -> !n.isEmpty())
                .orElseGet(speaker::getSpeakerName);

        try {
            log.info("Sending acceptance confirmation email to: {} for event: {}",
                    LoggingUtils.maskEmail(recipientEmail), event.getEventCode());

            // Default to German locale if not specified
            Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;

            // Convert event date to Swiss timezone
            ZonedDateTime eventDateTime = event.getDate().atZone(SWISS_ZONE);

            // Load and populate email template (html + subject)
            EmailContent content = loadEmailTemplate(
                    emailLocale,
                    speaker,
                    event,
                    eventDateTime,
                    recipientName
            );

            // Story 10.32: CC speaker's additional emails (empty list = unchanged behaviour)
            java.util.List<String> cc = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::additionalEmails)
                    .orElse(java.util.Collections.emptyList());
            // Story 16.0 AC1: the speaker's answer reaches the moderator, not replies@.
            MailContext context = MailContext.individual(event.getEventCode(), MailContext.Audience.SPEAKERS,
                    "speaker-acceptance");
            emailService.sendHtmlEmail(
                    context,
                    recipientEmail,
                    cc,
                    content.subject(),
                    content.html(),
                    moderatorReplyAddress.forEvent(event)
            );

            log.info("Acceptance confirmation email sent successfully to: {} (ccCount={})",
                    LoggingUtils.maskEmail(recipientEmail), cc.size());

        } catch (Exception e) {
            log.error("Failed to send acceptance confirmation email to: {}",
                    LoggingUtils.maskEmail(recipientEmail), e);
            // Don't re-throw - email failure shouldn't block acceptance process
        }
    }

    private record EmailContent(String html, String subject) {}

    /**
     * Load and populate email template with speaker/event data.
     */
    private EmailContent loadEmailTemplate(
            Locale locale,
            SpeakerPool speaker,
            Event event,
            ZonedDateTime eventDateTime,
            String speakerDisplayName
    ) {
        String localeStr = locale.getLanguage();

        // Story 11.E.3 D1 / 11.F.1 Phase F: speakers now authenticate via Cognito; the
        // SPA routes use eventCode in the path and pick up the Bearer from the session.
        // The dedicated per-event profile route was removed (Story 11.C.1 consolidated
        // profile editing into CUMS /users/me endpoints); the profile link block is gone
        // from the templates.
        String profileUrl = "";
        String contentUrl = baseUrl + "/speaker-portal/content/" + event.getEventCode();
        String dashboardLink = baseUrl + "/speaker-portal/dashboard";

        // Get session details if assigned
        String sessionTitle = "";
        if (speaker.getSessionId() != null) {
            Session session = sessionRepository.findById(speaker.getSessionId()).orElse(null);
            if (session != null) {
                sessionTitle = session.getTitle() != null ? session.getTitle() : "";
            }
        }

        Map<String, String> variables = mailPipeline.variables().event(event, localeStr)
                .put(MailVariable.SPEAKER_NAME, speakerDisplayName)
                // Story 16.11: format kept until the owner-decided change (separate commit)
                .put(MailVariable.EVENT_DATE, eventDateTime.format(DATE_FORMATTER))
                // "Uhr" in English too: open unification decision (Story 16.11)
                .put(MailVariable.EVENT_TIME, eventDateTime.format(TIME_FORMATTER) + " Uhr")
                .put(MailVariable.SESSION_TITLE, sessionTitle)
                .put(MailVariable.PROFILE_URL, profileUrl)
                .put(MailVariable.CONTENT_URL, contentUrl)
                .put(MailVariable.DASHBOARD_LINK, dashboardLink)
                .put(MailVariable.CONTENT_DEADLINE, speaker.getContentDeadline() != null
                        ? speaker.getContentDeadline().format(DATE_FORMATTER) : "")
                .asMap();

        RenderedMail mail = mailPipeline.render(mailPipeline.template("speaker-acceptance", localeStr), variables);
        String html = mail.html();
        String subject = mail.subject() != null ? mail.subject()
                : localeStr.equals("de")
                        ? "Bestätigung Ihrer Teilnahme - " + event.getTitle()
                        : "Speaker Confirmation - " + event.getTitle();
        return new EmailContent(html, subject);
    }
}
