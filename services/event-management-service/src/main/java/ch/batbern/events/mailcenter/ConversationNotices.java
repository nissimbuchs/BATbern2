package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.mailcenter.variables.MailVariables;
import ch.batbern.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Story 16.6 AC8: the short mail that replaces a forwarded copy when the mail's group is answered in
 * the Mail Center (template {@code mailcenter-new-mail}, DE + EN): who wrote, the subject, and a link
 * to the conversation. No body, no attachment, no quoted text. The language follows the recipient's
 * preference when the address belongs to an organizer, else German.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationNotices {

    static final String TEMPLATE_KEY = "mailcenter-new-mail";

    private final MailPipeline mailPipeline;
    private final EventRepository eventRepository;
    private final ForwardingDirectory directory;
    private final UserApiClient userApiClient;
    private final ConversationLinks links;

    /** The notice for each recipient address, rendered once per language. */
    public Function<String, EmailForwardingService.Notice> forStored(InboundMailCenter.Stored stored) {
        Optional<Event> event = Optional.ofNullable(stored.eventCode()).flatMap(eventRepository::findByEventCode);
        Map<String, EmailForwardingService.Notice> byLocale = new HashMap<>();
        return recipient -> byLocale.computeIfAbsent(localeOf(recipient), locale -> render(stored, event, locale));
    }

    private EmailForwardingService.Notice render(InboundMailCenter.Stored stored, Optional<Event> event,
            String locale) {
        String from = stored.fromName() != null && !stored.fromName().isBlank() ? stored.fromName() : "?";
        MailVariables variables = event.map(e -> mailPipeline.variables().event(e, locale))
                .orElseGet(() -> mailPipeline.variables().system())
                .put(MailVariable.COUNTERPART_NAME, from)
                .put(MailVariable.CONVERSATION_SUBJECT, stored.subject())
                .put(MailVariable.CONVERSATION_LINK, links.link(stored.eventCode(), stored.conversationKey()));
        RenderedMail mail = mailPipeline.render(mailPipeline.template(TEMPLATE_KEY, locale), variables.asMap());
        String subject = mail.subject() != null ? mail.subject()
                : ("en".equals(locale) ? "New mail from " : "Neue Mail von ") + from;
        return new EmailForwardingService.Notice(subject, mail.html());
    }

    private String localeOf(String recipient) {
        try {
            return directory.organizerUsernameFor(recipient)
                    .map(userApiClient::getPreferredLanguage)
                    .filter("en"::equals)
                    .orElse("de");
        } catch (RuntimeException e) {
            log.debug("Notice language for a recipient not resolved: {}", e.toString());
            return "de";
        }
    }
}
