package ch.batbern.shared.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for SlugGenerationService
 *
 * Tests username generation, German character conversion, collision handling,
 * and session title slugification.
 */
@DisplayName("SlugGenerationService Tests")
class SlugGenerationServiceTest {

    private SlugGenerationService slugService;

    @BeforeEach
    void setUp() {
        slugService = new SlugGenerationService();
    }

    @Test
    @DisplayName("should_generateUsername_when_firstLastNameProvided")
    void should_generateUsername_when_firstLastNameProvided() {
        // Given
        String firstName = "John";
        String lastName = "Doe";

        // When
        String username = slugService.generateUsername(firstName, lastName);

        // Then
        assertThat(username).isEqualTo("john.doe");
    }

    @Test
    @DisplayName("should_generateUsername_when_namesHaveSpaces")
    void should_generateUsername_when_namesHaveSpaces() {
        // Given
        String firstName = "Mary Jane";
        String lastName = "Smith Watson";

        // When
        String username = slugService.generateUsername(firstName, lastName);

        // Then - should convert spaces to dots and lowercase
        assertThat(username).isEqualTo("mary.jane.smith.watson");
    }

    @Test
    @DisplayName("should_convertGermanChars_when_generating")
    void should_convertGermanChars_when_generating() {
        // Given - German characters
        String firstName = "Müller";
        String lastName = "Özdemir";

        // When
        String username = slugService.generateUsername(firstName, lastName);

        // Then - ü→ue, ö→oe
        assertThat(username).isEqualTo("mueller.oezdemir");
    }

    @Test
    @DisplayName("should_convertAllGermanChars_when_generating")
    void should_convertAllGermanChars_when_generating() {
        // Given - All German umlauts
        String firstName = "Äöü";
        String lastName = "Ströß";

        // When
        String username = slugService.generateUsername(firstName, lastName);

        // Then - Ä→ae, ö→oe, ü→ue, ß→ss
        assertThat(username).isEqualTo("aeoeue.stroess");
    }

    @Test
    @DisplayName("should_handleCollisions_when_usernameExists")
    void should_handleCollisions_when_usernameExists() {
        // Given - a function that simulates existing usernames
        Function<String, Boolean> existsChecker = username ->
            username.equals("john.doe") || username.equals("john.doe.2");

        String baseUsername = "john.doe";

        // When
        String uniqueUsername = slugService.ensureUniqueUsername(baseUsername, existsChecker);

        // Then - should append .3 since john.doe and john.doe.2 exist
        assertThat(uniqueUsername).isEqualTo("john.doe.3");
    }

    @Test
    @DisplayName("should_returnOriginal_when_noCollisionExists")
    void should_returnOriginal_when_noCollisionExists() {
        // Given - a function that says username doesn't exist
        Function<String, Boolean> existsChecker = username -> false;
        String baseUsername = "jane.smith";

        // When
        String uniqueUsername = slugService.ensureUniqueUsername(baseUsername, existsChecker);

        // Then - should return original since no collision
        assertThat(uniqueUsername).isEqualTo("jane.smith");
    }

    @Test
    @DisplayName("should_slugifySessionTitle_when_generating")
    void should_slugifySessionTitle_when_generating() {
        // Given
        String sessionTitle = "Introduction to Spring Boot & Microservices";

        // When
        String slug = slugService.generateSessionSlug(sessionTitle);

        // Then - should be lowercase, hyphenated, no special chars
        assertThat(slug).isEqualTo("introduction-to-spring-boot-microservices");
    }

    @Test
    @DisplayName("should_handleGermanCharsInSessionTitle_when_slugifying")
    void should_handleGermanCharsInSessionTitle_when_slugifying() {
        // Given - German characters in session title
        String sessionTitle = "Einführung in Künstliche Intelligenz für Zürich";

        // When
        String slug = slugService.generateSessionSlug(sessionTitle);

        // Then - convert umlauts and use hyphens
        assertThat(slug).isEqualTo("einfuehrung-in-kuenstliche-intelligenz-fuer-zuerich");
    }

    @Test
    @DisplayName("should_removeMultipleSpaces_when_slugifying")
    void should_removeMultipleSpaces_when_slugifying() {
        // Given - multiple spaces
        String sessionTitle = "Cloud    Computing   Architecture";

        // When
        String slug = slugService.generateSessionSlug(sessionTitle);

        // Then - should collapse multiple spaces to single hyphen
        assertThat(slug).isEqualTo("cloud-computing-architecture");
    }

    @Test
    @DisplayName("should_removeLeadingTrailingHyphens_when_slugifying")
    void should_removeLeadingTrailingHyphens_when_slugifying() {
        // Given - special chars at start/end
        String sessionTitle = "  @Event Management!  ";

        // When
        String slug = slugService.generateSessionSlug(sessionTitle);

        // Then - no leading/trailing hyphens
        assertThat(slug).isEqualTo("event-management");
    }

    @Test
    @DisplayName("should_throwException_when_firstNameIsNull")
    void should_throwException_when_firstNameIsNull() {
        // Given
        String firstName = null;
        String lastName = "Doe";

        // When/Then
        assertThatThrownBy(() -> slugService.generateUsername(firstName, lastName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("First name cannot be null or empty");
    }

    @Test
    @DisplayName("should_throwException_when_lastNameIsNull")
    void should_throwException_when_lastNameIsNull() {
        // Given
        String firstName = "John";
        String lastName = null;

        // When/Then
        assertThatThrownBy(() -> slugService.generateUsername(firstName, lastName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Last name cannot be null or empty");
    }

    @Test
    @DisplayName("should_throwException_when_sessionTitleIsNull")
    void should_throwException_when_sessionTitleIsNull() {
        // Given
        String sessionTitle = null;

        // When/Then
        assertThatThrownBy(() -> slugService.generateSessionSlug(sessionTitle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Session title cannot be null or empty");
    }

    @Test
    @DisplayName("should_throwException_when_baseUsernameIsNull")
    void should_throwException_when_baseUsernameIsNull() {
        // Given
        String baseUsername = null;
        Function<String, Boolean> existsChecker = username -> false;

        // When/Then
        assertThatThrownBy(() -> slugService.ensureUniqueUsername(baseUsername, existsChecker))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base username cannot be null or empty");
    }

    @Test
    @DisplayName("should_throwException_when_existsCheckerIsNull")
    void should_throwException_when_existsCheckerIsNull() {
        // Given
        String baseUsername = "john.doe";
        Function<String, Boolean> existsChecker = null;

        // When/Then
        assertThatThrownBy(() -> slugService.ensureUniqueUsername(baseUsername, existsChecker))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Exists checker function cannot be null");
    }
}
