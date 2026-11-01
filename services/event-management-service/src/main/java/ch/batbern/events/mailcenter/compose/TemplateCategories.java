package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.service.EmailTemplateService;

import java.util.Optional;
import java.util.stream.Stream;

/** Whether a template of the admin store belongs to a category, in either language. */
final class TemplateCategories {

    private TemplateCategories() {
    }

    static boolean has(EmailTemplateService templates, String templateKey, String category) {
        return templateKey != null && Stream.of("de", "en")
                .map(locale -> templates.findByKeyAndLocale(templateKey, locale))
                .flatMap(Optional::stream)
                .anyMatch(t -> category.equalsIgnoreCase(t.getCategory()));
    }
}
