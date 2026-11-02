package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipelines;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Story 16.5 AC1/AC2: the own-text preview marks values, keeps attributes valid, flags unknown names. */
class PreviewHighlighterTest {

    private final EmailService emailService = mock(EmailService.class);
    private final EmailTemplateService templates = mock(EmailTemplateService.class);

    private RenderedMail highlight(String subject, String body, Map<String, String> vars) {
        when(emailService.replaceVariables(anyString(), anyMap())).thenCallRealMethod();
        when(templates.mergeWithLayout(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        MailContent content = new MailContent(null, "en", subject, body, MailRenderer.LAYOUT_KEY,
                MailContext.ContentSource.OWN_TEXT);
        return PreviewHighlighter.highlight(MailPipelines.over(emailService, templates), content, vars);
    }

    @Test
    @DisplayName("a value in the text is wrapped in <mark data-var>")
    void valueInText() {
        RenderedMail mail = highlight("Hi {{speakerName}}", "<p>Dear {{speakerName}}</p>",
                Map.of("speakerName", "Anna"));

        assertThat(mail.html()).contains("<p>Dear <mark data-var=\"speakerName\">Anna</mark></p>");
        assertThat(mail.subject()).isEqualTo("Hi <mark data-var=\"speakerName\">Anna</mark>");
        assertThat(mail.html()).contains(PreviewHighlighter.STYLE);
    }

    @Test
    @DisplayName("a value inside an attribute is not wrapped, so the link stays valid")
    void valueInAttribute() {
        RenderedMail mail = highlight("S", "<a href=\"{{portalLink}}\">{{portalLink}}</a>",
                Map.of("portalLink", "https://batbern.ch/p"));

        assertThat(mail.html()).contains("<a href=\"https://batbern.ch/p\">"
                + "<mark data-var=\"portalLink\">https://batbern.ch/p</mark></a>");
        assertThat(mail.html()).doesNotContain("\u0001").doesNotContain("\u0003");
    }

    @Test
    @DisplayName("an unknown placeholder is marked red; in the subject too")
    void unknown() {
        RenderedMail mail = highlight("Re {{deadlin}}", "<p>{{deadlin}}</p>", Map.of());

        assertThat(mail.html()).contains("<mark class=\"unknown\">{{deadlin}}</mark>");
        assertThat(mail.subject()).contains("<mark class=\"unknown\">{{deadlin}}</mark>");
    }

    @Test
    @DisplayName("empty values are not wrapped, so conditional blocks render as in the real mail")
    void emptyValueBlocks() {
        RenderedMail mail = highlight("S", "{{#notes}}Notes: {{notes}}{{/notes}}end", Map.of("notes", ""));

        assertThat(mail.html()).doesNotContain("Notes:").contains("end").doesNotContain("<mark data-var");
    }

    @Test
    @DisplayName("the subject is HTML-escaped before marking")
    void subjectEscaped() {
        RenderedMail mail = highlight("<b>{{eventTitle}}</b>", "<p>x</p>", Map.of("eventTitle", "A&B"));

        assertThat(mail.subject()).isEqualTo("&lt;b&gt;<mark data-var=\"eventTitle\">A&amp;B</mark>&lt;/b&gt;");
    }
}
