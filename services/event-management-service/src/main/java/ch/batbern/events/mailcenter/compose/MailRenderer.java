package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.shared.service.EmailService;

import java.util.Map;

/**
 * The one render order of a BATbern-layout mail (Story 16.8 AC3): variables into the content, the
 * content into the {@code batbern-default} layout, variables again for the layout's own
 * placeholders. Newsletter, registrant notices and venue mail render through here, for preview and
 * send alike, so a preview cannot differ from what goes out.
 *
 * <p>Static on purpose: the services keep their constructor dependencies, and a test that mocks
 * {@link EmailService} sees exactly the calls it saw before.
 */
public final class MailRenderer {

    public static final String LAYOUT_KEY = "batbern-default";

    private MailRenderer() {
    }

    /**
     * Story 16.11: the one renderer. Content variables, then the content's layout (none when its
     * layout key is null), then variables again for the layout's own placeholders; the subject gets
     * the same variables, or stays null when the content has none.
     */
    public static RenderedMail render(EmailService emailService, EmailTemplateService templates, MailContent content,
            Map<String, String> variables) {
        String body = emailService.replaceVariables(content.bodyHtml(), variables);
        String html = content.layoutKey() == null ? body
                : emailService.replaceVariables(templates.mergeWithLayout(body, content.layoutKey(),
                        content.locale()), variables);
        String subject = content.subject() == null ? null
                : emailService.replaceVariables(content.subject(), variables);
        return new RenderedMail(subject, html);
    }
}
