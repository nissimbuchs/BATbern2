package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.service.SlidesOnlineEmailService;
import ch.batbern.events.service.SlidesOnlineEmailService.RegistrantAudience;
import ch.batbern.events.service.SlidesOnlineEmailService.RenderedMail;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Registrant notice to the event's active registrants (Story 16.8). Personalised (name, one-click
 * cancel link) and in each registrant's own language. Delivery is the existing async send with
 * its {@code newsletter_sends} audit row and one-shot guard (409 when already sent).
 */
@Component
@RequiredArgsConstructor
public class RegistrantAudienceAdapter implements AudienceAdapter {

    private final SlidesOnlineEmailService notices;

    @Override
    public MailContext.Audience audience() {
        return MailContext.Audience.REGISTRANTS;
    }

    @Override
    public boolean personalized() {
        return true;
    }

    @Override
    public Set<String> allowedCategories() {
        return Set.of("REGISTRANT_NOTICE");
    }

    @Override
    public boolean allowsTemplate(String templateKey) {
        return notices.isRegistrantNoticeTemplate(templateKey);
    }

    @Override
    public Set<String> allowedVariables() {
        return Set.of("eventTitle", "eventNumber", "eventType", "eventDate", "eventDetailLink", "currentYear",
                "logoUrl", "eventUrl", "dashboardLink", "supportUrl", "deregistrationUrl", "recipientName",
                "attendeeFirstName");
    }

    @Override
    public Set<String> credentialVariables() {
        return Set.of("deregistrationUrl");
    }

    @Override
    public void validate(ComposeRequest request) {
        if (request.templateKey() == null) {
            throw new IllegalArgumentException("templateKey is required for registrants");
        }
    }

    @Override
    public Recipients resolveRecipients(ComposeContext context) {
        RegistrantAudience audience = notices.resolveRecipients(context.event());
        return new Recipients(
                audience.recipients().stream()
                        .map(r -> new ComposeRecipient(r.displayName(), r.email(), List.of(), r.registration()))
                        .toList(),
                audience.skipped().stream()
                        .map(s -> new SkippedRecipient(s.displayName(), s.reason()))
                        .toList());
    }

    @Override
    public Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test) {
        Registration registration = (Registration) recipient.key();
        String templateKey = context.request().templateKey();
        String locale = notices.recipientLocale(registration);
        RenderedMail mail = notices.personalise(notices.renderBase(context.event(), templateKey, locale),
                registration, test);
        return new Rendering(mail.subject(), mail.html(), templateKey, locale, null,
                List.of(registration.getRegistrationCode()), List.of());
    }

    /** The existing registrant-notice send; its record is grouped by its audit row. */
    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        String templateKey = context.request().templateKey();
        UUID sendId = notices.sendRegistrantNotice(context.event(), templateKey, context.organizerUsername())
                .getSendId();
        MailContext mailContext = SlidesOnlineEmailService.mailContextFor(sendId, context.event().getEventCode(),
                templateKey, context.organizerUsername());
        return List.of(new DeliveredMail(mailContext, render(context, recipients.recipients().get(0), false)));
    }
}
