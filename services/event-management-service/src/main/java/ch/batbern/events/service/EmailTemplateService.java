package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.dto.generated.CreateEmailTemplateRequest;
import ch.batbern.events.dto.generated.UpdateEmailTemplateRequest;
import ch.batbern.events.repository.EmailTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for email template management (Story 10.2).
 *
 * Handles CRUD operations on email templates and the layout merge logic
 * used by email senders at send time.
 */
@Slf4j
@Service
@Transactional
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;

    public EmailTemplateService(EmailTemplateRepository emailTemplateRepository) {
        this.emailTemplateRepository = emailTemplateRepository;
    }

    /**
     * Returns all templates, optionally filtered by category and/or isLayout.
     */
    @Transactional(readOnly = true)
    public List<EmailTemplate> findAll(String category, Boolean isLayout) {
        if (category != null && isLayout != null) {
            // Both filters: load by category and filter isLayout in memory
            return emailTemplateRepository.findByCategory(category).stream()
                    .filter(t -> t.isLayout() == isLayout)
                    .toList();
        }
        if (category != null) {
            return emailTemplateRepository.findByCategory(category);
        }
        if (Boolean.TRUE.equals(isLayout)) {
            return emailTemplateRepository.findByLayoutTrue();
        }
        if (Boolean.FALSE.equals(isLayout)) {
            return emailTemplateRepository.findAll().stream()
                    .filter(t -> !t.isLayout())
                    .toList();
        }
        return emailTemplateRepository.findAll();
    }

    /**
     * Returns a single template by key and locale.
     */
    @Transactional(readOnly = true)
    public Optional<EmailTemplate> findByKeyAndLocale(String templateKey, String locale) {
        return emailTemplateRepository.findByTemplateKeyAndLocale(templateKey, locale);
    }

    /**
     * Creates a custom (non-system) email template.
     * Content templates must have a non-null subject.
     */
    private static final java.util.regex.Pattern TEMPLATE_KEY_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public EmailTemplate create(CreateEmailTemplateRequest request) {
        if (!TEMPLATE_KEY_PATTERN.matcher(request.getTemplateKey()).matches()) {
            throw new IllegalArgumentException(
                    "Template key must be kebab-case (lowercase letters, digits, hyphens): "
                            + request.getTemplateKey());
        }
        boolean isLayout = Boolean.TRUE.equals(request.getIsLayout());
        if (!isLayout && (request.getSubject() == null || request.getSubject().isBlank())) {
            throw new IllegalArgumentException("Content templates must have a non-empty subject");
        }
        if (emailTemplateRepository.findByTemplateKeyAndLocale(
                request.getTemplateKey(), request.getLocale()).isPresent()) {
            throw new IllegalStateException(
                    "Template already exists: " + request.getTemplateKey() + " (" + request.getLocale() + ")");
        }
        EmailTemplate template = new EmailTemplate();
        template.setTemplateKey(request.getTemplateKey());
        template.setLocale(request.getLocale());
        template.setCategory(request.getCategory() != null
                ? request.getCategory().getValue() : "SPEAKER");
        template.setSubject(isLayout ? null : request.getSubject());
        template.setHtmlBody(request.getHtmlBody());
        template.setLayout(isLayout);
        template.setLayoutKey(request.getLayoutKey());
        template.setSystemTemplate(false);
        return emailTemplateRepository.save(template);
    }

    /**
     * Updates subject, htmlBody and/or layoutKey of an existing template.
     */
    public EmailTemplate update(String templateKey, String locale, UpdateEmailTemplateRequest request) {
        EmailTemplate template = emailTemplateRepository
                .findByTemplateKeyAndLocale(templateKey, locale)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Email template not found: " + templateKey + "/" + locale));
        if (request.getSubject() != null) {
            template.setSubject(request.getSubject());
        }
        if (request.getHtmlBody() != null) {
            template.setHtmlBody(request.getHtmlBody());
        }
        if (request.getLayoutKey() != null) {
            template.setLayoutKey(request.getLayoutKey());
        }
        return emailTemplateRepository.save(template);
    }

    /**
     * Deletes a custom (non-system, non-layout) template.
     * System templates and layout templates cannot be deleted.
     */
    public void delete(String templateKey, String locale) {
        EmailTemplate template = emailTemplateRepository
                .findByTemplateKeyAndLocale(templateKey, locale)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Email template not found: " + templateKey + "/" + locale));
        if (template.isLayout()) {
            throw new IllegalStateException(
                    "Cannot delete layout template — it is referenced by content templates: " + templateKey);
        }
        if (template.isSystemTemplate()) {
            throw new IllegalStateException("Cannot delete system template: " + templateKey);
        }
        emailTemplateRepository.delete(template);
    }

    /**
     * Returns the stored subject for a template, if present and non-blank.
     * Caller should run replaceVariables() on the result before using it as an email subject.
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveSubject(String templateKey, String locale) {
        return findByKeyAndLocale(templateKey, locale)
                .map(EmailTemplate::getSubject)
                .filter(s -> s != null && !s.isBlank());
    }

    /**
     * Merges content HTML with a layout template at the {{content}} placeholder.
     *
     * If the layout template is not found in DB, logs a WARN and returns contentHtml directly
     * (graceful degradation — email is sent without layout branding).
     *
     * @param contentHtml the HTML to inject into the layout
     * @param layoutKey   the key of the layout template
     * @param locale      the locale of the layout template
     * @return merged HTML, or contentHtml if layout not found
     */
    @Transactional(readOnly = true)
    public String mergeWithLayout(String contentHtml, String layoutKey, String locale) {
        Optional<EmailTemplate> layoutTemplate =
                emailTemplateRepository.findByTemplateKeyAndLocale(layoutKey, locale);
        if (layoutTemplate.isEmpty()) {
            log.warn("Layout template '{}' locale '{}' not found in DB — sending email without layout",
                    layoutKey, locale);
            return contentHtml;
        }
        return layoutTemplate.get().getHtmlBody().replace("{{content}}", contentHtml);
    }
}
