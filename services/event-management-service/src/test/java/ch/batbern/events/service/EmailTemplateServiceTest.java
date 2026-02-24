package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.dto.generated.CreateEmailTemplateRequest;
import ch.batbern.events.dto.generated.UpdateEmailTemplateRequest;
import ch.batbern.events.repository.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EmailTemplateService (Story 10.2 — Task 2e).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTemplateService Unit Tests")
class EmailTemplateServiceTest {

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @InjectMocks
    private EmailTemplateService emailTemplateService;

    @Captor
    private ArgumentCaptor<EmailTemplate> templateCaptor;

    private EmailTemplate speakerInvitationDe;
    private EmailTemplate layoutBatbernDefaultDe;

    @BeforeEach
    void setUp() {
        speakerInvitationDe = new EmailTemplate();
        speakerInvitationDe.setTemplateKey("speaker-invitation");
        speakerInvitationDe.setLocale("de");
        speakerInvitationDe.setCategory("SPEAKER");
        speakerInvitationDe.setSubject("Einladung als Referent");
        speakerInvitationDe.setHtmlBody("<html>body</html>");
        speakerInvitationDe.setSystemTemplate(true);
        speakerInvitationDe.setLayout(false);

        layoutBatbernDefaultDe = new EmailTemplate();
        layoutBatbernDefaultDe.setTemplateKey("batbern-default");
        layoutBatbernDefaultDe.setLocale("de");
        layoutBatbernDefaultDe.setCategory("LAYOUT");
        layoutBatbernDefaultDe.setHtmlBody("<html>{{content}}</html>");
        layoutBatbernDefaultDe.setSystemTemplate(true);
        layoutBatbernDefaultDe.setLayout(true);
    }

    // ── findAll ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll without filters returns all templates")
    void findAll_noFilter_returnsAll() {
        when(emailTemplateRepository.findAll()).thenReturn(List.of(speakerInvitationDe, layoutBatbernDefaultDe));

        List<EmailTemplate> result = emailTemplateService.findAll(null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findAll filtered by category returns matching templates")
    void findAll_categoryFilter_returnsFiltered() {
        when(emailTemplateRepository.findByCategory("SPEAKER")).thenReturn(List.of(speakerInvitationDe));

        List<EmailTemplate> result = emailTemplateService.findAll("SPEAKER", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("SPEAKER");
    }

    @Test
    @DisplayName("findAll filtered by isLayout=true returns layout templates")
    void findAll_isLayoutFilter_returnsLayouts() {
        when(emailTemplateRepository.findByLayoutTrue()).thenReturn(List.of(layoutBatbernDefaultDe));

        List<EmailTemplate> result = emailTemplateService.findAll(null, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isLayout()).isTrue();
    }

    // ── findByKeyAndLocale ───────────────────────────────────────────────────

    @Test
    @DisplayName("findByKeyAndLocale returns template when found")
    void findByKeyAndLocale_found_returnsTemplate() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale("speaker-invitation", "de"))
                .thenReturn(Optional.of(speakerInvitationDe));

        Optional<EmailTemplate> result = emailTemplateService.findByKeyAndLocale("speaker-invitation", "de");

        assertThat(result).isPresent();
        assertThat(result.get().getTemplateKey()).isEqualTo("speaker-invitation");
    }

    @Test
    @DisplayName("findByKeyAndLocale returns empty when not found")
    void findByKeyAndLocale_notFound_returnsEmpty() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Optional<EmailTemplate> result = emailTemplateService.findByKeyAndLocale("nonexistent", "de");

        assertThat(result).isEmpty();
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create content template without subject throws IllegalArgumentException")
    void create_contentTemplate_withoutSubject_throws() {
        CreateEmailTemplateRequest request = new CreateEmailTemplateRequest();
        request.setTemplateKey("my-custom");
        request.setLocale("de");
        request.setCategory(CreateEmailTemplateRequest.CategoryEnum.SPEAKER);
        request.setHtmlBody("<p>Hello</p>");
        request.setIsLayout(false);
        // subject not set

        assertThatThrownBy(() -> emailTemplateService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }

    @Test
    @DisplayName("create content template with subject saves template as non-system")
    void create_contentTemplate_withSubject_saves() {
        CreateEmailTemplateRequest request = new CreateEmailTemplateRequest();
        request.setTemplateKey("my-custom");
        request.setLocale("de");
        request.setCategory(CreateEmailTemplateRequest.CategoryEnum.SPEAKER);
        request.setSubject("Betreff");
        request.setHtmlBody("<p>Hello</p>");
        request.setIsLayout(false);
        request.setLayoutKey("batbern-default");

        when(emailTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailTemplate saved = emailTemplateService.create(request);

        assertThat(saved.isSystemTemplate()).isFalse();
        assertThat(saved.getTemplateKey()).isEqualTo("my-custom");
        assertThat(saved.getLayoutKey()).isEqualTo("batbern-default");
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update updates subject, htmlBody and layoutKey")
    void update_updatesFields() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale("speaker-invitation", "de"))
                .thenReturn(Optional.of(speakerInvitationDe));
        when(emailTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateEmailTemplateRequest request = new UpdateEmailTemplateRequest();
        request.setSubject("Neuer Betreff");
        request.setHtmlBody("<html>new body</html>");
        request.setLayoutKey("batbern-default");

        EmailTemplate updated = emailTemplateService.update("speaker-invitation", "de", request);

        assertThat(updated.getSubject()).isEqualTo("Neuer Betreff");
        assertThat(updated.getHtmlBody()).isEqualTo("<html>new body</html>");
        assertThat(updated.getLayoutKey()).isEqualTo("batbern-default");
    }

    @Test
    @DisplayName("update throws when template not found")
    void update_notFound_throws() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale(anyString(), anyString()))
                .thenReturn(Optional.empty());

        UpdateEmailTemplateRequest request = new UpdateEmailTemplateRequest();
        request.setHtmlBody("<html>x</html>");

        assertThatThrownBy(() -> emailTemplateService.update("nonexistent", "de", request))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete system template throws IllegalStateException")
    void delete_systemTemplate_throws() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale("speaker-invitation", "de"))
                .thenReturn(Optional.of(speakerInvitationDe));

        assertThatThrownBy(() -> emailTemplateService.delete("speaker-invitation", "de"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("system");
    }

    @Test
    @DisplayName("delete layout template throws IllegalStateException")
    void delete_layoutTemplate_throws() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale("batbern-default", "de"))
                .thenReturn(Optional.of(layoutBatbernDefaultDe));

        assertThatThrownBy(() -> emailTemplateService.delete("batbern-default", "de"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("layout");
    }

    @Test
    @DisplayName("delete custom non-layout template deletes successfully")
    void delete_customTemplate_deletesSuccessfully() {
        EmailTemplate customTemplate = new EmailTemplate();
        customTemplate.setTemplateKey("my-custom");
        customTemplate.setLocale("de");
        customTemplate.setSystemTemplate(false);
        customTemplate.setLayout(false);

        when(emailTemplateRepository.findByTemplateKeyAndLocale("my-custom", "de"))
                .thenReturn(Optional.of(customTemplate));

        emailTemplateService.delete("my-custom", "de");

        verify(emailTemplateRepository, times(1)).delete(customTemplate);
    }

    @Test
    @DisplayName("delete throws when template not found")
    void delete_notFound_throws() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailTemplateService.delete("nonexistent", "de"))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ── mergeWithLayout ──────────────────────────────────────────────────────

    @Test
    @DisplayName("mergeWithLayout replaces {{content}} with content HTML")
    void mergeWithLayout_replacesContentPlaceholder() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale("batbern-default", "de"))
                .thenReturn(Optional.of(layoutBatbernDefaultDe));

        String result = emailTemplateService.mergeWithLayout("<p>Hello</p>", "batbern-default", "de");

        assertThat(result).isEqualTo("<html><p>Hello</p></html>");
    }

    @Test
    @DisplayName("mergeWithLayout returns contentHtml directly when layout not found")
    void mergeWithLayout_layoutNotFound_returnsContentDirectly() {
        when(emailTemplateRepository.findByTemplateKeyAndLocale("missing-layout", "de"))
                .thenReturn(Optional.empty());

        String result = emailTemplateService.mergeWithLayout("<p>Hello</p>", "missing-layout", "de");

        assertThat(result).isEqualTo("<p>Hello</p>");
    }
}
