package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.IcsCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service for sending registration confirmation emails.
 *
 * Features:
 * - i18n support (German/English)
 * - HTML email templates
 * - Calendar file (.ics) attachment
 * - QR code ticket link
 * - Async sending (non-blocking)
 *
 * Story 2.2a Task B12: Email confirmation for anonymous registrations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationEmailService {

    private final EmailService emailService;
    private final IcsCalendarService icsCalendarService;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    @Value("${app.email.organizer-name:BATbern Team}")
    private String organizerName;

    @Value("${app.email.organizer-email:events@batbern.ch}")
    private String organizerEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final java.time.ZoneId SWISS_ZONE = java.time.ZoneId.of("Europe/Zurich");

    /**
     * Send registration confirmation email asynchronously with i18n support.
     *
     * @param registration Registration entity
     * @param userProfile User profile DTO
     * @param event Event entity
     * @param confirmationToken JWT token for email confirmation (Story 4.1.5c)
     * @param locale User's preferred locale (defaults to German if null)
     */
    @Async
    public void sendRegistrationConfirmation(
            Registration registration,
            UserResponse userProfile,
            Event event,
            String confirmationToken,
            Locale locale
    ) {
        try {
            log.info("Sending registration confirmation email to: {} for event: {}",
                    userProfile.getEmail(), event.getEventCode());

            // Default to German locale if not specified
            Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;

            // Convert event date from Instant to ZonedDateTime (Swiss timezone)
            ZonedDateTime eventDateTime = event.getDate().atZone(SWISS_ZONE);

            // Load email template (i18n)
            String htmlBody = loadEmailTemplate(emailLocale, registration, userProfile, event, eventDateTime, confirmationToken);

            // Generate calendar file (.ics)
            byte[] icsFile = generateCalendarFile(event, eventDateTime);

            // Send email with attachment
            EmailService.EmailAttachment calendarAttachment = new EmailService.EmailAttachment(
                    "event.ics",
                    icsFile,
                    "text/calendar; charset=utf-8; method=REQUEST"
            );

            String subject = emailLocale.getLanguage().equals("de")
                    ? "Registrierungsbestätigung - " + event.getTitle()
                    : "Registration Confirmation - " + event.getTitle();

            emailService.sendHtmlEmailWithAttachments(
                    userProfile.getEmail(),
                    subject,
                    htmlBody,
                    List.of(calendarAttachment)
            );

            log.info("Registration confirmation email sent successfully to: {}", userProfile.getEmail());

        } catch (Exception e) {
            log.error("Failed to send registration confirmation email to: {}", userProfile.getEmail(), e);
            // Don't re-throw - email failure shouldn't block registration
        }
    }

    /**
     * Load and populate email template with user/event data.
     */
    private String loadEmailTemplate(Locale locale, Registration registration, UserResponse userProfile, Event event, ZonedDateTime eventDateTime, String confirmationToken) {
        try {
            // Determine template file based on locale
            String templateName = locale.getLanguage().equals("de")
                    ? "email-templates/registration-confirmation-de.html"
                    : "email-templates/registration-confirmation-en.html";

            ClassPathResource resource = new ClassPathResource(templateName);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Prepare template variables
            // Story 4.1.5c: Use JWT confirmation token instead of registration code in URLs
            Map<String, String> variables = Map.ofEntries(
                    Map.entry("attendeeFirstName", userProfile.getFirstName()),
                    Map.entry("attendeeLastName", userProfile.getLastName()),
                    Map.entry("attendeeName", userProfile.getFirstName() + " " + userProfile.getLastName()),
                    Map.entry("eventTitle", event.getTitle()),
                    Map.entry("eventDate", eventDateTime.format(DATE_FORMATTER)),
                    Map.entry("eventTime", eventDateTime.format(TIME_FORMATTER) + " Uhr"),
                    Map.entry("venueName", event.getVenueName() != null ? event.getVenueName() : "TBA"),
                    Map.entry("venueAddress", event.getVenueAddress() != null ? event.getVenueAddress() : "TBA"),
                    Map.entry("confirmationUrl", baseUrl + "/events/" + event.getEventCode() + "/confirm-registration?token=" + confirmationToken),
                    Map.entry("createAccountUrl", baseUrl + "/auth/signup?email=" + userProfile.getEmail()),
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

    /**
     * Generate .ics calendar file for the event.
     */
    private byte[] generateCalendarFile(Event event, ZonedDateTime startDateTime) {
        // Calculate end time (assume 4-hour event if endDate not specified)
        ZonedDateTime endDateTime = startDateTime.plusHours(4);

        String eventDescription = "Berner Architekten Treffen - " + event.getTitle();

        return icsCalendarService.generateIcsFile(
                event.getTitle(),
                eventDescription,
                event.getVenueAddress() != null ? event.getVenueAddress() : "",
                startDateTime,
                endDateTime,
                organizerEmail,
                organizerName
        );
    }
}
