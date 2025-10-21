package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserPreferences;
import ch.batbern.companyuser.domain.UserSettings;
import ch.batbern.shared.test.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for UserRepository
 * Story 1.14-2, AC: 1, 3, 4, 5 (User CRUD and Queries)
 * Story 1.16.2: Username-based lookups (CRITICAL)
 * Uses Testcontainers PostgreSQL for production parity
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("User Repository Tests")
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // AC1 Tests: User CRUD Operations

    @Test
    @DisplayName("should_saveUser_when_validUserProvided")
    void should_saveUser_when_validUserProvided() {
        // Given
        User user = createTestUser("john.doe", "john.doe@example.com", "John", "Doe");

        // When
        User saved = userRepository.save(user);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("john.doe");
        assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("should_findById_when_userExists")
    void should_findById_when_userExists() {
        // Given
        User user = createAndSaveUser("jane.smith", "jane@example.com");

        // When
        Optional<User> found = userRepository.findById(user.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("jane.smith");
    }

    @Test
    @DisplayName("should_updateUser_when_modificationsMade")
    void should_updateUser_when_modificationsMade() {
        // Given
        User user = createAndSaveUser("test.user", "test@example.com");

        // When
        user.setFirstName("Updated");
        user.setBio("New bio");
        user.onUpdate();
        User updated = userRepository.save(user);
        entityManager.flush();

        // Then
        User retrieved = userRepository.findById(updated.getId()).orElseThrow();
        assertThat(retrieved.getFirstName()).isEqualTo("Updated");
        assertThat(retrieved.getBio()).isEqualTo("New bio");
        assertThat(retrieved.getUpdatedAt()).isAfter(retrieved.getCreatedAt());
    }

    @Test
    @DisplayName("should_deleteUser_when_requested")
    void should_deleteUser_when_requested() {
        // Given
        User user = createAndSaveUser("delete.user", "delete@example.com");

        // When
        userRepository.delete(user);
        entityManager.flush();

        // Then
        Optional<User> deleted = userRepository.findById(user.getId());
        assertThat(deleted).isEmpty();
    }

    // AC3 Tests: Story 1.16.2 - Username-based lookups (CRITICAL)

    @Test
    @DisplayName("should_findByUsername_when_usernameProvided")
    void should_findByUsername_when_usernameProvided() {
        // Given
        createAndSaveUser("john.doe", "john@example.com");

        // When - Story 1.16.2: Primary lookup by username
        Optional<User> found = userRepository.findByUsername("john.doe");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("john.doe");
        assertThat(found.get().getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("should_returnEmpty_when_usernameNotFound")
    void should_returnEmpty_when_usernameNotFound() {
        // When
        Optional<User> found = userRepository.findByUsername("nonexistent.user");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should_checkExistence_when_existsByUsernameCalled")
    void should_checkExistence_when_existsByUsernameCalled() {
        // Given
        createAndSaveUser("existing.user", "existing@example.com");

        // When
        boolean exists = userRepository.existsByUsername("existing.user");
        boolean notExists = userRepository.existsByUsername("nonexistent.user");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    // AC4 Tests: Email-based queries

    @Test
    @DisplayName("should_findByEmail_when_emailProvided")
    void should_findByEmail_when_emailProvided() {
        // Given
        createAndSaveUser("test.user", "unique@example.com");

        // When
        Optional<User> found = userRepository.findByEmail("unique@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("unique@example.com");
    }

    @Test
    @DisplayName("should_checkExistence_when_existsByEmailCalled")
    void should_checkExistence_when_existsByEmailCalled() {
        // Given
        createAndSaveUser("test.user", "test@example.com");

        // When
        boolean exists = userRepository.existsByEmail("test@example.com");
        boolean notExists = userRepository.existsByEmail("other@example.com");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    // AC5 Tests: Cognito-based queries

    @Test
    @DisplayName("should_findByCognitoUserId_when_cognitoIdProvided")
    void should_findByCognitoUserId_when_cognitoIdProvided() {
        // Given
        User user = createAndSaveUser("test.user", "test@example.com");

        // When
        Optional<User> found = userRepository.findByCognitoUserId(user.getCognitoUserId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCognitoUserId()).isEqualTo(user.getCognitoUserId());
    }

    // AC6 Tests: Company-based queries (Story 1.16.2 - company name, not UUID)

    @Test
    @DisplayName("should_findByCompanyId_when_companyNameProvided")
    void should_findByCompanyId_when_companyNameProvided() {
        // Given - Story 1.16.2: companyId is company name (String)
        createAndSaveUser("user.one", "user1@example.com", "GoogleZH");
        createAndSaveUser("user.two", "user2@example.com", "GoogleZH");
        createAndSaveUser("user.three", "user3@example.com", "MicrosoftBE");

        // When
        List<User> googleUsers = userRepository.findByCompanyId("GoogleZH");

        // Then
        assertThat(googleUsers).hasSize(2);
        assertThat(googleUsers).allMatch(u -> u.getCompanyId().equals("GoogleZH"));
    }

    // AC7 Tests: Role-based queries

    @Test
    @DisplayName("should_findByRole_when_roleProvided")
    void should_findByRole_when_roleProvided() {
        // Given
        User speaker1 = createAndSaveUser("speaker.one", "speaker1@example.com");
        speaker1.addRole(Role.SPEAKER);
        userRepository.save(speaker1);

        User speaker2 = createAndSaveUser("speaker.two", "speaker2@example.com");
        speaker2.addRole(Role.SPEAKER);
        userRepository.save(speaker2);

        User attendee = createAndSaveUser("attendee.one", "attendee1@example.com");
        attendee.addRole(Role.ATTENDEE);
        userRepository.save(attendee);

        entityManager.flush();

        // When
        List<User> speakers = userRepository.findByRolesContaining(Role.SPEAKER);

        // Then
        assertThat(speakers).hasSize(2);
        assertThat(speakers).allMatch(u -> u.hasRole(Role.SPEAKER));
    }

    // AC8 Tests: Name-based search

    @Test
    @DisplayName("should_findByNameContaining_when_partialNameProvided")
    void should_findByNameContaining_when_partialNameProvided() {
        // Given
        createAndSaveUser("john.doe", "john@example.com", "John", "Doe");
        createAndSaveUser("jane.doe", "jane@example.com", "Jane", "Doe");
        createAndSaveUser("max.smith", "max@example.com", "Max", "Smith");

        // When
        List<User> doeUsers = userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("Doe", "Doe");

        // Then
        assertThat(doeUsers).hasSize(2);
    }

    // AC9 Tests: Active status queries

    @Test
    @DisplayName("should_findByIsActive_when_activeStatusProvided")
    void should_findByIsActive_when_activeStatusProvided() {
        // Given
        User activeUser = createAndSaveUser("active.user", "active@example.com");

        User inactiveUser = createAndSaveUser("inactive.user", "inactive@example.com");
        inactiveUser.setActive(false);
        userRepository.save(inactiveUser);

        entityManager.flush();

        // When
        List<User> activeUsers = userRepository.findByIsActive(true);

        // Then
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).isActive()).isTrue();
    }

    // AC10 Tests: Embedded objects persistence

    @Test
    @DisplayName("should_persistEmbeddedPreferences_when_userSaved")
    void should_persistEmbeddedPreferences_when_userSaved() {
        // Given
        UserPreferences prefs = UserPreferences.builder()
                .theme("dark")
                .language("en")
                .emailNotifications(false)
                .build();

        User user = createTestUser("pref.user", "pref@example.com", "Pref", "User");
        user.setPreferences(prefs);

        // When
        User saved = userRepository.saveAndFlush(user);

        // Then
        User retrieved = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getPreferences()).isNotNull();
        assertThat(retrieved.getPreferences().getTheme()).isEqualTo("dark");
        assertThat(retrieved.getPreferences().getLanguage()).isEqualTo("en");
        assertThat(retrieved.getPreferences().isEmailNotifications()).isFalse();
    }

    @Test
    @DisplayName("should_persistEmbeddedSettings_when_userSaved")
    void should_persistEmbeddedSettings_when_userSaved() {
        // Given
        UserSettings settings = UserSettings.builder()
                .profileVisibility("private")
                .timezone("UTC")
                .twoFactorEnabled(true)
                .build();

        User user = createTestUser("settings.user", "settings@example.com", "Settings", "User");
        user.setSettings(settings);

        // When
        User saved = userRepository.saveAndFlush(user);

        // Then
        User retrieved = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getSettings()).isNotNull();
        assertThat(retrieved.getSettings().getProfileVisibility()).isEqualTo("private");
        assertThat(retrieved.getSettings().getTimezone()).isEqualTo("UTC");
        assertThat(retrieved.getSettings().isTwoFactorEnabled()).isTrue();
    }

    // Helper methods

    private User createTestUser(String username, String email, String firstName, String lastName) {
        User user = User.builder()
                .username(username)
                .cognitoUserId("cognito-" + System.nanoTime())
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();
        user.onCreate();
        return user;
    }

    private User createAndSaveUser(String username, String email) {
        return createAndSaveUser(username, email, null);
    }

    private User createAndSaveUser(String username, String email, String companyId) {
        User user = User.builder()
                .username(username)
                .cognitoUserId("cognito-" + System.nanoTime())
                .email(email)
                .firstName("Test")
                .lastName("User")
                .companyId(companyId)
                .build();
        user.onCreate();
        return userRepository.saveAndFlush(user);
    }

    private User createAndSaveUser(String username, String email, String firstName, String lastName) {
        User user = createTestUser(username, email, firstName, lastName);
        return userRepository.saveAndFlush(user);
    }
}
