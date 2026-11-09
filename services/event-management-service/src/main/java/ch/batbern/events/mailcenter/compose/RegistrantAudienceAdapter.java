package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.mailcenter.variables.MailVariable.Scope;
import ch.batbern.events.service.SlidesOnlineEmailService;
import ch.batbern.events.service.SlidesOnlineEmailService.RegistrantAudience;
import ch.batbern.events.service.SlidesOnlineEmailService.RenderedMail;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public Set<Scope> scopes() {
        return Set.of(Scope.RECIPIENT, Scope.REGISTRATION);
    }

    @Override
    public Optional<Instant> ownTextAlreadySent(ComposeContext context) {
        return notices.ownTextAlreadySent(context.event(), context.request().ownSubject());
    }

    @Override
    public List<String> offeredVariables() {
        return List.of("recipientName", "deregistrationUrl", "eventTitle", "eventDate", "eventNumber", "eventType");
    }

    @Override
    public void validate(ComposeRequest request) {
        if (!request.ownText() && request.templateKey() == null) {
            throw new IllegalArgumentException("templateKey is required for registrants");
        }
        // A template send is one-shot per event, so it always goes to the whole default audience.
        if (!request.ownText() && !request.registrantStatuses().isEmpty()) {
            throw new IllegalArgumentException("registrantStatuses is for own text only");
        }
        if (!SlidesOnlineEmailService.CHOOSABLE_REGISTRANT_STATUSES.containsAll(request.registrantStatuses())) {
            throw new IllegalArgumentException("registrantStatuses may contain "
                    + SlidesOnlineEmailService.CHOOSABLE_REGISTRANT_STATUSES + " only");
        }
    }

    @Override
    public Recipients resolveRecipients(ComposeContext context) {
        RegistrantAudience audience = notices.resolveRecipients(context.event(), statuses(context.request()));
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
        String templateKey = context.request().ownText() ? null : context.request().templateKey();
        String locale = locale(context, registration);
        RenderedMail base = context.request().ownText()
                ? notices.renderOwnBase(context.event(), ownTextContent(context))
                : notices.renderBase(context.event(), templateKey, locale);
        RenderedMail mail = notices.personalise(base, registration, test);
        return new Rendering(mail.subject(), mail.html(), templateKey, locale, null,
                List.of(registration.getRegistrationCode()), List.of());
    }

    @Override
    public Map<String, String> variables(ComposeContext context, ComposeRecipient recipient, boolean test) {
        Registration registration = (Registration) recipient.key();
        return notices.variables(context.event(), locale(context, registration), registration, test);
    }

    private static List<String> statuses(ComposeRequest request) {
        return SlidesOnlineEmailService.registrantStatuses(request.registrantStatuses());
    }

    /** Own text is in the organizer's language for everyone (Story 16.5); a template in each registrant's. */
    private String locale(ComposeContext context, Registration registration) {
        return context.request().ownText() ? context.request().locale() : notices.recipientLocale(registration);
    }

    /** The existing registrant-notice send; its record is grouped by its audit row. */
    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        ComposeRequest request = context.request();
        if (request.ownText()) {
            Optional<Instant> sentAt = notices.ownTextAlreadySent(context.event(), request.ownSubject());
            if (sentAt.isPresent() && !request.confirmResend()) {
                throw new AlreadySentException(request.ownSubject(), sentAt.get());
            }
            UUID sendId = notices.sendRegistrantOwnText(context.event(), request.ownSubject(), request.ownBodyHtml(),
                    request.locale(), context.organizerUsername(), statuses(request))
                    .getSendId();
            MailContext mailContext = MailContext.ownText(MailTrigger.forSend(sendId, context.organizerUsername()),
                    context.event().getEventCode(), MailContext.Audience.REGISTRANTS).forNewsletterSend(sendId, false);
            return List.of(new DeliveredMail(mailContext, render(context, recipients.recipients().get(0), false)));
        }
        String templateKey = context.request().templateKey();
        UUID sendId = notices.sendRegistrantNotice(context.event(), templateKey, context.organizerUsername())
                .getSendId();
        MailContext mailContext = SlidesOnlineEmailService.mailContextFor(sendId, context.event().getEventCode(),
                templateKey, context.organizerUsername());
        return List.of(new DeliveredMail(mailContext, render(context, recipients.recipients().get(0), false)));
    }
}
