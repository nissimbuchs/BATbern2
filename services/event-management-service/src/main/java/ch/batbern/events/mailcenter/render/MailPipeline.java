package ch.batbern.events.mailcenter.render;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.mailcenter.compose.MailRenderer;
import ch.batbern.events.mailcenter.variables.MailVariableFactory;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one way a template-based mail is made (Story 16.11): {@link #template} resolves the content
 * once, {@link #render} fills in the variables through {@link MailRenderer}, the only renderer.
 * Replaces the eight copies of {@code loadHtmlContent} the services carried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailPipeline {

    private static final String DEFAULT_LOCALE = "de";

    private final EmailService emailService;
    private final EmailTemplateService templates;
    private final MailVariableFactory variables;

    public MailVariableFactory variables() {
        return variables;
    }

    /**
     * The template's content in {@code locale}: the store first, its German version next, then the
     * classpath file the store is seeded from (seeded on every start, so in practice never reached;
     * used as the services used it: raw file, no layout, the caller's fallback subject).
     * Empty body, logged, when none exists.
     */
    public MailContent template(String templateKey, String locale) {
        String wanted = "en".equalsIgnoreCase(locale) ? "en" : DEFAULT_LOCALE;
        String foundLocale = wanted;
        Optional<EmailTemplate> stored = templates.findByKeyAndLocale(templateKey, wanted);
        if (stored.isEmpty() && !wanted.equals(DEFAULT_LOCALE)) {
            stored = templates.findByKeyAndLocale(templateKey, DEFAULT_LOCALE);
            foundLocale = DEFAULT_LOCALE;
        }
        if (stored.isPresent()) {
            EmailTemplate t = stored.get();
            return new MailContent(templateKey, foundLocale, subjectOf(t), t.getHtmlBody(), t.getLayoutKey(),
                    MailContext.ContentSource.TEMPLATE);
        }
        List<String> candidates = wanted.equals(DEFAULT_LOCALE) ? List.of(wanted) : List.of(wanted, DEFAULT_LOCALE);
        for (String candidate : candidates) {
            Optional<String> file = classpath("email-templates/" + templateKey + "-" + candidate + ".html");
            if (file.isPresent()) {
                // As the services did before 16.11: the file as it is, no layout, the caller's subject.
                return new MailContent(templateKey, candidate, null, file.get(), null,
                        MailContext.ContentSource.TEMPLATE);
            }
        }
        log.error("Email template not found in the store or on the classpath: {}/{}", templateKey, wanted);
        return new MailContent(templateKey, wanted, null, "", MailRenderer.LAYOUT_KEY,
                MailContext.ContentSource.TEMPLATE);
    }

    /**
     * The template's content from the store only, always in the BATbern layout: how the newsletter,
     * registrant notices, venue and partner mails loaded it before Story 16.11. Empty body when the
     * store has neither the locale nor German.
     */
    public MailContent storedTemplate(String templateKey, String locale) {
        String wanted = "en".equalsIgnoreCase(locale) ? "en" : DEFAULT_LOCALE;
        // The layout is merged in the requested language, as the per-service copies did.
        Optional<EmailTemplate> stored = templates.findByKeyAndLocale(templateKey, wanted);
        if (stored.isEmpty() && !wanted.equals(DEFAULT_LOCALE)) {
            stored = templates.findByKeyAndLocale(templateKey, DEFAULT_LOCALE);
        }
        return stored.map(t -> new MailContent(templateKey, wanted, subjectOf(t), t.getHtmlBody(),
                        t.getLayoutKey(), MailContext.ContentSource.TEMPLATE))
                .orElseGet(() -> new MailContent(templateKey, wanted, null, "", null,
                        MailContext.ContentSource.TEMPLATE))
                .withDefaultLayout(MailRenderer.LAYOUT_KEY);
    }

    /** The content with {@code vars} filled in. */
    public RenderedMail render(MailContent content, Map<String, String> vars) {
        return MailRenderer.render(emailService, templates, content, vars);
    }

    /** A blank subject counts as none, as {@code EmailTemplateService.resolveSubject} decided. */
    private static String subjectOf(EmailTemplate template) {
        String subject = template.getSubject();
        return subject == null || subject.isBlank() ? null : subject;
    }

    private static Optional<String> classpath(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.exists() ? Optional.of(resource.getContentAsString(StandardCharsets.UTF_8))
                    : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
