package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Service for sending deregistration-related emails.
 * <p>
 * Story 10.12 (AC6): Sends deregistration link email when attendee requests cancellation via email form.
 * Story 10.12 (AC7): The registration-confirmation-{locale} templates include a cancellation link —
 *   that link update is handled in RegistrationEmailService (same template loading path).
 * <p>
 * Template loading: DB-first (Story 10.2), classpath fallback.
 * Pattern: follows RegistrationEmailService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeregistrationEmailService {

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    /**
     * Send deregistration link email to the attendee.
     * Called asynchronously from DeregistrationService.deregisterByEmail().
     *
     * @param registration       The registration entity
     * @param event              The event entity
     * @param deregistrationLink Full URL to the self-service deregistration page (includes token)
     */
    // Not @Async: always called from DeregistrationService.deregisterByEmail() which is itself @Async
    public void sendDeregistrationLinkEmail(Registration registration, Event event, String deregistrationLink) {
        try {
            String locale = resolveLocale(registration.getAttendeeEmail());
            String templateKey = "deregistration-link";
            String classpathFallback = "email-templates/deregistration-link-" + locale + ".html";

            String template = loadHtmlContent(templateKey, locale, classpathFallback);
            if (template.isBlank()) {
                log.warn("No deregistration-link template found for locale '{}', skipping email to {}",
                        locale, registration.getAttendeeEmail());
                return;
            }

            ZonedDateTime eventDateTime = event.getDate().atZone(SWISS_ZONE);
            Map<String, String> variables = Map.of(
                    "recipientName", registration.getAttendeeFirstName() != null
                            ? registration.getAttendeeFirstName() : "",
                    "eventTitle", event.getTitle(),
                    "eventCode", event.getEventCode(),
                    "eventDate", eventDateTime.format(DATE_FORMATTER),
                    "deregistrationLink", deregistrationLink
            );

            String html = emailService.replaceVariables(template, variables);
            String subject = emailTemplateService.resolveSubject(templateKey, locale)
                    .map(s -> emailService.replaceVariables(s, variables))
                    .orElse("Ihre Abmeldung / Your Cancellation Request");

            emailService.sendHtmlEmail(registration.getAttendeeEmail(), subject, html);
            log.info("Deregistration link email sent to {} for event {}", registration.getAttendeeEmail(),
                    event.getEventCode());
        } catch (Exception e) {
            log.error("Failed to send deregistration link email to {}: {}",
                    registration.getAttendeeEmail(), e.getMessage(), e);
        }
    }

    /**
     * Resolve email locale. Uses "de" as default (primary BATbern audience is German-speaking).
     * A future improvement could look up the user's preferred language via UserApiClient.
     */
    private String resolveLocale(String email) {
        // TODO: resolve from user profile via UserApiClient (Story 10.12 improvement)
        return "de";
    }

    /**
     * DB-first template loading with classpath fallback (Story 10.2 pattern).
     */
    private String loadHtmlContent(String templateKey, String locale, String classpathFallback) {
        Optional<EmailTemplate> dbTemplate = emailTemplateService.findByKeyAndLocale(templateKey, locale);
        if (dbTemplate.isPresent()) {
            String contentHtml = dbTemplate.get().getHtmlBody();
            String layoutKey = dbTemplate.get().getLayoutKey();
            if (layoutKey != null) {
                return emailTemplateService.mergeWithLayout(contentHtml, layoutKey, locale);
            }
            return contentHtml;
        }
        try {
            ClassPathResource resource = new ClassPathResource(classpathFallback);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Email template not found in DB or classpath: {}/{}", templateKey, locale);
            return "";
        }
    }
}
