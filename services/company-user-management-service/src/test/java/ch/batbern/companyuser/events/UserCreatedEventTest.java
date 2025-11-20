package ch.batbern.companyuser.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserCreatedEvent
 * Story 1.14-2, AC: 2, 11 (Event Publishing)
 * Story 1.16.2: Verify username is used as aggregateId
 */
@DisplayName("UserCreatedEvent Tests")
class UserCreatedEventTest {

    @Test
    @DisplayName("should_createEvent_when_builderUsed")
    void should_createEvent_when_builderUsed() {
        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .companyId("GoogleZH")
                .cognitoUserId("cognito-123")
                .createdBy("admin.user")
                .build();

        // Then
        assertThat(event.getUsername()).isEqualTo("john.doe");
        assertThat(event.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(event.getFirstName()).isEqualTo("John");
        assertThat(event.getLastName()).isEqualTo("Doe");
        assertThat(event.getCompanyId()).isEqualTo("GoogleZH");
        assertThat(event.getCognitoUserId()).isEqualTo("cognito-123");
        assertThat(event.getUserId()).isEqualTo("admin.user");
    }

    @Test
    @DisplayName("should_useUsernameAsAggregateId_when_eventCreated")
    void should_useUsernameAsAggregateId_when_eventCreated() {
        // Given
        String username = "jane.smith";

        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .username(username)
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .cognitoUserId("cognito-456")
                .createdBy("system")
                .build();

        // Then - Story 1.16.2: aggregateId should be username (String)
        assertThat(event.getAggregateId()).isEqualTo(username);
    }

    @Test
    @DisplayName("should_haveEventMetadata_when_created")
    void should_haveEventMetadata_when_created() {
        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .username("test.user")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .cognitoUserId("cognito-789")
                .createdBy("admin")
                .build();

        // Then
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("UserCreatedEvent");
        assertThat(event.getEventName()).isEqualTo("UserCreatedEvent");
        assertThat(event.getAggregateType()).isEqualTo("User");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("should_allowNullCompanyId_when_userHasNoCompany")
    void should_allowNullCompanyId_when_userHasNoCompany() {
        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .username("independent.user")
                .email("independent@example.com")
                .firstName("Independent")
                .lastName("User")
                .companyId(null)
                .cognitoUserId("cognito-999")
                .createdBy("system")
                .build();

        // Then
        assertThat(event.getCompanyId()).isNull();
    }

    @Test
    @DisplayName("should_throwException_when_usernameIsNull")
    void should_throwException_when_usernameIsNull() {
        // When/Then
        assertThatThrownBy(() ->
                UserCreatedEvent.builder()
                        .username(null)
                        .email("test@example.com")
                        .firstName("Test")
                        .lastName("User")
                        .cognitoUserId("cognito-123")
                        .createdBy("admin")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("username");
    }

    @Test
    @DisplayName("should_throwException_when_emailIsNull")
    void should_throwException_when_emailIsNull() {
        // When/Then
        assertThatThrownBy(() ->
                UserCreatedEvent.builder()
                        .username("test.user")
                        .email(null)
                        .firstName("Test")
                        .lastName("User")
                        .cognitoUserId("cognito-123")
                        .createdBy("admin")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email");
    }
}
