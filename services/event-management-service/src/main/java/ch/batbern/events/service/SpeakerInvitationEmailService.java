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
 * Service for sending speaker invitation emails.
 * Story 6.1b: Speaker Invitation System (AC3, AC4)
 *
 * Features:
 * - i18n support (German/English)
 * - HTML email templates with BATbern branding
 * - Magic link integration for accept/decline
 * - Async sending (non-blocking)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationEmailService {

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
     * Send speaker invitation email asynchronously.
     * AC3: Email with magic links for accept/decline
     * AC4: i18n support (German/English)
     *
     * @param speaker the speaker pool entry
     * @param event the event
     * @param respondToken magic link token for response (accept/decline)
     * @param dashboardToken magic link token for dashboard access (reusable VIEW token)
     * @param locale preferred language (defaults to German)
     */
    @Async
    public void sendInvitationEmail(
            SpeakerPool speaker,
            Event event,
            String respondToken,
            String dashboardToken,
            Locale locale
    ) {
        try {
            log.info("Sending invitation email to: {} for event: {}",
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
                    respondToken,
                    dashboardToken
            );

            // Determine subject based on locale
            String subject = emailLocale.getLanguage().equals("de")
                    ? "Einladung als Referent - " + event.getTitle()
                    : "Speaker Invitation - " + event.getTitle();

            // Send email (no attachments for invitation)
            emailService.sendHtmlEmail(
                    speaker.getEmail(),
                    subject,
                    htmlBody
            );

            log.info("Invitation email sent successfully to: {}", LoggingUtils.maskEmail(speaker.getEmail()));

        } catch (Exception e) {
            log.error("Failed to send invitation email to: {}", LoggingUtils.maskEmail(speaker.getEmail()), e);
            // Don't re-throw - email failure shouldn't block invitation process
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
            String respondToken,
            String dashboardToken
    ) {
        try {
            // Determine template file based on locale
            String templateName = locale.getLanguage().equals("de")
                    ? "email-templates/speaker-invitation-de.html"
                    : "email-templates/speaker-invitation-en.html";

            ClassPathResource resource = new ClassPathResource(templateName);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Build magic link URLs
            String acceptLink = baseUrl + "/speaker-portal/respond?token=" + respondToken + "&action=accept";
            String declineLink = baseUrl + "/speaker-portal/respond?token=" + respondToken + "&action=decline";
            String dashboardLink = baseUrl + "/speaker-portal/dashboard?token=" + dashboardToken;

            // Get session details if assigned
            String sessionTitle = "";
            String sessionDescription = "";
            if (speaker.getSessionId() != null) {
                Session session = sessionRepository.findById(speaker.getSessionId()).orElse(null);
                if (session != null) {
                    sessionTitle = session.getTitle() != null ? session.getTitle() : "";
                    sessionDescription = session.getDescription() != null ? session.getDescription() : "";
                }
            }

            // Prepare template variables
            Map<String, String> variables = Map.ofEntries(
                    Map.entry("speakerName", speaker.getSpeakerName()),
                    Map.entry("eventTitle", event.getTitle()),
                    Map.entry("eventDate", eventDateTime.format(DATE_FORMATTER)),
                    Map.entry("eventTime", eventDateTime.format(TIME_FORMATTER) + " Uhr"),
                    Map.entry("venueName", event.getVenueName() != null ? event.getVenueName() : "TBA"),
                    Map.entry("venueAddress", event.getVenueAddress() != null ? event.getVenueAddress() : "TBA"),
                    Map.entry("sessionTitle", sessionTitle),
                    Map.entry("sessionDescription", sessionDescription),
                    Map.entry("acceptLink", acceptLink),
                    Map.entry("declineLink", declineLink),
                    Map.entry("dashboardLink", dashboardLink),
                    Map.entry("responseDeadline", speaker.getResponseDeadline() != null
                            ? speaker.getResponseDeadline().format(DATE_FORMATTER)
                            : ""),
                    Map.entry("contentDeadline", speaker.getContentDeadline() != null
                            ? speaker.getContentDeadline().format(DATE_FORMATTER)
                            : ""),
                    Map.entry("organizerName", organizerName),
                    Map.entry("organizerEmail", organizerEmail),
                    Map.entry("eventUrl", baseUrl + "/events/" + event.getEventCode()),
                    Map.entry("supportUrl", baseUrl + "/support"),
                    Map.entry("currentYear", String.valueOf(java.time.Year.now().getValue()))
            );

            // Replace template variables
            return emailService.replaceVariables(template, variables);

        } catch (IOException e) {
            log.error("Failed to load email template for locale: {}", locale, e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }
}
