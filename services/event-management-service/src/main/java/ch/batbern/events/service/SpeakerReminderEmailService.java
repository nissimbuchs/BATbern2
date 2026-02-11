package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

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

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    @Value("${app.email.organizer-name:BATbern Team}")
    private String organizerName;

    @Value("${app.email.organizer-email:events@batbern.ch}")
    private String organizerEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    /**
     * Send a reminder email to a speaker.
     *
     * @param speaker the speaker pool entry
     * @param event the event
     * @param reminderType RESPONSE or CONTENT
     * @param tier TIER_1, TIER_2, or TIER_3
     * @param deadline the deadline date
     * @param portalToken VIEW token for dashboard link
     * @param locale email language (defaults to German)
     */
    public void sendReminderEmail(
            SpeakerPool speaker,
            Event event,
            String reminderType,
            String tier,
            LocalDate deadline,
            String portalToken,
            Locale locale
    ) {
        try {
            Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;

            String htmlBody = loadReminderTemplate(
                    emailLocale, speaker, event, reminderType, tier, deadline, portalToken);

            String subject = buildSubject(emailLocale, reminderType, tier, event.getTitle());

            emailService.sendHtmlEmail(speaker.getEmail(), subject, htmlBody);

            log.info("Reminder email sent: type={}, tier={}, speaker={}, event={}",
                    reminderType, tier, LoggingUtils.maskEmail(speaker.getEmail()), event.getEventCode());

        } catch (Exception e) {
            log.error("Failed to send reminder email: type={}, tier={}, speaker={}",
                    reminderType, tier, LoggingUtils.maskEmail(speaker.getEmail()), e);
            throw new RuntimeException("Failed to send reminder email", e);
        }
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

    private String loadReminderTemplate(
            Locale locale,
            SpeakerPool speaker,
            Event event,
            String reminderType,
            String tier,
            LocalDate deadline,
            String portalToken
    ) {
        try {
            String lang = locale.getLanguage().equals("de") ? "de" : "en";
            String type = reminderType.toLowerCase();
            String tierNum = tier.toLowerCase().replace("_", "");

            // e.g., "email-templates/speaker-reminder-response-tier1-en.html"
            String templateName = String.format("email-templates/speaker-reminder-%s-%s-%s.html",
                    type, tierNum, lang);

            ClassPathResource resource = new ClassPathResource(templateName);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            ZonedDateTime eventDateTime = event.getDate().atZone(SWISS_ZONE);
            long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
            String portalLink = baseUrl + "/speaker-portal/dashboard?token=" + portalToken;

            Map<String, String> variables = Map.ofEntries(
                    Map.entry("speakerName", speaker.getSpeakerName()),
                    Map.entry("eventTitle", event.getTitle()),
                    Map.entry("eventDate", eventDateTime.format(DATE_FORMATTER)),
                    Map.entry("deadline", deadline.format(DATE_FORMATTER)),
                    Map.entry("daysRemaining", String.valueOf(Math.max(0, daysRemaining))),
                    Map.entry("portalLink", portalLink),
                    Map.entry("organizerName", organizerName),
                    Map.entry("organizerEmail", organizerEmail),
                    Map.entry("currentYear", String.valueOf(java.time.Year.now().getValue()))
            );

            return emailService.replaceVariables(template, variables);

        } catch (IOException e) {
            log.error("Failed to load reminder template: type={}, tier={}, locale={}",
                    reminderType, tier, locale, e);
            throw new RuntimeException("Failed to load reminder email template", e);
        }
    }
}
