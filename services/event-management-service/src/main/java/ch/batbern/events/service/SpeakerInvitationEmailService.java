package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
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
import java.util.HashMap;
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
    private final EmailTemplateService emailTemplateService;
    // Phase B of the post-Epic-11 cleanup (2026-05-21): the linked User's identity is
    // now resolved through session_users + UserApiClient (PrimarySpeakerResolver) so
    // that organizer reassignments on the Sessions tab take effect on the next
    // invitation. Previously this service read speaker_pool.username/email directly —
    // those columns went stale at reassign time.
    private final PrimarySpeakerResolver primarySpeakerResolver;

    @Value("${app.email.organizer-name:BATbern Team}")
    private String organizerName;

    @Value("${app.email.organizer-email:events@batbern.ch}")
    private String organizerEmail;

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
            emailService.sendHtmlEmail(
                    recipientEmail,
                    cc,
                    content.subject(),
                    content.html()
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
        String templateName = localeStr.equals("de")
                ? "email-templates/speaker-invitation-de.html"
                : "email-templates/speaker-invitation-en.html";

        // 1. Try DB lookup; fall back to classpath if absent (Story 10.2).
        String template = loadHtmlContent("speaker-invitation", localeStr, templateName);

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

        Map<String, String> variables = new HashMap<>();
        // Epic 11 bug fix 2026-05-19 — resolve the linked User's first+last name for
        // the email greeting. `speaker.getSpeakerName()` carries the brainstorm-stage
        // placeholder (e.g. "Testreferent2"); by READY+ the real User exists in CUMS
        // and that's the name the recipient expects to see.
        // Fallback chain: linked-User name → brainstorm name → empty. The fallback
        // protects against (1) UserApiClient transient failures and (2) the
        // theoretical out-of-order case where invitation is sent before promote
        // populates `speaker_pool.username` (should not happen via the workflow
        // service but defensive in case of direct DB manipulation).
        // Phase B: PrimarySpeakerResolver gives us the live User name (session_users ⨝
        // CUMS). Invitations only send post-READY (session always exists), so primary
        // should never be null here — but if CUMS degrades and resolver returns an
        // empty profile, fall back to the brainstorm-stage speaker name on pool.
        String speakerNameDisplay = primary != null && !primary.fullName().isEmpty()
                ? primary.fullName()
                : nullToEmpty(speaker.getSpeakerName());
        variables.put("speakerName", escapeHtml(speakerNameDisplay));
        variables.put("eventTitle", escapeHtml(nullToEmpty(event.getTitle())));
        variables.put("eventDate", eventDateTime.format(DATE_FORMATTER));
        variables.put("eventTime", eventDateTime.format(TIME_FORMATTER) + " Uhr");
        variables.put("venueName", escapeHtml(event.getVenueName() != null ? event.getVenueName() : "TBA"));
        variables.put("venueAddress", escapeHtml(event.getVenueAddress() != null ? event.getVenueAddress() : "TBA"));
        variables.put("sessionTitle", escapeHtml(sessionTitle));
        variables.put("sessionDescription", escapeHtml(sessionDescription));
        // Story 11.E.2 Cognito-flow variables. temporaryPassword may contain HTML-significant
        // chars from PasswordGenerator's symbol alphabet (& < > ") — escape both values so the
        // rendered email is well-formed and the password is preserved verbatim on copy/paste.
        variables.put("loginUrl", loginUrl);
        // Phase B: usernameForLogin must be the live recipient email — that's what the
        // speaker types into Cognito at login. Falls back to the recipient address resolved
        // by PrimarySpeakerResolver at the top of sendInvitationEmail.
        variables.put("usernameForLogin", escapeHtml(nullToEmpty(recipientEmail)));
        variables.put("temporaryPassword", escapeHtml(temporaryPasswordValue));
        variables.put("useExistingPassword", useExistingPasswordFlag);
        variables.put("tempPasswordValidityDays", String.valueOf(tempPasswordValidityDays));
        // Existing deadline + organizer + branding variables (unchanged).
        variables.put("responseDeadline", speaker.getResponseDeadline() != null
                ? speaker.getResponseDeadline().format(DATE_FORMATTER)
                : "");
        variables.put("contentDeadline", speaker.getContentDeadline() != null
                ? speaker.getContentDeadline().format(DATE_FORMATTER)
                : "");
        variables.put("organizerName", organizerName);
        variables.put("organizerEmail", organizerEmail);
        // Story 11.E.2: derive a public event URL from the login URL host (best-effort; the
        // existing classpath templates retain {{eventUrl}}). Use the loginUrl host portion.
        variables.put("eventUrl", deriveHostBase(loginUrl) + "/events/" + event.getEventCode());
        variables.put("supportUrl", deriveHostBase(loginUrl) + "/support");
        variables.put("currentYear", String.valueOf(java.time.Year.now().getValue()));
        variables.put("logoUrl", deriveHostBase(loginUrl) + "/BATbern_white_logo.svg");

        String html = emailService.replaceVariables(template, variables);
        String subject = emailTemplateService.resolveSubject("speaker-invitation", localeStr)
                .map(s -> emailService.replaceVariables(s, variables))
                .orElseGet(() -> localeStr.equals("de")
                        ? "Einladung als Referent - " + event.getTitle()
                        : "Speaker Invitation - " + event.getTitle());
        return new EmailContent(html, subject);
    }

    /**
     * Loads HTML content from DB (with optional layout merge) or falls back to classpath.
     * Story 10.2 AC1: DB-first template loading.
     */
    private String loadHtmlContent(String templateKey, String localeStr, String classpathFallback) {
        Optional<EmailTemplate> dbTemplate = emailTemplateService.findByKeyAndLocale(templateKey, localeStr);
        if (dbTemplate.isPresent()) {
            String contentHtml = dbTemplate.get().getHtmlBody();
            String layoutKey = dbTemplate.get().getLayoutKey();
            if (layoutKey != null) {
                return emailTemplateService.mergeWithLayout(contentHtml, layoutKey, localeStr);
            }
            return contentHtml;
        }
        // Classpath fallback.
        try {
            ClassPathResource resource = new ClassPathResource(classpathFallback);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Story 11.E.2 review patch (E10): a previous version returned "" here, which
            // caused {@link #sendInvitationEmail} to send an empty-body email after a Cognito
            // password rotation had already happened — speaker locked out with no recovery.
            // Throw instead so the caller's catch logs the failure and no email leaves SES.
            log.error("Email template not found in DB or classpath: {}/{}", templateKey, localeStr);
            throw new IllegalStateException(
                    "Missing email template: key=" + templateKey + " locale=" + localeStr, e);
        }
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
