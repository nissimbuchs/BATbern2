package ch.batbern.companyuser.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserDeletedEvent
 * Story 1.14-2, AC: 2, 11 (Event Publishing)
 */
@DisplayName("UserDeletedEvent Tests")
class UserDeletedEventTest {

    @Test
    @DisplayName("should_createEvent_when_builderUsed")
    void should_createEvent_when_builderUsed() {
        // When
        UserDeletedEvent event = UserDeletedEvent.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .reason("Account deactivation requested")
                .deletedBy("admin.user")
                .build();

        // Then
        assertThat(event.getUsername()).isEqualTo("john.doe");
        assertThat(event.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(event.getReason()).isEqualTo("Account deactivation requested");
        assertThat(event.getUserId()).isEqualTo("admin.user");
    }

    @Test
    @DisplayName("should_useUsernameAsAggregateId_when_eventCreated")
    void should_useUsernameAsAggregateId_when_eventCreated() {
        // Given
        String username = "jane.smith";

        // When
        UserDeletedEvent event = UserDeletedEvent.builder()
                .username(username)
                .email("jane@example.com")
                .deletedBy("system")
                .build();

        // Then - Story 1.16.2: aggregateId should be username (String)
        assertThat(event.getAggregateId()).isEqualTo(username);
    }

    @Test
    @DisplayName("should_haveEventMetadata_when_created")
    void should_haveEventMetadata_when_created() {
        // When
        UserDeletedEvent event = UserDeletedEvent.builder()
                .username("test.user")
                .email("test@example.com")
                .deletedBy("admin")
                .build();

        // Then
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("UserDeletedEvent");
        assertThat(event.getEventName()).isEqualTo("UserDeletedEvent");
        assertThat(event.getAggregateType()).isEqualTo("User");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("should_allowNullReason_when_notProvided")
    void should_allowNullReason_when_notProvided() {
        // When
        UserDeletedEvent event = UserDeletedEvent.builder()
                .username("test.user")
                .email("test@example.com")
                .reason(null)
                .deletedBy("system")
                .build();

        // Then
        assertThat(event.getReason()).isNull();
    }

    @Test
    @DisplayName("should_throwException_when_usernameIsNull")
    void should_throwException_when_usernameIsNull() {
        // When/Then
        assertThatThrownBy(() ->
                UserDeletedEvent.builder()
                        .username(null)
                        .email("test@example.com")
                        .deletedBy("admin")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("username");
    }

    @Test
    @DisplayName("should_throwException_when_emailIsNull")
    void should_throwException_when_emailIsNull() {
        // When/Then
        assertThatThrownBy(() ->
                UserDeletedEvent.builder()
                        .username("test.user")
                        .email(null)
                        .deletedBy("admin")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("email");
    }
}
