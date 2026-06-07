package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private final UserApiClient userApiClient;

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
                        locale, LoggingUtils.maskEmail(registration.getAttendeeEmail()));
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

            // Story 10.32 (P1-9 from 2026-05-22 review): CC attendee's additional emails so the
            // deregistration link reaches every address they declared. Anonymous registrants
            // (no attendeeUsername) and CUMS lookup failures degrade to primary-only.
            List<String> cc = additionalEmailsFor(registration.getAttendeeUsername());
            emailService.sendHtmlEmail(registration.getAttendeeEmail(), cc, subject, html);
            log.info("Deregistration link email sent to {} for event {} (ccCount={})",
                    LoggingUtils.maskEmail(registration.getAttendeeEmail()),
                    event.getEventCode(),
                    cc.size());
        } catch (Exception e) {
            log.error("Failed to send deregistration link email to {}: {}",
                    LoggingUtils.maskEmail(registration.getAttendeeEmail()), e.getMessage(), e);
        }
    }

    /**
     * Story 10.32 (P1-9) — flatten the attendee's additional emails into a CC list.
     * Returns an empty list for anonymous registrants (no username on file) and
     * for any CUMS lookup failure — the deregistration link still reaches the
     * primary attendee email so this is fail-soft by design.
     */
    private List<String> additionalEmailsFor(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        try {
            UserResponse attendee = userApiClient.getUserByUsername(username);
            if (attendee == null || attendee.getAdditionalEmails() == null
                    || attendee.getAdditionalEmails().isEmpty()) {
                return List.of();
            }
            return attendee.getAdditionalEmails().stream()
                    .map(ch.batbern.events.dto.generated.users.AdditionalEmail::getEmail)
                    .filter(java.util.Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .toList();
        } catch (UserNotFoundException e) {
            log.warn("Cannot enrich deregistration CC — user not found: {}", username);
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch additional emails for user {} ({}); sending primary only",
                    username, e.getMessage());
            return List.of();
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
