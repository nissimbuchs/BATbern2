package ch.batbern.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test suite for EmailService template variable replacement functionality.
 *
 * Tests simple variable replacement {{varName}} and Mustache-style
 * conditional blocks {{#varName}}...{{/varName}}.
 */
@DisplayName("EmailService Tests")
class EmailServiceTest {

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // EmailService uses @Autowired(required=false) for SesClient — safe to instantiate directly
        emailService = new EmailService();
    }

    @Nested
    @DisplayName("Simple Variable Replacement")
    class SimpleVariableReplacement {

        @Test
        @DisplayName("should_replaceVariable_when_valueProvided")
        void should_replaceVariable_when_valueProvided() {
            // Given
            String template = "Hello {{firstName}}!";
            Map<String, String> variables = Map.of("firstName", "John");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Hello John!");
        }

        @Test
        @DisplayName("should_replaceMultipleVariables_when_multipleProvided")
        void should_replaceMultipleVariables_when_multipleProvided() {
            // Given
            String template = "Hello {{firstName}} {{lastName}}, welcome to {{eventTitle}}!";
            Map<String, String> variables = Map.of(
                    "firstName", "John",
                    "lastName", "Doe",
                    "eventTitle", "BATbern 2026"
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Hello John Doe, welcome to BATbern 2026!");
        }

        @Test
        @DisplayName("should_replaceWithEmptyString_when_valueIsNull")
        void should_replaceWithEmptyString_when_valueIsNull() {
            // Given
            String template = "Hello {{firstName}}!";
            Map<String, String> variables = new java.util.HashMap<>();
            variables.put("firstName", null);

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Hello !");
        }

        @Test
        @DisplayName("should_replaceWithEmptyString_when_valueIsEmpty")
        void should_replaceWithEmptyString_when_valueIsEmpty() {
            // Given
            String template = "Hello {{firstName}}!";
            Map<String, String> variables = Map.of("firstName", "");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Hello !");
        }

        @Test
        @DisplayName("should_keepPlaceholder_when_variableNotProvided")
        void should_keepPlaceholder_when_variableNotProvided() {
            // Given
            String template = "Hello {{firstName}} {{lastName}}!";
            Map<String, String> variables = Map.of("firstName", "John");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - lastName placeholder remains since not in map
            assertThat(result).isEqualTo("Hello John {{lastName}}!");
        }
    }

    @Nested
    @DisplayName("Conditional Block Replacement")
    class ConditionalBlockReplacement {

        @Test
        @DisplayName("should_removeConditionalBlock_when_valueIsEmpty")
        void should_removeConditionalBlock_when_valueIsEmpty() {
            // Given
            String template = "Event Info{{#sessionTitle}}: {{sessionTitle}}{{/sessionTitle}}.";
            Map<String, String> variables = Map.of("sessionTitle", "");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Event Info.");
        }

        @Test
        @DisplayName("should_removeConditionalBlock_when_valueIsNull")
        void should_removeConditionalBlock_when_valueIsNull() {
            // Given
            String template = "Event Info{{#sessionTitle}}: {{sessionTitle}}{{/sessionTitle}}.";
            Map<String, String> variables = new java.util.HashMap<>();
            variables.put("sessionTitle", null);

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Event Info.");
        }

        @Test
        @DisplayName("should_removeConditionalBlock_when_valueIsBlank")
        void should_removeConditionalBlock_when_valueIsBlank() {
            // Given
            String template = "Event Info{{#sessionTitle}}: {{sessionTitle}}{{/sessionTitle}}.";
            Map<String, String> variables = Map.of("sessionTitle", "   ");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Event Info.");
        }

        @Test
        @DisplayName("should_keepContentAndRemoveTags_when_valueIsNonEmpty")
        void should_keepContentAndRemoveTags_when_valueIsNonEmpty() {
            // Given
            String template = "Event Info{{#sessionTitle}}: {{sessionTitle}}{{/sessionTitle}}.";
            Map<String, String> variables = Map.of("sessionTitle", "Introduction to AI");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("Event Info: Introduction to AI.");
        }

        @Test
        @DisplayName("should_handleMultilineConditionalBlock_when_present")
        void should_handleMultilineConditionalBlock_when_present() {
            // Given - multiline template like in email
            String template = """
                    <div>
                    {{#sessionTitle}}
                    <p>Session: {{sessionTitle}}</p>
                    {{/sessionTitle}}
                    </div>""";
            Map<String, String> variables = Map.of("sessionTitle", "My Talk");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("""
                    <div>

                    <p>Session: My Talk</p>

                    </div>""");
        }

        @Test
        @DisplayName("should_removeMultilineConditionalBlock_when_empty")
        void should_removeMultilineConditionalBlock_when_empty() {
            // Given - multiline template like in email
            String template = """
                    <div>
                    {{#sessionTitle}}
                    <p>Session: {{sessionTitle}}</p>
                    {{/sessionTitle}}
                    </div>""";
            Map<String, String> variables = Map.of("sessionTitle", "");

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - entire block removed
            assertThat(result).isEqualTo("""
                    <div>

                    </div>""");
        }

        @Test
        @DisplayName("should_handleNestedConditionalBlocks_when_bothHaveValues")
        void should_handleNestedConditionalBlocks_when_bothHaveValues() {
            // Given - nested conditionals like responseDeadline containing contentDeadline
            String template = """
                    {{#responseDeadline}}
                    Deadline: {{responseDeadline}}
                    {{#contentDeadline}}
                    Content due: {{contentDeadline}}
                    {{/contentDeadline}}
                    {{/responseDeadline}}""";
            Map<String, String> variables = Map.of(
                    "responseDeadline", "2026-02-25",
                    "contentDeadline", "2026-03-15"
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then
            assertThat(result).isEqualTo("""

                    Deadline: 2026-02-25

                    Content due: 2026-03-15

                    """);
        }

        @Test
        @DisplayName("should_handleNestedConditionalBlocks_when_innerIsEmpty")
        void should_handleNestedConditionalBlocks_when_innerIsEmpty() {
            // Given - outer has value, inner is empty
            String template = """
                    {{#responseDeadline}}
                    Deadline: {{responseDeadline}}
                    {{#contentDeadline}}
                    Content due: {{contentDeadline}}
                    {{/contentDeadline}}
                    {{/responseDeadline}}""";
            Map<String, String> variables = Map.of(
                    "responseDeadline", "2026-02-25",
                    "contentDeadline", ""
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - inner block removed, outer kept
            assertThat(result).isEqualTo("""

                    Deadline: 2026-02-25

                    """);
        }

        @Test
        @DisplayName("should_handleNestedConditionalBlocks_when_outerIsEmpty")
        void should_handleNestedConditionalBlocks_when_outerIsEmpty() {
            // Given - outer is empty, so entire block should be removed
            String template = """
                    {{#responseDeadline}}
                    Deadline: {{responseDeadline}}
                    {{#contentDeadline}}
                    Content due: {{contentDeadline}}
                    {{/contentDeadline}}
                    {{/responseDeadline}}""";
            Map<String, String> variables = Map.of(
                    "responseDeadline", "",
                    "contentDeadline", "2026-03-15"
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - entire outer block removed (including nested)
            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("should_handleMultipleIndependentConditionalBlocks")
        void should_handleMultipleIndependentConditionalBlocks() {
            // Given - multiple independent conditional blocks
            String template = """
                    {{#sessionTitle}}Session: {{sessionTitle}}{{/sessionTitle}}
                    {{#responseDeadline}}Deadline: {{responseDeadline}}{{/responseDeadline}}""";
            Map<String, String> variables = Map.of(
                    "sessionTitle", "My Talk",
                    "responseDeadline", ""
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - first block kept, second removed
            assertThat(result).isEqualTo("""
                    Session: My Talk
                    """);
        }
    }

    @Nested
    @DisplayName("Real Email Template Scenarios")
    class RealEmailTemplateScenarios {

        @Test
        @DisplayName("should_handleSpeakerInvitationTemplate_when_noSession")
        void should_handleSpeakerInvitationTemplate_when_noSession() {
            // Given - simulating the actual email template structure
            String template = """
                    <h1>Einladung als Referent</h1>
                    <p>Guten Tag {{speakerName}},</p>
                    {{#sessionTitle}}
                    <div class="session-info">
                        <h3>Vorgesehenes Thema</h3>
                        <p><strong>{{sessionTitle}}</strong></p>
                        {{#sessionDescription}}
                        <p>{{sessionDescription}}</p>
                        {{/sessionDescription}}
                    </div>
                    {{/sessionTitle}}
                    <p>Wir freuen uns auf Ihre Antwort!</p>""";

            Map<String, String> variables = Map.of(
                    "speakerName", "Max Mustermann",
                    "sessionTitle", "",
                    "sessionDescription", ""
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - session block completely removed
            assertThat(result).isEqualTo("""
                    <h1>Einladung als Referent</h1>
                    <p>Guten Tag Max Mustermann,</p>

                    <p>Wir freuen uns auf Ihre Antwort!</p>""");
        }

        @Test
        @DisplayName("should_handleSpeakerInvitationTemplate_when_sessionAssigned")
        void should_handleSpeakerInvitationTemplate_when_sessionAssigned() {
            // Given - simulating the actual email template structure with session
            String template = """
                    <h1>Einladung als Referent</h1>
                    <p>Guten Tag {{speakerName}},</p>
                    {{#sessionTitle}}
                    <div class="session-info">
                        <h3>Vorgesehenes Thema</h3>
                        <p><strong>{{sessionTitle}}</strong></p>
                        {{#sessionDescription}}
                        <p>{{sessionDescription}}</p>
                        {{/sessionDescription}}
                    </div>
                    {{/sessionTitle}}
                    <p>Wir freuen uns auf Ihre Antwort!</p>""";

            Map<String, String> variables = Map.of(
                    "speakerName", "Max Mustermann",
                    "sessionTitle", "Microservices mit Spring Boot",
                    "sessionDescription", "Ein Vortrag über moderne Architektur"
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - session block kept with content (use contains for whitespace flexibility)
            assertThat(result)
                    .contains("<h1>Einladung als Referent</h1>")
                    .contains("<p>Guten Tag Max Mustermann,</p>")
                    .contains("<div class=\"session-info\">")
                    .contains("<p><strong>Microservices mit Spring Boot</strong></p>")
                    .contains("<p>Ein Vortrag über moderne Architektur</p>")
                    .contains("<p>Wir freuen uns auf Ihre Antwort!</p>")
                    .doesNotContain("{{#sessionTitle}}")
                    .doesNotContain("{{/sessionTitle}}")
                    .doesNotContain("{{#sessionDescription}}")
                    .doesNotContain("{{/sessionDescription}}");
        }

        @Test
        @DisplayName("should_handleDeadlineBlock_when_deadlinesProvided")
        void should_handleDeadlineBlock_when_deadlinesProvided() {
            // Given - deadline block from template
            String template = """
                    {{#responseDeadline}}
                    <div class="deadline-box">
                        <h3>Bitte antworten Sie bis</h3>
                        <p>{{responseDeadline}}</p>
                        {{#contentDeadline}}
                        <p>Content deadline: {{contentDeadline}}</p>
                        {{/contentDeadline}}
                    </div>
                    {{/responseDeadline}}""";

            Map<String, String> variables = Map.of(
                    "responseDeadline", "25.02.2026",
                    "contentDeadline", "15.03.2026"
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - verify content is present and conditionals are processed
            assertThat(result)
                    .contains("<div class=\"deadline-box\">")
                    .contains("<h3>Bitte antworten Sie bis</h3>")
                    .contains("<p>25.02.2026</p>")
                    .contains("<p>Content deadline: 15.03.2026</p>")
                    .doesNotContain("{{#responseDeadline}}")
                    .doesNotContain("{{/responseDeadline}}")
                    .doesNotContain("{{#contentDeadline}}")
                    .doesNotContain("{{/contentDeadline}}");
        }

        @Test
        @DisplayName("should_handleDeadlineBlock_when_noDeadlines")
        void should_handleDeadlineBlock_when_noDeadlines() {
            // Given - deadline block from template with no deadlines
            String template = """
                    {{#responseDeadline}}
                    <div class="deadline-box">
                        <h3>Bitte antworten Sie bis</h3>
                        <p>{{responseDeadline}}</p>
                        {{#contentDeadline}}
                        <p>Content deadline: {{contentDeadline}}</p>
                        {{/contentDeadline}}
                    </div>
                    {{/responseDeadline}}""";

            Map<String, String> variables = Map.of(
                    "responseDeadline", "",
                    "contentDeadline", ""
            );

            // When
            String result = emailService.replaceVariables(template, variables);

            // Then - entire block removed
            assertThat(result).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Configuration Set Application (bounce processing pipeline)")
    class ConfigurationSetApplication {

        private SesClient sesClient;

        @BeforeEach
        void wireMockSes() {
            sesClient = mock(SesClient.class);
            ReflectionTestUtils.setField(emailService, "sesClient", sesClient);
            ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@batbern.ch");
            ReflectionTestUtils.setField(emailService, "fromName", "BATbern");
            ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
            when(sesClient.sendEmail(any(SendEmailRequest.class)))
                    .thenReturn(SendEmailResponse.builder().messageId("test-id").build());
            when(sesClient.sendRawEmail(any(SendRawEmailRequest.class)))
                    .thenReturn(SendRawEmailResponse.builder().messageId("test-id").build());
        }

        @Test
        @DisplayName("should_attachConfigurationSet_when_simpleSendAndPropertyConfigured")
        void should_attachConfigurationSet_when_simpleSendAndPropertyConfigured() {
            ReflectionTestUtils.setField(emailService, "configurationSetName", "batbern-staging-newsletter");

            emailService.sendHtmlEmailSync("user@batbern.ch", "subj", "<p>body</p>");

            ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
            verify(sesClient).sendEmail(captor.capture());
            assertThat(captor.getValue().configurationSetName()).isEqualTo("batbern-staging-newsletter");
        }

        @Test
        @DisplayName("should_omitConfigurationSet_when_propertyNotConfigured")
        void should_omitConfigurationSet_when_propertyNotConfigured() {
            ReflectionTestUtils.setField(emailService, "configurationSetName", null);

            emailService.sendHtmlEmailSync("user@batbern.ch", "subj", "<p>body</p>");

            ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
            verify(sesClient).sendEmail(captor.capture());
            assertThat(captor.getValue().configurationSetName()).isNull();
        }

        @Test
        @DisplayName("should_attachConfigurationSet_when_rawSendWithAttachmentsAndPropertyConfigured")
        void should_attachConfigurationSet_when_rawSendWithAttachmentsAndPropertyConfigured() {
            ReflectionTestUtils.setField(emailService, "configurationSetName", "batbern-staging-newsletter");

            emailService.sendHtmlEmailWithAttachments(
                    "user@batbern.ch", "subj", "<p>body</p>",
                    List.of(new EmailService.EmailAttachment("event.ics",
                            "BEGIN:VCALENDAR\nEND:VCALENDAR".getBytes(),
                            "text/calendar", true)));

            ArgumentCaptor<SendRawEmailRequest> captor = ArgumentCaptor.forClass(SendRawEmailRequest.class);
            verify(sesClient).sendRawEmail(captor.capture());
            assertThat(captor.getValue().configurationSetName()).isEqualTo("batbern-staging-newsletter");
        }

        @Test
        @DisplayName("should_omitConfigurationSet_when_rawSendWithAttachmentsAndPropertyNotConfigured")
        void should_omitConfigurationSet_when_rawSendWithAttachmentsAndPropertyNotConfigured() {
            ReflectionTestUtils.setField(emailService, "configurationSetName", null);

            emailService.sendHtmlEmailWithAttachments(
                    "user@batbern.ch", "subj", "<p>body</p>",
                    List.of(new EmailService.EmailAttachment("event.ics",
                            "BEGIN:VCALENDAR\nEND:VCALENDAR".getBytes(),
                            "text/calendar", true)));

            ArgumentCaptor<SendRawEmailRequest> captor = ArgumentCaptor.forClass(SendRawEmailRequest.class);
            verify(sesClient).sendRawEmail(captor.capture());
            assertThat(captor.getValue().configurationSetName()).isNull();
        }

        @Test
        @DisplayName("should_useExplicitOverride_when_configurationSetPassedToFourArgSync")
        void should_useExplicitOverride_when_configurationSetPassedToFourArgSync() {
            // Even with the bean-level value set, an explicit override (used by NewsletterEmailService)
            // must take precedence — the 4-arg sync method is the explicit-override entry point.
            ReflectionTestUtils.setField(emailService, "configurationSetName", "default-cs");

            emailService.sendHtmlEmailSync("user@batbern.ch", "subj", "<p>body</p>", "explicit-override-cs");

            ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
            verify(sesClient).sendEmail(captor.capture());
            assertThat(captor.getValue().configurationSetName()).isEqualTo("explicit-override-cs");
        }
    }

    /**
     * Reserved-domain recipient filter — RFC 2606 / RFC 6761.
     *
     * Goal: never burn SES quota or bounce reputation on test/scanner addresses
     * (zaproxy@example.com, anything@*.test, etc.). The check sits at the SES
     * boundary so every send path is covered uniformly — registration, deregister,
     * speaker invite, partner invite, task reminder, newsletter, …
     */
    @Nested
    @DisplayName("Reserved Recipient Domain Filter (RFC 2606 / RFC 6761)")
    class ReservedRecipientDomainFilter {

        private SesClient sesClient;

        @BeforeEach
        void wireMockSes() {
            sesClient = mock(SesClient.class);
            ReflectionTestUtils.setField(emailService, "sesClient", sesClient);
            ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@batbern.ch");
            ReflectionTestUtils.setField(emailService, "fromName", "BATbern");
            ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
        }

        @Test
        @DisplayName("should_throwAndSkipSes_when_simpleSendToReservedDomain")
        void should_throwAndSkipSes_when_simpleSendToReservedDomain() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    ReservedEmailRecipientException.class,
                    () -> emailService.sendHtmlEmailSync("zaproxy@example.com", "subj", "<p>body</p>")
            );
            org.mockito.Mockito.verifyNoInteractions(sesClient);
        }

        @Test
        @DisplayName("should_throwAndSkipSes_when_rawSendWithAttachmentsToReservedDomain")
        void should_throwAndSkipSes_when_rawSendWithAttachmentsToReservedDomain() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    ReservedEmailRecipientException.class,
                    () -> emailService.sendHtmlEmailWithAttachments(
                            "zaproxy@example.com", "subj", "<p>body</p>",
                            List.of(new EmailService.EmailAttachment("event.ics",
                                    "BEGIN:VCALENDAR\nEND:VCALENDAR".getBytes(),
                                    "text/calendar", true)))
            );
            org.mockito.Mockito.verifyNoInteractions(sesClient);
        }

        @Test
        @DisplayName("should_throwAndSkipSes_when_syncWithAttachmentsToReservedDomain")
        void should_throwAndSkipSes_when_syncWithAttachmentsToReservedDomain() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    ReservedEmailRecipientException.class,
                    () -> emailService.sendHtmlEmailSyncWithAttachments(
                            "user@example.test", "subj", "<p>body</p>", List.of(), null)
            );
            org.mockito.Mockito.verifyNoInteractions(sesClient);
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] {0}")
        @org.junit.jupiter.params.provider.ValueSource(strings = {
                "anyone@example.com",
                "anyone@example.org",
                "anyone@example.net",
                "root@localhost",
                "user@anything.test",
                "user@anything.invalid",
                "user@anything.example",
                "user@anything.localhost",
                "USER@EXAMPLE.COM",                 // case-insensitive
                "User@SubDomain.Example.com"        // sub of example.com (matches *.example TLD-suffix rule)
        })
        @DisplayName("isReservedRecipientForSes — covers all RFC 2606/6761 reserved patterns")
        void should_recognizeReservedRecipient_when_anyKnownPattern(String recipient) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    ReservedEmailRecipientException.class,
                    () -> emailService.sendHtmlEmailSync(recipient, "subj", "<p>body</p>")
            );
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] {0}")
        @org.junit.jupiter.params.provider.ValueSource(strings = {
                "user@batbern.ch",
                "info@swisscom.com",
                "speaker@example-company.ch",       // contains the substring "example" but not as reserved
                "test@batbern.ch"                    // local-part "test" is fine; only the domain matters
        })
        @DisplayName("isReservedRecipientForSes — does NOT block legitimate domains")
        void should_passThrough_when_legitimateDomain(String recipient) {
            when(sesClient.sendEmail(any(SendEmailRequest.class)))
                    .thenReturn(SendEmailResponse.builder().messageId("test-id").build());

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> emailService.sendHtmlEmailSync(recipient, "subj", "<p>body</p>")
            );
            verify(sesClient).sendEmail(any(SendEmailRequest.class));
        }
    }
}
