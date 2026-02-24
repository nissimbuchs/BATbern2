package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.repository.EmailTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seeds email templates from classpath HTML files into the database on startup (Story 10.2).
 *
 * Filename conventions:
 * - {@code {key}-{locale}.html}         → content template (isLayout=false)
 * - {@code layout-{key}-{locale}.html}  → layout template  (isLayout=true, category=LAYOUT)
 *
 * Seeding is idempotent: inserts only if (templateKey, locale) not already in DB.
 * Failures for individual templates are logged and skipped; remaining templates continue.
 */
@Slf4j
@Service
public class EmailTemplateSeedService {

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^(?:(layout)-)?(.+)-(de|en)\\.html$");

    private static final Pattern SUBJECT_COMMENT_PATTERN =
            Pattern.compile("^<!--\\s*subject:\\s*(.+?)\\s*-->\\r?\\n?");

    private final EmailTemplateRepository emailTemplateRepository;

    public EmailTemplateSeedService(EmailTemplateRepository emailTemplateRepository) {
        this.emailTemplateRepository = emailTemplateRepository;
    }

    @PostConstruct
    public void seedTemplatesFromClasspath() {
        log.info("Seeding email templates from classpath...");
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath*:email-templates/*.html");
        } catch (IOException e) {
            log.error("Failed to scan email-templates classpath directory: {}", e.getMessage());
            return;
        }

        int seeded = 0;
        int skipped = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            ParsedFilename parsed = parseFilename(filename);
            if (parsed == null) {
                log.warn("Skipping unrecognised email template filename: {}", filename);
                continue;
            }
            try {
                String rawHtml = resource.getContentAsString(StandardCharsets.UTF_8);
                String subject = parsed.isLayout() ? null : parseSubject(rawHtml);
                String htmlBody = subject != null ? stripSubjectComment(rawHtml) : rawHtml;
                String category = parsed.isLayout() ? "LAYOUT" : deriveCategory(parsed.templateKey());
                boolean existed = emailTemplateRepository.existsByTemplateKeyAndLocale(
                        parsed.templateKey(), parsed.locale());
                if (existed) {
                    skipped++;
                } else {
                    String layoutKey = parsed.isLayout() ? null : "batbern-default";
                    seedTemplate(parsed.templateKey(), parsed.locale(), parsed.isLayout(),
                            category, subject, htmlBody, layoutKey);
                    seeded++;
                }
            } catch (Exception e) {
                log.error("Failed to seed email template '{}': {}", filename, e.getMessage());
            }
        }
        log.info("Email template seeding complete: {} seeded, {} already existed", seeded, skipped);
    }

    /**
     * Seeds a single template into the DB. Package-visible for unit testing.
     * Checks idempotency (skips if already exists) and saves the entity.
     */
    void seedTemplate(String templateKey, String locale, boolean isLayout,
                      String category, String subject, String htmlBody, String layoutKey) {
        if (emailTemplateRepository.existsByTemplateKeyAndLocale(templateKey, locale)) {
            return;
        }
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey(templateKey);
        template.setLocale(locale);
        template.setLayout(isLayout);
        template.setCategory(category);
        template.setSubject(isLayout ? null : subject);
        template.setHtmlBody(htmlBody);
        template.setSystemTemplate(true);
        template.setLayoutKey(layoutKey);
        emailTemplateRepository.save(template);
    }

    /**
     * Extracts the subject line from an optional {@code <!-- subject: ... -->} comment
     * at the very start of the HTML file. Returns {@code null} if not present.
     */
    String parseSubject(String html) {
        Matcher m = SUBJECT_COMMENT_PATTERN.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Removes the {@code <!-- subject: ... -->} comment from the top of the HTML body
     * so it is not stored as visible content in the template.
     */
    String stripSubjectComment(String html) {
        return SUBJECT_COMMENT_PATTERN.matcher(html).replaceFirst("").trim();
    }

    /**
     * Parses an email template HTML filename into its components.
     * Returns null for filenames that don't match the expected pattern.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code layout-batbern-default-de.html} → key=batbern-default, locale=de, isLayout=true</li>
     *   <li>{@code speaker-invitation-en.html} → key=speaker-invitation, locale=en, isLayout=false</li>
     *   <li>{@code speaker-reminder-content-tier1-de.html} → key=speaker-reminder-content-tier1, locale=de</li>
     * </ul>
     */
    ParsedFilename parseFilename(String filename) {
        Matcher m = FILENAME_PATTERN.matcher(filename);
        if (!m.matches()) {
            return null;
        }
        boolean isLayout = "layout".equals(m.group(1));
        String key = m.group(2);
        String locale = m.group(3);
        return new ParsedFilename(key, locale, isLayout);
    }

    /**
     * Derives the template category from the key prefix.
     */
    String deriveCategory(String templateKey) {
        if (templateKey.startsWith("speaker-")) {
            return "SPEAKER";
        }
        if (templateKey.startsWith("registration-")) {
            return "REGISTRATION";
        }
        if (templateKey.startsWith("task-reminder")) {
            return "TASK_REMINDER";
        }
        return "LAYOUT";
    }

    /**
     * Parsed components from an email template filename.
     */
    public record ParsedFilename(String templateKey, String locale, boolean isLayout) {}
}
