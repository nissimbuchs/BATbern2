package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.NewsletterPreviewResponse;
import ch.batbern.events.dto.NewsletterSendResponse;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
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
            EventWorkflowState.EVENT_LIVE,
            EventWorkflowState.EVENT_COMPLETED,
            EventWorkflowState.ARCHIVED
    );

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final NewsletterSubscriberService subscriberService;
    private final NewsletterSendRepository sendRepository;
    private final NewsletterRecipientRepository recipientRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserService sessionUserService;
    private final EventRepository eventRepository;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    // ── Preview (no send) ─────────────────────────────────────────────────────

    /**
     * Builds a preview of the newsletter email without sending.
     *
     * @param event       the event to preview for
     * @param isReminder  whether to render as a reminder (adds "Erinnerung: " prefix)
     * @param locale      "de" or "en"
     * @param templateKey optional template key override; null → uses default 'newsletter-event'
     * @return preview with rendered subject and HTML
     */
    @Transactional(readOnly = true)
    public NewsletterPreviewResponse preview(Event event, boolean isReminder, String locale,
                                              @Nullable String templateKey) {
        String effectiveKey = resolveTemplateKey(templateKey);
        Map<String, String> vars = buildVariables(event, locale, isReminder, baseUrl + "/unsubscribe?token=PREVIEW");
        String contentHtml = renderContent(locale, vars, effectiveKey);
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);
        String subject = buildSubject(event, isReminder, locale, vars, effectiveKey);
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
     * <p>Note: email sending is intentionally performed outside of a DB transaction
     * to avoid holding a connection open during potentially long SMTP operations.
     * The audit record is committed first via {@link #createSendAuditRecord}.
     *
     * @param event           the event
     * @param isReminder      whether to use "Reminder: " prefix
     * @param locale          "de" or "en"
     * @param sentByUsername  organizer's username for audit log
     * @param templateKey     optional template key override; null → uses default 'newsletter-event'
     * @return summary of the send operation
     */
    public NewsletterSendResponse sendNewsletter(Event event, boolean isReminder,
                                                  String locale, String sentByUsername,
                                                  @Nullable String templateKey) {
        String effectiveKey = resolveTemplateKey(templateKey);
        List<NewsletterSubscriber> subscribers = subscriberService.findActiveSubscribers();
        Map<String, String> baseVars = buildVariables(event, locale, isReminder, "");
        String subject = buildSubject(event, isReminder, locale, baseVars, effectiveKey);

        // Persist send audit record first (committed immediately — own transaction)
        NewsletterSend saved = createSendAuditRecord(event, isReminder, locale, sentByUsername,
                subscribers.size(), effectiveKey);

        // Per-recipient send + recipient audit row (outside main transaction)
        for (NewsletterSubscriber subscriber : subscribers) {
            String deliveryStatus = "sent";
            try {
                String unsubscribeLink = baseUrl + "/unsubscribe?token=" + subscriber.getUnsubscribeToken();
                Map<String, String> recipientVars = new HashMap<>(baseVars);
                recipientVars.put("unsubscribeLink", unsubscribeLink);
                String contentHtml = renderContent(locale, recipientVars, effectiveKey);
                String mergedHtml = emailService.replaceVariables(
                        emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), recipientVars);
                emailService.sendHtmlEmail(subscriber.getEmail(), subject, mergedHtml);
            } catch (Exception e) {
                log.error("Failed to send newsletter to {}: {}", subscriber.getEmail(), e.getMessage());
                deliveryStatus = "failed";
            }
            // AC10: log each recipient in newsletter_recipients
            recordRecipient(saved.getId(), subscriber.getEmail(), deliveryStatus);
        }

        log.info("Newsletter sent for event {} by {}: {} recipients",
                event.getEventCode(), sentByUsername, subscribers.size());
        return toResponse(saved);
    }

    /** Saves the newsletter_sends audit row in its own transaction. */
    @Transactional
    protected NewsletterSend createSendAuditRecord(Event event, boolean isReminder, String locale,
                                                   String sentByUsername, int recipientCount,
                                                   String templateKey) {
        NewsletterSend send = NewsletterSend.builder()
                .eventId(event.getId())
                .templateKey(templateKey)
                .reminder(isReminder)
                .locale(locale)
                .sentAt(Instant.now())
                .sentByUsername(sentByUsername)
                .recipientCount(recipientCount)
                .build();
        return sendRepository.save(send);
    }

    /** Saves a single newsletter_recipients row in its own transaction. */
    @Transactional
    protected void recordRecipient(java.util.UUID sendId, String email, String deliveryStatus) {
        NewsletterRecipient recipient = NewsletterRecipient.builder()
                .id(new NewsletterRecipientId(sendId, email))
                .deliveryStatus(deliveryStatus)
                .build();
        recipientRepository.save(recipient);
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
     * Speaker names and company come from SessionUserService (enriched via user-management-service).
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
                .append("</tr></thead><tbody>");

        boolean hasRows = false;
        for (Session session : sessions) {
            if (STRUCTURAL_SESSION_TYPES.contains(session.getSessionType())) {
                continue;
            }

            // Use SessionUserService to get enriched speaker data (firstName/lastName/company
            // fetched from company-user-management-service — same path as the event detail API).
            List<SessionSpeakerResponse> speakers =
                    sessionUserService.getSessionSpeakers(session.getId());
            if (speakers.isEmpty()) {
                continue;
            }

            // Collect title from first speaker's presentationTitle, fall back to session title
            String title = speakers.stream()
                    .map(SessionSpeakerResponse::getPresentationTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(session.getTitle());

            // Build "First Last, Company; First Last2, Company2" — one entry per speaker
            String speakerNames = speakers.stream()
                    .map(sp -> {
                        String fn = sp.getFirstName() != null ? sp.getFirstName() : "";
                        String ln = sp.getLastName() != null ? sp.getLastName() : "";
                        String name = (fn + " " + ln).trim();
                        if (name.isBlank()) {
                            name = sp.getUsername();
                        }
                        String company = sp.getCompany() != null ? sp.getCompany().trim() : "";
                        return company.isBlank() ? name : name + ", " + company;
                    })
                    .collect(Collectors.joining("; "));

            sb.append("<tr>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(title)).append("</td>")
                    .append("<td style=\"").append(tdMutedStyle).append("\">")
                    .append(escapeHtml(speakerNames)).append("</td>")
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

    private String resolveTemplateKey(@Nullable String templateKey) {
        return (templateKey != null && !templateKey.isBlank()) ? templateKey : TEMPLATE_KEY;
    }

    private String renderContent(String locale, Map<String, String> vars, String templateKey) {
        Optional<ch.batbern.events.domain.EmailTemplate> templateOpt =
                emailTemplateService.findByKeyAndLocale(templateKey, locale);
        if (templateOpt.isEmpty()) {
            log.warn("Newsletter template '{}' locale '{}' not found in DB", templateKey, locale);
            return "<p>Newsletter template not available.</p>";
        }
        return emailService.replaceVariables(templateOpt.get().getHtmlBody(), vars);
    }

    private String buildSubject(Event event, boolean isReminder, String locale,
                                Map<String, String> vars, String templateKey) {
        Optional<String> subjectTemplate = emailTemplateService.resolveSubject(templateKey, locale);
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
