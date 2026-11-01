package ch.batbern.events.mailcenter.compose;

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

    public static String body(EmailService emailService, EmailTemplateService templates, String contentTemplate,
            String locale, Map<String, String> variables) {
        String content = emailService.replaceVariables(contentTemplate, variables);
        return emailService.replaceVariables(templates.mergeWithLayout(content, LAYOUT_KEY, locale), variables);
    }
}
