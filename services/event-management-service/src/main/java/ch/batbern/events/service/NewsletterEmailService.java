package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.NewsletterPreviewResponse;
import ch.batbern.events.dto.NewsletterSendResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.UserPortraitProjection;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for building and sending newsletter emails (Story 10.7 — AC8, AC10).
 *
 * <p>Assembles all template variables from event data, builds the per-recipient
 * email with a unique unsubscribeLink, and sends via {@link EmailService}.
 * Audit records are written to newsletter_sends + newsletter_recipients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterEmailService {

    private static final String TEMPLATE_KEY = "newsletter-event";
    private static final String LAYOUT_KEY = "batbern-default";

    /** Session types that are structural (moderation, breaks, lunch) — excluded from newsletter. */
    private static final Set<String> STRUCTURAL_SESSION_TYPES = Set.of("moderation", "break", "lunch");

    /** Workflow states where speaker section is considered published. */
    private static final Set<EventWorkflowState> SPEAKERS_VISIBLE_STATES = EnumSet.of(
            EventWorkflowState.AGENDA_PUBLISHED,
            EventWorkflowState.AGENDA_FINALIZED,
            EventWorkflowState.EVENT_LIVE,
            EventWorkflowState.EVENT_COMPLETED,
            EventWorkflowState.ARCHIVED
    );

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final NewsletterSubscriberService subscriberService;
    private final NewsletterSendRepository sendRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final EventRepository eventRepository;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    // ── Preview (no send) ─────────────────────────────────────────────────────

    /**
     * Builds a preview of the newsletter email without sending.
     *
     * @param event      the event to preview for
     * @param isReminder whether to render as a reminder (adds "Erinnerung: " prefix)
     * @param locale     "de" or "en"
     * @return preview with rendered subject and HTML
     */
    @Transactional(readOnly = true)
    public NewsletterPreviewResponse preview(Event event, boolean isReminder, String locale) {
        Map<String, String> vars = buildVariables(event, locale, isReminder, baseUrl + "/unsubscribe?token=PREVIEW");
        String contentHtml = renderContent(locale, vars);
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);
        String subject = buildSubject(event, isReminder, locale, vars);
        int count = (int) subscriberService.getActiveCount();
        return NewsletterPreviewResponse.builder()
                .subject(subject)
                .htmlPreview(mergedHtml)
                .recipientCount(count)
                .build();
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Sends the newsletter to all active subscribers and records the send.
     *
     * @param event           the event
     * @param isReminder      whether to use "Reminder: " prefix
     * @param locale          "de" or "en"
     * @param sentByUsername  organizer's username for audit log
     * @return summary of the send operation
     */
    @Transactional
    public NewsletterSendResponse sendNewsletter(Event event, boolean isReminder,
                                                  String locale, String sentByUsername) {
        List<NewsletterSubscriber> subscribers = subscriberService.findActiveSubscribers();
        Map<String, String> baseVars = buildVariables(event, locale, isReminder, "");

        // Compute subject once (shared across recipients)
        String subject = buildSubject(event, isReminder, locale, baseVars);

        // Send audit record
        NewsletterSend send = NewsletterSend.builder()
                .eventId(event.getId())
                .templateKey(TEMPLATE_KEY)
                .reminder(isReminder)
                .locale(locale)
                .sentAt(Instant.now())
                .sentByUsername(sentByUsername)
                .recipientCount(subscribers.size())
                .build();
        NewsletterSend saved = sendRepository.save(send);

        // Per-recipient send
        for (NewsletterSubscriber subscriber : subscribers) {
            try {
                String unsubscribeLink = baseUrl + "/unsubscribe?token=" + subscriber.getUnsubscribeToken();
                Map<String, String> recipientVars = new HashMap<>(baseVars);
                recipientVars.put("unsubscribeLink", unsubscribeLink);
                String contentHtml = renderContent(locale, recipientVars);
                String mergedHtml = emailService.replaceVariables(
                        emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), recipientVars);
                emailService.sendHtmlEmail(subscriber.getEmail(), subject, mergedHtml);
            } catch (Exception e) {
                log.error("Failed to send newsletter to {}: {}", subscriber.getEmail(), e.getMessage());
            }
        }

        log.info("Newsletter sent for event {} by {}: {} recipients",
                event.getEventCode(), sentByUsername, subscribers.size());
        return toResponse(saved);
    }

    // ── Variable building ─────────────────────────────────────────────────────

    /**
     * Builds all template variable substitutions for a newsletter email.
     *
     * @param event           event data
     * @param locale          "de" or "en"
     * @param isReminder      whether to add the reminder prefix
     * @param unsubscribeLink per-recipient unsubscribe URL (placeholder for preview)
     */
    Map<String, String> buildVariables(Event event, String locale, boolean isReminder, String unsubscribeLink) {
        boolean isDe = "de".equals(locale);
        Locale javaLocale = isDe ? Locale.GERMAN : Locale.ENGLISH;

        Map<String, String> vars = new HashMap<>();
        vars.put("reminderPrefix", buildReminderPrefix(isReminder, isDe));
        vars.put("eventNumber", String.valueOf(event.getEventNumber()));
        vars.put("eventType", localizeEventType(event.getEventType(), isDe));
        vars.put("eventTitle", event.getTitle());
        vars.put("eventDate", formatEventDate(event, javaLocale));
        vars.put("eventTime", formatEventTime(event, isDe));
        vars.put("venue", event.getVenueName());
        vars.put("venueDirectionsUrl", ""); // No directions URL in current Event model — Mustache block suppressed
        vars.put("conferenceLanguage", isDe ? "Deutsch / Englisch" : "German / English");
        vars.put("speakersSection", buildSpeakersSection(event, isDe));
        vars.put("currentYear", String.valueOf(java.time.Year.now().getValue()));
        vars.put("eventDetailLink", baseUrl + "/events/" + event.getEventCode());
        vars.put("registrationLink", baseUrl + "/register/" + event.getEventCode());
        vars.put("upcomingEventsSection", buildUpcomingEventsSection(event.getId(), isDe));
        vars.put("unsubscribeLink", unsubscribeLink);
        vars.put("preferencesLink", baseUrl + "/account");
        return vars;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildReminderPrefix(boolean isReminder, boolean isDe) {
        if (!isReminder) {
            return "";
        }
        return isDe ? "Erinnerung: " : "Reminder: ";
    }

    private String localizeEventType(ch.batbern.events.dto.generated.EventType eventType, boolean isDe) {
        if (eventType == null) {
            return isDe ? "Abend-BAT" : "Evening BAT";
        }
        return switch (eventType) {
            case EVENING -> isDe ? "Abend-BAT" : "Evening BAT";
            case FULL_DAY -> isDe ? "Ganztages-BAT" : "Full-Day BAT";
            case AFTERNOON -> isDe ? "Nachmittags-BAT" : "Afternoon BAT";
            default -> eventType.name().replace("_", " ");
        };
    }

    private String formatEventDate(Event event, Locale locale) {
        if (event.getDate() == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("EEEE, d. MMMM yyyy", locale)
                .withZone(ZoneId.of("Europe/Zurich"));
        return formatter.format(event.getDate());
    }

    private String formatEventTime(Event event, boolean isDe) {
        // Event entity doesn't have a separate start/end time field.
        // Default to standard BATbern evening time.
        return isDe ? "ab 16:00 Uhr" : "from 4:00 PM";
    }

    /**
     * Builds the speakers section as an HTML table when event workflow state allows it.
     * One row per session; multiple speakers joined by "; ". Structural sessions filtered out.
     * Company comes from user_profiles.company_id via UserPortraitProjection.
     * Returns empty string when agenda is not yet published.
     */
    String buildSpeakersSection(Event event, boolean isDe) {
        if (event.getWorkflowState() == null
                || !SPEAKERS_VISIBLE_STATES.contains(event.getWorkflowState())) {
            return "";
        }
        List<Session> sessions = sessionRepository.findByEventIdWithSpeakers(event.getId());
        if (sessions.isEmpty()) {
            return "";
        }

        // Collect all speaker usernames across content sessions
        Set<String> usernames = sessions.stream()
                .filter(s -> !STRUCTURAL_SESSION_TYPES.contains(s.getSessionType()))
                .flatMap(s -> s.getSessionUsers() == null ? java.util.stream.Stream.empty()
                        : s.getSessionUsers().stream())
                .map(SessionUser::getUsername)
                .filter(u -> u != null && !u.isBlank())
                .collect(Collectors.toSet());

        // Batch-load companyId from user_profiles (intentional cross-service DB join)
        Map<String, String> usernameToCompany = usernames.isEmpty()
                ? Map.of()
                : sessionUserRepository.findUserPortraitsByUsernames(usernames).stream()
                        .filter(p -> p.getCompanyId() != null)
                        .collect(Collectors.toMap(
                                UserPortraitProjection::getUsername,
                                UserPortraitProjection::getCompanyId,
                                (a, b) -> a));

        String thStyle = "padding:6px 10px;text-align:left;font-size:11px;font-weight:600;"
                + "text-transform:uppercase;letter-spacing:1px;color:#71717A;"
                + "border-bottom:1px solid #E4E4E7;";
        String tdStyle = "padding:8px 10px;font-size:14px;color:#3F3F46;"
                + "border-bottom:1px solid #F4F4F5;vertical-align:top;";
        String tdMutedStyle = "padding:8px 10px;font-size:14px;color:#71717A;"
                + "border-bottom:1px solid #F4F4F5;vertical-align:top;";

        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin-top:8px;\">")
                .append("<thead><tr>")
                .append("<th style=\"").append(thStyle).append("\">")
                .append(isDe ? "Vortrag" : "Talk").append("</th>")
                .append("<th style=\"").append(thStyle).append("\">")
                .append(isDe ? "Sprecher\u00b7in" : "Speaker").append("</th>")
                .append("<th style=\"").append(thStyle).append("\">")
                .append(isDe ? "Unternehmen" : "Company").append("</th>")
                .append("</tr></thead><tbody>");

        boolean hasRows = false;
        for (Session session : sessions) {
            if (STRUCTURAL_SESSION_TYPES.contains(session.getSessionType())) {
                continue;
            }
            if (session.getSessionUsers() == null || session.getSessionUsers().isEmpty()) {
                continue;
            }

            // Collect title from first speaker's presentationTitle, fall back to session title
            String title = session.getSessionUsers().stream()
                    .map(SessionUser::getPresentationTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(session.getTitle());

            // Build "First Last; First Last" — one entry per speaker
            String speakerNames = session.getSessionUsers().stream()
                    .map(su -> {
                        String fn = su.getSpeakerFirstName() != null ? su.getSpeakerFirstName() : "";
                        String ln = su.getSpeakerLastName() != null ? su.getSpeakerLastName() : "";
                        String name = (fn + " " + ln).trim();
                        return name.isBlank() ? su.getUsername() : name;
                    })
                    .collect(Collectors.joining("; "));

            // Company: use first speaker's companyId (most sessions are single-company)
            String company = session.getSessionUsers().stream()
                    .map(su -> usernameToCompany.getOrDefault(su.getUsername(), ""))
                    .filter(c -> !c.isBlank())
                    .findFirst()
                    .orElse("");

            sb.append("<tr>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(title)).append("</td>")
                    .append("<td style=\"").append(tdStyle).append("\"><strong>")
                    .append(escapeHtml(speakerNames)).append("</strong></td>")
                    .append("<td style=\"").append(tdMutedStyle).append("\">")
                    .append(escapeHtml(company)).append("</td>")
                    .append("</tr>");
            hasRows = true;
        }

        if (!hasRows) {
            return "";
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    /**
     * Builds the upcoming events section HTML with future confirmed events.
     * Returns empty string if no future events exist.
     */
    String buildUpcomingEventsSection(java.util.UUID excludeEventId, boolean isDe) {
        Instant now = Instant.now();
        List<Event> future = eventRepository.findByDateAfter(now).stream()
                .filter(e -> !e.getId().equals(excludeEventId))
                .filter(e -> e.getWorkflowState() != EventWorkflowState.ARCHIVED)
                .toList();
        if (future.isEmpty()) {
            return "";
        }
        Locale locale = isDe ? Locale.GERMAN : Locale.ENGLISH;
        DateTimeFormatter dateFormatter = DateTimeFormatter
                .ofPattern("EEEE, dd.MM.yyyy", locale)
                .withZone(ZoneId.of("Europe/Zurich"));
        StringBuilder sb = new StringBuilder();
        String headerStyle = "margin: 0 0 8px; font-size: 14px; font-weight: 600; color: #18181B;";
        String headerHtml = isDe
                ? "<p style=\"" + headerStyle + "\">Weitere BAT-Termine:</p>"
                : "<p style=\"" + headerStyle + "\">Upcoming events:</p>";
        sb.append(headerHtml);
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin:0 0 16px;\">");
        for (Event e : future) {
            String code = e.getEventCode();
            String date = e.getDate() != null ? dateFormatter.format(e.getDate()) : "";
            String title = e.getTitle() != null ? e.getTitle() : (isDe ? "Thema noch offen" : "Topic TBD");
            String tdStyle = "padding:5px 10px;border:1px solid #E4E4E7;font-size:13px;";
            sb.append("<tr>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(code)).append("</td>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(date)).append("</td>")
                    .append("<td style=\"").append(tdStyle).append("font-style:italic;\">")
                    .append(escapeHtml(title)).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String renderContent(String locale, Map<String, String> vars) {
        Optional<ch.batbern.events.domain.EmailTemplate> templateOpt =
                emailTemplateService.findByKeyAndLocale(TEMPLATE_KEY, locale);
        if (templateOpt.isEmpty()) {
            log.warn("Newsletter template '{}' locale '{}' not found in DB", TEMPLATE_KEY, locale);
            return "<p>Newsletter template not available.</p>";
        }
        return emailService.replaceVariables(templateOpt.get().getHtmlBody(), vars);
    }

    private String buildSubject(Event event, boolean isReminder, String locale,
                                Map<String, String> vars) {
        Optional<String> subjectTemplate = emailTemplateService.resolveSubject(TEMPLATE_KEY, locale);
        String subject = subjectTemplate.orElseGet(() ->
                (isReminder ? vars.get("reminderPrefix") : "") + event.getTitle() + " — BATbern");
        return emailService.replaceVariables(subject, vars);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /** Maps a NewsletterSend entity to its response DTO. */
    public NewsletterSendResponse toResponse(NewsletterSend send) {
        return NewsletterSendResponse.builder()
                .id(send.getId())
                .sentAt(send.getSentAt())
                .reminder(send.isReminder())
                .locale(send.getLocale())
                .recipientCount(send.getRecipientCount() != null ? send.getRecipientCount() : 0)
                .sentByUsername(send.getSentByUsername())
                .build();
    }
}
