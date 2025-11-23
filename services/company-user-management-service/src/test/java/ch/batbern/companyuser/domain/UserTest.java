package ch.batbern.companyuser.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for User entity (aggregate root)
 * Tests cover entity creation, business methods, and validation
 * Story 1.14-2, AC: 1, 2, 5 (User Profile Management)
 * Story 1.16.2: Username field (meaningful public ID)
 */
@DisplayName("User Entity Tests")
class UserTest {

    @Test
    @DisplayName("should_createUser_when_builderUsed")
    void should_createUser_when_builderUsed() {
        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("john.doe@example.com")
                .username("john.doe")
                .firstName("John")
                .lastName("Doe")
                .companyId("GoogleZH")
                .build();

        // Then
        assertThat(user.getCognitoUserId()).isEqualTo("cognito-123");
        assertThat(user.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(user.getUsername()).isEqualTo("john.doe");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getCompanyId()).isEqualTo("GoogleZH");
        assertThat(user.isActive()).isTrue(); // Default
    }

    @Test
    @DisplayName("should_haveDefaultActiveStatus_when_created")
    void should_haveDefaultActiveStatus_when_created() {
        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();

        // Then
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("should_addRole_when_roleProvided")
    void should_addRole_when_roleProvided() {
        // Given
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();

        // When
        user.addRole(Role.SPEAKER);
        user.addRole(Role.ATTENDEE);

        // Then
        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.SPEAKER, Role.ATTENDEE);
    }

    @Test
    @DisplayName("should_removeRole_when_roleExists")
    void should_removeRole_when_roleExists() {
        // Given
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();
        user.addRole(Role.ORGANIZER);
        user.addRole(Role.SPEAKER);

        // When
        user.removeRole(Role.ORGANIZER);

        // Then
        assertThat(user.getRoles()).containsExactly(Role.SPEAKER);
    }

    @Test
    @DisplayName("should_returnTrue_when_userHasRole")
    void should_returnTrue_when_userHasRole() {
        // Given
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();
        user.addRole(Role.PARTNER);

        // When/Then
        assertThat(user.hasRole(Role.PARTNER)).isTrue();
        assertThat(user.hasRole(Role.ORGANIZER)).isFalse();
    }

    @Test
    @DisplayName("should_updateLastLoginAt_when_recordLoginCalled")
    void should_updateLastLoginAt_when_recordLoginCalled() {
        // Given
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();
        Instant before = Instant.now().minusSeconds(1);

        // When
        user.recordLogin();

        // Then
        Instant after = Instant.now().plusSeconds(1);
        assertThat(user.getLastLoginAt())
                .isNotNull()
                .isAfter(before)
                .isBefore(after);
    }

    @Test
    @DisplayName("should_setTimestamps_when_onCreateCalled")
    void should_setTimestamps_when_onCreateCalled() {
        // Given
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();
        Instant before = Instant.now().minusSeconds(1);

        // When
        user.onCreate();

        // Then
        Instant after = Instant.now().plusSeconds(1);
        assertThat(user.getCreatedAt())
                .isNotNull()
                .isAfter(before)
                .isBefore(after);
        assertThat(user.getUpdatedAt())
                .isNotNull()
                .isAfter(before)
                .isBefore(after);
    }

    @Test
    @DisplayName("should_updateTimestamp_when_onUpdateCalled")
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        // Given
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .build();
        user.onCreate();
        Instant createdAt = user.getCreatedAt();

        Thread.sleep(10); // Ensure time difference

        // When
        user.onUpdate();

        // Then
        assertThat(user.getUpdatedAt()).isAfter(createdAt);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt); // Should not change
    }

    @Test
    @DisplayName("should_embedPreferences_when_set")
    void should_embedPreferences_when_set() {
        // Given
        UserPreferences prefs = UserPreferences.builder()
                .theme("dark")
                .language("en")
                .build();

        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .preferences(prefs)
                .build();

        // Then
        assertThat(user.getPreferences()).isNotNull();
        assertThat(user.getPreferences().getTheme()).isEqualTo("dark");
        assertThat(user.getPreferences().getLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("should_embedSettings_when_set")
    void should_embedSettings_when_set() {
        // Given
        UserSettings settings = UserSettings.builder()
                .profileVisibility("private")
                .timezone("UTC")
                .build();

        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .settings(settings)
                .build();

        // Then
        assertThat(user.getSettings()).isNotNull();
        assertThat(user.getSettings().getProfileVisibility()).isEqualTo("private");
        assertThat(user.getSettings().getTimezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("should_storeProfilePicture_when_set")
    void should_storeProfilePicture_when_set() {
        // Given
        String s3Key = "profile-pictures/john-doe-123.jpg";
        String url = "https://cdn.batbern.ch/profile-pictures/john-doe-123.jpg";

        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .profilePictureS3Key(s3Key)
                .profilePictureUrl(url)
                .build();

        // Then
        assertThat(user.getProfilePictureS3Key()).isEqualTo(s3Key);
        assertThat(user.getProfilePictureUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("should_storeBio_when_provided")
    void should_storeBio_when_provided() {
        // Given
        String bio = "Experienced software engineer passionate about cloud architecture.";

        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .bio(bio)
                .build();

        // Then
        assertThat(user.getBio()).isEqualTo(bio);
    }

    @Test
    @DisplayName("should_allowNullCompanyId_when_userHasNoCompany")
    void should_allowNullCompanyId_when_userHasNoCompany() {
        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .companyId(null)
                .build();

        // Then
        assertThat(user.getCompanyId()).isNull();
    }

    @Test
    @DisplayName("should_storeCompanyName_when_provided")
    void should_storeCompanyName_when_provided() {
        // Given - Story 1.16.2: company_id is company name (not UUID)
        String companyId = "MicrosoftBE";

        // When
        User user = User.builder()
                .cognitoUserId("cognito-123")
                .email("test@example.com")
                .username("test.user")
                .firstName("Test")
                .lastName("User")
                .companyId(companyId)
                .build();

        // Then
        assertThat(user.getCompanyId()).isEqualTo(companyId);
    }
}
