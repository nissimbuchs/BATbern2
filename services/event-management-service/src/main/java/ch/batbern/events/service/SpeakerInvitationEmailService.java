package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.mailcenter.render.ModeratorReplyAddress;
import ch.batbern.events.mailcenter.render.MailContent;
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
 * Service for sending speaker invitation emails.
 *
 * <p>Story 6.1b (original): magic-link-based accept/decline + dashboard tokens.
 * <p>Story 11.E.2 (this story): rewrite to the Cognito-flow payload — single login URL +
 * temporary password block (or "use existing password" branch). The magic-link parameters
 * are removed; the dedicated Cognito-credential issuance is delegated to CUMS.
 *
 * <p>Features:
 * - i18n support (de + en only per CLAUDE.md §Localization — narrowed scope; other locales fall back to en/de)
 * - HTML-only templates (per Story 11.E.2 Resolved Q#3 — no .txt parity)
 * - Cognito login URL + temporary password (FRESH) or "use existing password" (CONFIRMED)
 * - Async sending (non-blocking)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationEmailService {

    private final EmailService emailService;
    private final SessionRepository sessionRepository;
    private final MailPipeline mailPipeline;
    // Phase B of the post-Epic-11 cleanup (2026-05-21): the linked User's identity is
    // now resolved through session_users + UserApiClient (PrimarySpeakerResolver) so
    // that organizer reassignments on the Sessions tab take effect on the next
    // invitation. Previously this service read speaker_pool.username/email directly —
    // those columns went stale at reassign time.
    private final PrimarySpeakerResolver primarySpeakerResolver;
    private final ModeratorReplyAddress moderatorReplyAddress;

    @Value("${app.cognito.temp-password-validity-days:14}")
    private int tempPasswordValidityDays;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    /**
     * Truthy sentinel for the {@code {{#useExistingPassword}}} positive Mustache flag.
     * Any non-empty string would work; {@code "yes"} is human-readable for log-trace clarity.
     * Paired contract with {@link ch.batbern.shared.service.EmailService#replaceVariables}.
     */
    private static final String MUSTACHE_TRUTHY = "yes";

    /**
     * Send speaker invitation email asynchronously.
     *
     * <p>Story 11.E.2 (AC8) — new signature replacing the magic-link variant.
     *
     * @param speaker     the speaker pool entry
     * @param event       the event
     * @param loginUrl    speaker-portal login URL (e.g. {@code https://batbern.ch/login})
     * @param credentials Cognito-credential response from CUMS — action + optional temp password
     * @param locale      preferred language (defaults to German)
     */
    @Async
    public void sendInvitationEmail(
            SpeakerPool speaker,
            Event event,
            String loginUrl,
            InvitationCredentialsResponse credentials,
            Locale locale
    ) {
        // Phase B: recipient routing via PrimarySpeakerResolver (session_users + CUMS),
        // not the stale speaker_pool.email column. Resolve once at the top so the email
        // body, recipient header, and logs all agree on the same address.
        Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> primary =
                primarySpeakerResolver.resolve(speaker);
        String recipientEmail = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::email)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
        if (recipientEmail == null) {
            log.error("Cannot send invitation: speaker pool {} has no resolvable primary speaker "
                    + "email (session not yet linked, no PRIMARY_SPEAKER session_user, or CUMS degraded). "
                    + "The READY→INVITED transition should have provisioned this — investigate.",
                    speaker.getId());
            return;
        }

        try {
            log.info("Sending invitation email to: {} for event: {} (action={})",
                    LoggingUtils.maskEmail(recipientEmail), event.getEventCode(),
                    credentials != null ? credentials.getAction() : "null");

            // Default to German locale if not specified.
            Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;

            // Convert event date to Swiss timezone.
            ZonedDateTime eventDateTime = event.getDate().atZone(SWISS_ZONE);

            // Load + populate the email template (html + subject). The recipient
            // bundle carries the live primary-speaker identity + resolved email so
            // the template uses session_users data, not stale pool columns.
            RecipientContext recipient = new RecipientContext(primary.orElse(null), recipientEmail);
            EmailContent content = loadEmailTemplate(
                    emailLocale,
                    speaker,
                    event,
                    eventDateTime,
                    loginUrl,
                    credentials,
                    recipient
            );

            // Send email (no attachments for invitation; HTML only per Q#3).
            // Story 10.32: CC the speaker's additional emails so the invite reaches
            // every declared address (empty list = unchanged 3-arg behaviour).
            java.util.List<String> cc = primary.map(PrimarySpeakerResolver.PrimarySpeakerProfile::additionalEmails)
                    .orElse(java.util.Collections.emptyList());
            // Story 16.0 AC1: the speaker's answer reaches the moderator, not replies@.
            // The temporary password must not reach the mail record (Story 11.E.2 keeps it out of storage).
            MailContext context = MailContext.individual(event.getEventCode(), MailContext.Audience.SPEAKERS,
                    "speaker-invitation").withSecrets(credentials != null ? credentials.getTemporaryPassword() : null);
            emailService.sendHtmlEmail(
                    context,
                    recipientEmail,
                    cc,
                    content.subject(),
                    content.html(),
                    moderatorReplyAddress.forEvent(event)
            );

            log.info("Invitation email sent successfully to: {} (ccCount={})",
                    LoggingUtils.maskEmail(recipientEmail), cc.size());

        } catch (Exception e) {
            log.error("Failed to send invitation email to: {}",
                    LoggingUtils.maskEmail(recipientEmail), e);
            // Don't re-throw — email failure shouldn't block the workflow transition.
        }
    }

    private record EmailContent(String html, String subject) {}

    /**
     * Bundles the resolved primary speaker profile + recipient email so the template
     * loader sees one parameter for "who we're addressing" instead of two. Keeps the
     * loadEmailTemplate signature under the Checkstyle 7-parameter limit.
     */
    private record RecipientContext(
            PrimarySpeakerResolver.PrimarySpeakerProfile primary,
            String email) {}

    /**
     * Load and populate the email template with speaker / event / Cognito-flow data.
     */
    private EmailContent loadEmailTemplate(
            Locale locale,
            SpeakerPool speaker,
            Event event,
            ZonedDateTime eventDateTime,
            String loginUrl,
            InvitationCredentialsResponse credentials,
            RecipientContext recipient
    ) {
        PrimarySpeakerResolver.PrimarySpeakerProfile primary = recipient.primary();
        String recipientEmail = recipient.email();
        // Determine template file based on locale (de + en officially supported per CLAUDE.md
        // §Localization; other locales fall back to en at the repository layer).
        String localeStr = locale.getLanguage();

        // Story 11.E.2 review patch (E10): never send an empty invitation after a Cognito password
        // rotation (speaker locked out with no recovery). A missing template throws instead, so the
        // caller's catch logs it and no mail leaves SES.
        MailContent content = mailPipeline.template("speaker-invitation", localeStr);
        if (content.bodyHtml().isBlank()) {
            throw new IllegalStateException("Missing email template: key=speaker-invitation locale=" + localeStr);
        }

        // Get session details if assigned.
        String sessionTitle = "";
        String sessionDescription = "";
        if (speaker.getSessionId() != null) {
            Session session = sessionRepository.findById(speaker.getSessionId()).orElse(null);
            if (session != null) {
                sessionTitle = session.getTitle() != null ? session.getTitle() : "";
                sessionDescription = session.getDescription() != null ? session.getDescription() : "";
            }
        }

        // Story 11.E.2: branch on the action discriminator to render the right block.
        // EmailService.replaceVariables supports positive Mustache conditionals
        // {{#var}}...{{/var}} only — empty value → block removed; non-empty → tags removed
        // (content kept). We expose TWO positive conditional variables, exactly one of
        // which is non-empty per call:
        //   - {{#temporaryPassword}}...{{/temporaryPassword}} → FRESH_TEMP_PASSWORD branch
        //   - {{#useExistingPassword}}...{{/useExistingPassword}} → USE_EXISTING_PASSWORD branch
        // This pattern is semantically equivalent to the AC8 {{^temporaryPassword}} inverted
        // conditional but uses only the conditional shape the shared-kernel replacer
        // already supports.
        boolean isFresh = credentials != null
                && credentials.getAction() == InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD
                && credentials.getTemporaryPassword() != null;
        String temporaryPasswordValue = isFresh ? credentials.getTemporaryPassword() : "";
        String useExistingPasswordFlag = isFresh ? "" : MUSTACHE_TRUTHY;

        // Epic 11 bug fix 2026-05-19 / Phase B: the greeting uses the live User name
        // (PrimarySpeakerResolver: session_users ⨝ CUMS), falling back to the brainstorm-stage
        // speaker name when CUMS degrades.
        String speakerNameDisplay = primary != null && !primary.fullName().isEmpty()
                ? primary.fullName()
                : nullToEmpty(speaker.getSpeakerName());
        String hostBase = deriveHostBase(loginUrl);
        // This mail HTML-escapes its values (temporary passwords contain & < > "); the other mails
        // do not. Kept as is in Story 16.11.
        Map<String, String> variables = mailPipeline.variables().event(event, localeStr)
                .put(MailVariable.SPEAKER_NAME, escapeHtml(speakerNameDisplay))
                .put(MailVariable.EVENT_TITLE, escapeHtml(nullToEmpty(event.getTitle())))
                // "Uhr" in English too: open unification decision (Story 16.11)
                .put(MailVariable.EVENT_TIME, eventDateTime.format(TIME_FORMATTER) + " Uhr")
                .put(MailVariable.VENUE_NAME, escapeHtml(event.getVenueName() != null ? event.getVenueName() : "TBA"))
                .put(MailVariable.VENUE_ADDRESS,
                        escapeHtml(event.getVenueAddress() != null ? event.getVenueAddress() : "TBA"))
                .put(MailVariable.SESSION_TITLE, escapeHtml(sessionTitle))
                .put(MailVariable.SESSION_DESCRIPTION, escapeHtml(sessionDescription))
                .put(MailVariable.LOGIN_URL, loginUrl)
                // Phase B: the live recipient email is what the speaker types into Cognito.
                .put(MailVariable.USERNAME_FOR_LOGIN, escapeHtml(nullToEmpty(recipientEmail)))
                .put(MailVariable.TEMPORARY_PASSWORD, escapeHtml(temporaryPasswordValue))
                .put(MailVariable.USE_EXISTING_PASSWORD, useExistingPasswordFlag)
                .put(MailVariable.TEMP_PASSWORD_VALIDITY_DAYS, tempPasswordValidityDays)
                .put(MailVariable.RESPONSE_DEADLINE, speaker.getResponseDeadline() != null
                        ? speaker.getResponseDeadline().format(DATE_FORMATTER) : "")
                .put(MailVariable.CONTENT_DEADLINE, speaker.getContentDeadline() != null
                        ? speaker.getContentDeadline().format(DATE_FORMATTER) : "")
                // Story 11.E.2: links from the login URL's host (best effort)
                .put(MailVariable.EVENT_URL, hostBase + "/events/" + event.getEventCode())
                .put(MailVariable.SUPPORT_URL, hostBase + "/support")
                .put(MailVariable.LOGO_URL, hostBase + "/BATbern_white_logo.png")
                .asMap();

        RenderedMail mail = mailPipeline.renderNotice(content, variables);
        String html = mail.html();
        String subject = mail.subject() != null ? mail.subject()
                : localeStr.equals("de")
                        ? "Einladung als Referent - " + event.getTitle()
                        : "Speaker Invitation - " + event.getTitle();
        return new EmailContent(html, subject);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * Minimal HTML escape for the 5 entity chars commonly emitted by user-supplied or
     * generator-produced values (speaker name, email, password, session title/description).
     * Avoids pulling in a heavier escaper for a hot path.
     */
    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Extracts the scheme + host prefix from {@code loginUrl} so that ancillary links
     * (event URL, support, logo) point to the same host. Falls back to the raw {@code loginUrl}
     * if parsing fails.
     */
    private static String deriveHostBase(String loginUrl) {
        if (loginUrl == null || loginUrl.isBlank()) {
            return "https://batbern.ch";
        }
        try {
            java.net.URI uri = java.net.URI.create(loginUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
            String host = uri.getHost() != null ? uri.getHost() : "batbern.ch";
            int port = uri.getPort();
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        } catch (IllegalArgumentException e) {
            return "https://batbern.ch";
        }
    }
}
