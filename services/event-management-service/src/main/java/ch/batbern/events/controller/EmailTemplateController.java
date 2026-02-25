package ch.batbern.events.controller;

import ch.batbern.events.api.generated.EmailTemplatesApi;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.dto.generated.CreateEmailTemplateRequest;
import ch.batbern.events.dto.generated.EmailTemplateResponse;
import ch.batbern.events.dto.generated.UpdateEmailTemplateRequest;
import ch.batbern.events.mapper.EmailTemplateMapper;
import ch.batbern.events.service.EmailTemplateService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Email Template Management API (Story 10.2).
 *
 * Implements generated EmailTemplatesApi interface (ADR-006 contract-first).
 * All endpoints require ORGANIZER role.
 *
 * Endpoints:
 * - GET  /api/v1/email-templates               - list with optional category/isLayout filters
 * - GET  /api/v1/email-templates/{key}/{locale} - get single template
 * - POST /api/v1/email-templates               - create custom template
 * - PUT  /api/v1/email-templates/{key}/{locale} - update template
 * - DELETE /api/v1/email-templates/{key}/{locale} - delete custom template (400 for system/layout)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ORGANIZER')")
public class EmailTemplateController implements EmailTemplatesApi {

    private final EmailTemplateService emailTemplateService;
    private final EmailTemplateMapper emailTemplateMapper;

    @Override
    public ResponseEntity<List<EmailTemplateResponse>> listEmailTemplates(
            Optional<String> category, Optional<Boolean> isLayout) {
        log.debug("GET /api/v1/email-templates category={} isLayout={}", category, isLayout);
        List<EmailTemplate> templates = emailTemplateService.findAll(
                category.orElse(null), isLayout.orElse(null));
        return ResponseEntity.ok(templates.stream()
                .map(emailTemplateMapper::toResponse)
                .toList());
    }

    @Override
    public ResponseEntity<EmailTemplateResponse> getEmailTemplate(String templateKey, String locale) {
        log.debug("GET /api/v1/email-templates/{}/{}", templateKey, locale);
        return emailTemplateService.findByKeyAndLocale(templateKey, locale)
                .map(emailTemplateMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<EmailTemplateResponse> createEmailTemplate(
            CreateEmailTemplateRequest createEmailTemplateRequest) {
        log.debug("POST /api/v1/email-templates key={}", createEmailTemplateRequest.getTemplateKey());
        EmailTemplate created = emailTemplateService.create(createEmailTemplateRequest);
        EmailTemplateResponse response = emailTemplateMapper.toResponse(created);
        return ResponseEntity.created(
                URI.create("/api/v1/email-templates/"
                        + created.getTemplateKey() + "/" + created.getLocale()))
                .body(response);
    }

    @Override
    public ResponseEntity<EmailTemplateResponse> updateEmailTemplate(
            String templateKey, String locale, UpdateEmailTemplateRequest updateEmailTemplateRequest) {
        log.debug("PUT /api/v1/email-templates/{}/{}", templateKey, locale);
        try {
            EmailTemplate updated = emailTemplateService.update(templateKey, locale, updateEmailTemplateRequest);
            return ResponseEntity.ok(emailTemplateMapper.toResponse(updated));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Void> deleteEmailTemplate(String templateKey, String locale) {
        log.debug("DELETE /api/v1/email-templates/{}/{}", templateKey, locale);
        try {
            emailTemplateService.delete(templateKey, locale);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
