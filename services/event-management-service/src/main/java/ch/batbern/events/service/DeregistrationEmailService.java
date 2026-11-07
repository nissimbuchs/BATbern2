package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
    private final UserApiClient userApiClient;
    private final MailPipeline mailPipeline;

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
        sendDeregistrationLinkEmail(registration, event, deregistrationLink, registration.getAttendeeEmail());
    }

    /**
     * #1183: the same mail to {@code recipient}, the address the person typed into the by-email form,
     * when the registration was found under another of their verified addresses: the link goes where
     * they asked for it, an address proven to be theirs.
     */
    public void sendDeregistrationLinkEmail(Registration registration, Event event, String deregistrationLink,
            String recipient) {
        try {
            String locale = resolveLocale(registration.getAttendeeUsername());
            String templateKey = "deregistration-link";
            MailContent content = mailPipeline.template(templateKey, locale);
            if (content.bodyHtml().isBlank()) {
                log.warn("No deregistration-link template found for locale '{}', skipping email to {}",
                        locale, LoggingUtils.maskEmail(recipient));
                return;
            }

            Map<String, String> variables = mailPipeline.variables().event(event, locale)
                    .put(MailVariable.RECIPIENT_NAME, registration.getAttendeeFirstName())
                    .put(MailVariable.DEREGISTRATION_URL, deregistrationLink)
                    .asMap();

            RenderedMail mail = mailPipeline.renderNotice(content, variables);
            String html = mail.html();
            String subject = mail.subject() != null ? mail.subject() : "Ihre Abmeldung / Your Cancellation Request";

            List<String> cc = additionalEmailsFor(registration.getAttendeeUsername());
            MailContext context = MailContext.individual(event.getEventCode(), MailContext.Audience.REGISTRANTS,
                    templateKey).withSecrets(registration.getRegistrationCode());
            emailService.sendHtmlEmail(context, recipient, cc, subject, html, null);
            log.info("Deregistration link email sent to {} for event {} (ccCount={})",
                    LoggingUtils.maskEmail(recipient),
                    event.getEventCode(),
                    cc.size());
        } catch (Exception e) {
            log.error("Failed to send deregistration link email to {}: {}",
                    LoggingUtils.maskEmail(recipient), e.getMessage(), e);
        }
    }

    /**
     * Story 10.32 (P1-9), #1244 — the attendee's additional addresses that get a copy.
     * Returns an empty list for anonymous registrants (no username on file) and
     * for any CUMS lookup failure — the deregistration link still reaches the
     * primary attendee email so this is fail-soft by design.
     */
    private List<String> additionalEmailsFor(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        try {
            // #1244: only confirmed addresses whose owner wants a copy
            return EventMailCopies.of(userApiClient.getUserByUsername(username));
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
     * The attendee's language, from their CUMS preference: German or English (the only template
     * locales). Unknown user or failed lookup: German, the primary audience.
     */
    private String resolveLocale(String username) {
        if (username == null || username.isBlank()) {
            return "de";
        }
        return EmailLocales.fromPreference(userApiClient.getPreferredLanguage(username)).getLanguage();
    }
}
