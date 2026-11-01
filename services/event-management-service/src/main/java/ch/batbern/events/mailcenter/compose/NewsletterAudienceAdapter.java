package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.events.service.NewsletterEmailService;
import ch.batbern.events.service.NewsletterEmailService.NewsletterDraft;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Newsletter to all active subscribers (Story 16.8). Everyone gets the same text except the
 * unsubscribe link, which is redacted for display, so the preview does not step. Delivery is the
 * existing newsletter job with its {@code newsletter_sends} progress, retry and ADR-015 wall.
 */
@Component
@RequiredArgsConstructor
public class NewsletterAudienceAdapter implements AudienceAdapter {

    private static final UUID KEYSET_START = new UUID(0L, 0L);

    private final NewsletterEmailService newsletter;
    private final NewsletterSubscriberRepository subscribers;
    private final EmailTemplateService templates;

    @Override
    public MailContext.Audience audience() {
        return MailContext.Audience.NEWSLETTER;
    }

    @Override
    public boolean personalized() {
        return false;
    }

    @Override
    public Set<String> allowedCategories() {
        return Set.of("NEWSLETTER");
    }

    @Override
    public boolean allowsTemplate(String templateKey) {
        return templateKey == null || TemplateCategories.has(templates, templateKey, "NEWSLETTER");
    }

    @Override
    public Set<String> allowedVariables() {
        return Set.of("reminderPrefix", "eventNumber", "eventType", "eventTitle", "eventDate", "eventTime", "venue",
                "venueDirectionsUrl", "conferenceLanguage", "speakersSection", "currentYear", "eventDetailLink",
                "registrationLink", "upcomingEventsSection", "unsubscribeLink", "preferencesLink", "logoUrl");
    }

    @Override
    public Set<String> credentialVariables() {
        return Set.of("unsubscribeLink");
    }

    @Override
    public void validate(ComposeRequest request) {
        // Nothing beyond the template check: every field has a default.
    }

    /** Active subscribers in the job's order (keyset by id, #1129). */
    @Override
    public Recipients resolveRecipients(ComposeContext context) {
        List<ComposeRecipient> recipients = subscribers.findActiveAfterId(KEYSET_START, Limit.unlimited()).stream()
                .map(s -> new ComposeRecipient(displayName(s), s.getEmail(), List.of(), s))
                .toList();
        return new Recipients(recipients, List.of());
    }

    @Override
    public Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test) {
        ComposeRequest request = context.request();
        NewsletterDraft draft = draft(context);
        String token = test ? "TEST" : ((NewsletterSubscriber) recipient.key()).getUnsubscribeToken();
        String html = newsletter.renderBody(draft, newsletter.unsubscribeLink(token));
        return new Rendering(draft.subject(), html, draft.templateKey(), request.locale(), null, List.of(),
                newsletter.icsAttachments(draft, context.event()));
    }

    /**
     * The existing newsletter send, as an organizer request. Its record is grouped by its send row,
     * not by {@code trigger}, so a retry joins the same mail.
     */
    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        ComposeRequest request = context.request();
        String templateKey = newsletter.effectiveTemplateKey(request.templateKey());
        UUID sendId = newsletter.sendNewsletter(context.event(), request.newsletterReminder(), request.locale(),
                context.organizerUsername(), templateKey, null, false).getId();
        MailContext mailContext = NewsletterEmailService.mailContextFor(sendId, context.event().getEventCode(),
                templateKey, context.organizerUsername(), false);
        return List.of(new DeliveredMail(mailContext, render(context, recipients.recipients().get(0), false)));
    }

    private NewsletterDraft draft(ComposeContext context) {
        ComposeRequest request = context.request();
        return newsletter.prepare(context.event(), request.locale(), request.newsletterReminder(),
                newsletter.effectiveTemplateKey(request.templateKey()));
    }

    private static String displayName(NewsletterSubscriber subscriber) {
        return subscriber.getFirstName() != null && !subscriber.getFirstName().isBlank()
                ? subscriber.getFirstName()
                : subscriber.getEmail();
    }
}
