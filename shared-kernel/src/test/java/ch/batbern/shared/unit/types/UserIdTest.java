package ch.batbern.shared.unit.types;

import ch.batbern.shared.exceptions.ValidationException;
import ch.batbern.shared.types.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

class UserIdTest {

    @Test
    @DisplayName("should_enforceUserIdValidation_when_created")
    void should_enforceUserIdValidation_when_created() {
        String cognitoUserId = "eu-central-1:12345678-1234-1234-1234-123456789012";

        UserId userId = UserId.from(cognitoUserId);

        assertThat(userId).isNotNull();
        assertThat(userId.getValue()).isEqualTo(cognitoUserId);
    }

    @Test
    @DisplayName("should_acceptValidCognitoFormats_when_created")
    void should_acceptValidCognitoFormats_when_created() {
        String[] validFormats = {
            "eu-central-1:12345678-1234-1234-1234-123456789012",
            "us-east-1:abcdef12-3456-7890-abcd-ef1234567890",
            "username_pool_user",
            "google_112233445566778899000",
            "facebook_1234567890123456"
        };

        for (String id : validFormats) {
            UserId userId = UserId.from(id);
            assertThat(userId).isNotNull();
            assertThat(userId.getValue()).isEqualTo(id);
        }
    }

    @Test
    @DisplayName("should_throwValidationException_when_nullProvided")
    void should_throwValidationException_when_nullProvided() {
        assertThatThrownBy(() -> UserId.from(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("UserId cannot be null");
    }

    @Test
    @DisplayName("should_throwValidationException_when_emptyStringProvided")
    void should_throwValidationException_when_emptyStringProvided() {
        assertThatThrownBy(() -> UserId.from(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("UserId cannot be empty");
    }

    @Test
    @DisplayName("should_throwValidationException_when_blankStringProvided")
    void should_throwValidationException_when_blankStringProvided() {
        assertThatThrownBy(() -> UserId.from("   "))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("UserId cannot be blank");
    }

    @Test
    @DisplayName("should_extractProvider_when_federatedUserIdProvided")
    void should_extractProvider_when_federatedUserIdProvided() {
        UserId googleUser = UserId.from("google_112233445566778899000");
        UserId facebookUser = UserId.from("facebook_1234567890123456");
        UserId cognitoUser = UserId.from("eu-central-1:12345678-1234-1234-1234-123456789012");

        assertThat(googleUser.getProvider()).isEqualTo("google");
        assertThat(facebookUser.getProvider()).isEqualTo("facebook");
        assertThat(cognitoUser.getProvider()).isEqualTo("cognito");
    }

    @Test
    @DisplayName("should_implementEqualsCorrectly_when_comparingUserIds")
    void should_implementEqualsCorrectly_when_comparingUserIds() {
        String id = "eu-central-1:12345678-1234-1234-1234-123456789012";
        UserId userId1 = UserId.from(id);
        UserId userId2 = UserId.from(id);
        UserId userId3 = UserId.from("us-west-2:87654321-4321-4321-4321-210987654321");

        assertThat(userId1).isEqualTo(userId2);
        assertThat(userId1).isNotEqualTo(userId3);
        assertThat(userId1.hashCode()).isEqualTo(userId2.hashCode());
        assertThat(userId1.hashCode()).isNotEqualTo(userId3.hashCode());
    }

    @Test
    @DisplayName("should_beImmutable_when_created")
    void should_beImmutable_when_created() {
        String id = "eu-central-1:12345678-1234-1234-1234-123456789012";
        UserId userId = UserId.from(id);
        String originalValue = userId.getValue();

        assertThat(userId.getValue()).isEqualTo(originalValue);
        assertThat(userId.getValue()).isSameAs(originalValue);
    }

    @Test
    @DisplayName("should_provideStringRepresentation_when_toStringCalled")
    void should_provideStringRepresentation_when_toStringCalled() {
        String id = "eu-central-1:12345678-1234-1234-1234-123456789012";
        UserId userId = UserId.from(id);

        assertThat(userId.toString()).isEqualTo(id);
    }

    @Test
    @DisplayName("should_supportAnonymousUser_when_anonymousMethodCalled")
    void should_supportAnonymousUser_when_anonymousMethodCalled() {
        UserId anonymous = UserId.anonymous();

        assertThat(anonymous).isNotNull();
        assertThat(anonymous.getValue()).isEqualTo("anonymous");
        assertThat(anonymous.isAnonymous()).isTrue();
    }

    @Test
    @DisplayName("should_identifyNonAnonymousUsers_when_regularUserProvided")
    void should_identifyNonAnonymousUsers_when_regularUserProvided() {
        UserId regularUser = UserId.from("eu-central-1:12345678-1234-1234-1234-123456789012");

        assertThat(regularUser.isAnonymous()).isFalse();
    }
}