package ch.batbern.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
