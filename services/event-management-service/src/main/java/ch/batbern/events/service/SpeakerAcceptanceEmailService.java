package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

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

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    @Value("${app.email.organizer-name:BATbern Team}")
    private String organizerName;

    @Value("${app.email.organizer-email:events@batbern.ch}")
    private String organizerEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    /**
     * Send acceptance confirmation email asynchronously.
     * AC9: Confirmation email with portal links
     *
     * @param speaker   the speaker pool entry
     * @param event     the event
     * @param viewToken VIEW token for portal access (30-day expiry)
     * @param locale    preferred language (defaults to German)
     */
    @Async
    public void sendAcceptanceConfirmationEmail(
            SpeakerPool speaker,
            Event event,
            String viewToken,
            Locale locale
    ) {
        sendAcceptanceConfirmationEmail(speaker, event, viewToken, locale, null);
    }

    /**
     * Story 9.2 AC5: Overload that includes temporary password for NEW Cognito accounts.
     * Pass temporaryPassword=null for EXTENDED accounts (they already have a password).
     */
    @Async
    public void sendAcceptanceConfirmationEmail(
            SpeakerPool speaker,
            Event event,
            String viewToken,
            Locale locale,
            String temporaryPassword
    ) {
        try {
            log.info("Sending acceptance confirmation email to: {} for event: {}",
                    LoggingUtils.maskEmail(speaker.getEmail()), event.getEventCode());

            // Default to German locale if not specified
            Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;

            // Convert event date to Swiss timezone
            ZonedDateTime eventDateTime = event.getDate().atZone(SWISS_ZONE);

            // Load and populate email template
            String htmlBody = loadEmailTemplate(
                    emailLocale,
                    speaker,
                    event,
                    eventDateTime,
                    viewToken,
                    temporaryPassword
            );

            // Determine subject based on locale
            String subject = emailLocale.getLanguage().equals("de")
                    ? "Bestätigung Ihrer Teilnahme - " + event.getTitle()
                    : "Speaker Confirmation - " + event.getTitle();

            // Send email
            emailService.sendHtmlEmail(
                    speaker.getEmail(),
                    subject,
                    htmlBody
            );

            log.info("Acceptance confirmation email sent successfully to: {}",
                    LoggingUtils.maskEmail(speaker.getEmail()));

        } catch (Exception e) {
            log.error("Failed to send acceptance confirmation email to: {}",
                    LoggingUtils.maskEmail(speaker.getEmail()), e);
            // Don't re-throw - email failure shouldn't block acceptance process
        }
    }

    /**
     * Load and populate email template with speaker/event data.
     */
    private String loadEmailTemplate(
            Locale locale,
            SpeakerPool speaker,
            Event event,
            ZonedDateTime eventDateTime,
            String viewToken,
            String temporaryPassword
    ) {
        try {
            // Determine template file based on locale
            String templateName = locale.getLanguage().equals("de")
                    ? "email-templates/speaker-acceptance-de.html"
                    : "email-templates/speaker-acceptance-en.html";

            ClassPathResource resource = new ClassPathResource(templateName);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Build portal URLs with VIEW token
            String profileUrl = baseUrl + "/speaker-portal/profile?token=" + viewToken;
            String contentUrl = baseUrl + "/speaker-portal/content?token=" + viewToken;
            String dashboardLink = baseUrl + "/speaker-portal/dashboard?token=" + viewToken;

            // Get session details if assigned
            String sessionTitle = "";
            if (speaker.getSessionId() != null) {
                Session session = sessionRepository.findById(speaker.getSessionId()).orElse(null);
                if (session != null) {
                    sessionTitle = session.getTitle() != null ? session.getTitle() : "";
                }
            }

            // Prepare template variables
            Map<String, String> variables = Map.ofEntries(
                    Map.entry("speakerName", speaker.getSpeakerName()),
                    Map.entry("eventTitle", event.getTitle()),
                    Map.entry("eventCode", event.getEventCode()),
                    Map.entry("eventDate", eventDateTime.format(DATE_FORMATTER)),
                    Map.entry("eventTime", eventDateTime.format(TIME_FORMATTER) + " Uhr"),
                    Map.entry("venueName", event.getVenueName() != null ? event.getVenueName() : "TBA"),
                    Map.entry("venueAddress", event.getVenueAddress() != null ? event.getVenueAddress() : "TBA"),
                    Map.entry("sessionTitle", sessionTitle),
                    Map.entry("profileUrl", profileUrl),
                    Map.entry("contentUrl", contentUrl),
                    Map.entry("dashboardLink", dashboardLink),
                    Map.entry("contentDeadline", speaker.getContentDeadline() != null
                            ? speaker.getContentDeadline().format(DATE_FORMATTER)
                            : ""),
                    Map.entry("organizerName", organizerName),
                    Map.entry("organizerEmail", organizerEmail),
                    Map.entry("eventUrl", baseUrl + "/events/" + event.getEventCode()),
                    Map.entry("supportUrl", baseUrl + "/support"),
                    Map.entry("currentYear", String.valueOf(java.time.Year.now().getValue())),
                    // Story 9.2 AC5: temporary password for NEW Cognito accounts (empty = not shown)
                    Map.entry("temporaryPassword", temporaryPassword != null ? temporaryPassword : ""),
                    Map.entry("speakerEmail", speaker.getEmail() != null ? speaker.getEmail() : "")
            );

            // Replace template variables
            return emailService.replaceVariables(template, variables);

        } catch (IOException e) {
            log.error("Failed to load email template for locale: {}", locale, e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }
}
