package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EmailTemplateSeedService (Story 10.2 — Task 2d).
 *
 * Tests core seeding logic including:
 * - Filename parsing (layout vs content templates)
 * - Category derivation from key prefix
 * - Idempotency (skip if (templateKey, locale) already in DB)
 * - Seed failure handling (log error, continue)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailTemplateSeedService Unit Tests")
class EmailTemplateSeedServiceTest {

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @InjectMocks
    private EmailTemplateSeedService emailTemplateSeedService;

    @Captor
    private ArgumentCaptor<EmailTemplate> templateCaptor;

    @BeforeEach
    void setUp() {
        // By default, no templates exist (idempotency base case: all absent)
        when(emailTemplateRepository.existsByTemplateKeyAndLocale(anyString(), anyString()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("should derive SPEAKER category for speaker-* template keys")
    void should_deriveCategory_forSpeakerKey() {
        String category = emailTemplateSeedService.deriveCategory("speaker-invitation");
        assertThat(category).isEqualTo("SPEAKER");
    }

    @Test
    @DisplayName("should derive REGISTRATION category for registration-* template keys")
    void should_deriveCategory_forRegistrationKey() {
        String category = emailTemplateSeedService.deriveCategory("registration-confirmation");
        assertThat(category).isEqualTo("REGISTRATION");
    }

    @Test
    @DisplayName("should derive TASK_REMINDER category for task-reminder-* template keys")
    void should_deriveCategory_forTaskReminderKey() {
        String category = emailTemplateSeedService.deriveCategory("task-reminder-overdue");
        assertThat(category).isEqualTo("TASK_REMINDER");
    }

    @Test
    @DisplayName("should derive TASK_REMINDER category for plain 'task-reminder' key (from task-reminder-de.html)")
    void should_deriveCategory_forPlainTaskReminderKey() {
        // task-reminder-de.html parses to key="task-reminder" (locale stripped) — must map to TASK_REMINDER
        String category = emailTemplateSeedService.deriveCategory("task-reminder");
        assertThat(category).isEqualTo("TASK_REMINDER");
    }

    @Test
    @DisplayName("should return LAYOUT category for layout template keys")
    void should_deriveCategory_forLayoutKey() {
        String category = emailTemplateSeedService.deriveCategory("batbern-default");
        assertThat(category).isEqualTo("LAYOUT");
    }

    @Test
    @DisplayName("should parse filename: layout-batbern-default-de.html → key=batbern-default, locale=de, isLayout=true")
    void should_parseLayoutFilename() {
        EmailTemplateSeedService.ParsedFilename parsed =
                emailTemplateSeedService.parseFilename("layout-batbern-default-de.html");

        assertThat(parsed).isNotNull();
        assertThat(parsed.templateKey()).isEqualTo("batbern-default");
        assertThat(parsed.locale()).isEqualTo("de");
        assertThat(parsed.isLayout()).isTrue();
    }

    @Test
    @DisplayName("should parse filename: speaker-invitation-en.html → key=speaker-invitation, locale=en, isLayout=false")
    void should_parseContentFilename() {
        EmailTemplateSeedService.ParsedFilename parsed =
                emailTemplateSeedService.parseFilename("speaker-invitation-en.html");

        assertThat(parsed).isNotNull();
        assertThat(parsed.templateKey()).isEqualTo("speaker-invitation");
        assertThat(parsed.locale()).isEqualTo("en");
        assertThat(parsed.isLayout()).isFalse();
    }

    @Test
    @DisplayName("should parse multi-segment filename: speaker-reminder-content-tier1-de.html")
    void should_parseMultiSegmentFilename() {
        EmailTemplateSeedService.ParsedFilename parsed =
                emailTemplateSeedService.parseFilename("speaker-reminder-content-tier1-de.html");

        assertThat(parsed).isNotNull();
        assertThat(parsed.templateKey()).isEqualTo("speaker-reminder-content-tier1");
        assertThat(parsed.locale()).isEqualTo("de");
        assertThat(parsed.isLayout()).isFalse();
    }

    @Test
    @DisplayName("should skip seeding when (templateKey, locale) already exists in DB (idempotency)")
    void should_skipSeeding_whenAlreadyExists() {
        when(emailTemplateRepository.existsByTemplateKeyAndLocale("speaker-invitation", "de"))
                .thenReturn(true);

        emailTemplateSeedService.seedTemplate("speaker-invitation", "de", false, "SPEAKER",
                null, "<html>body</html>");

        verify(emailTemplateRepository, never()).save(templateCaptor.capture());
    }

    @Test
    @DisplayName("should insert template when (templateKey, locale) not in DB")
    void should_insert_whenNotExists() {
        when(emailTemplateRepository.existsByTemplateKeyAndLocale("speaker-invitation", "de"))
                .thenReturn(false);

        emailTemplateSeedService.seedTemplate("speaker-invitation", "de", false, "SPEAKER",
                null, "<html>body</html>");

        verify(emailTemplateRepository, times(1)).save(templateCaptor.capture());
        EmailTemplate saved = templateCaptor.getValue();
        assertThat(saved.getTemplateKey()).isEqualTo("speaker-invitation");
        assertThat(saved.getLocale()).isEqualTo("de");
        assertThat(saved.isLayout()).isFalse();
        assertThat(saved.getCategory()).isEqualTo("SPEAKER");
        assertThat(saved.isSystemTemplate()).isTrue();
        assertThat(saved.getLayoutKey()).isNull();
    }

    @Test
    @DisplayName("should save layout template with isLayout=true, category=LAYOUT, subject=null")
    void should_saveLayoutTemplate_withCorrectFields() {
        emailTemplateSeedService.seedTemplate("batbern-default", "de", true, "LAYOUT",
                null, "<html>{{content}}</html>");

        verify(emailTemplateRepository, times(1)).save(templateCaptor.capture());
        EmailTemplate saved = templateCaptor.getValue();
        assertThat(saved.isLayout()).isTrue();
        assertThat(saved.getCategory()).isEqualTo("LAYOUT");
        assertThat(saved.getSubject()).isNull();
        assertThat(saved.isSystemTemplate()).isTrue();
    }

    @Test
    @DisplayName("should seed from classpath and find at least 2 layout templates")
    void should_seedFromClasspath_andFindLayoutTemplates() {
        // This calls the actual @PostConstruct method, but with mocked repository
        // The classpath scan will find the real HTML files in src/main/resources
        emailTemplateSeedService.seedTemplatesFromClasspath();

        // Capture all saves
        verify(emailTemplateRepository, org.mockito.Mockito.atLeast(2))
                .save(templateCaptor.capture());

        List<EmailTemplate> allSaved = templateCaptor.getAllValues();
        long layoutCount = allSaved.stream().filter(EmailTemplate::isLayout).count();
        long contentCount = allSaved.stream().filter(t -> !t.isLayout()).count();

        assertThat(layoutCount).isGreaterThanOrEqualTo(2);
        assertThat(contentCount).isGreaterThanOrEqualTo(18);
    }

    @Test
    @DisplayName("should skip all templates when all already exist (idempotency — second run)")
    void should_skipAll_whenAllAlreadyExist() {
        // Simulate second run: all templates already present
        when(emailTemplateRepository.existsByTemplateKeyAndLocale(anyString(), anyString()))
                .thenReturn(true);

        emailTemplateSeedService.seedTemplatesFromClasspath();

        verify(emailTemplateRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
