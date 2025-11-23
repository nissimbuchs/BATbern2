package ch.batbern.companyuser.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserUpdatedEvent
 * Story 1.14-2, AC: 2, 11 (Event Publishing)
 */
@DisplayName("UserUpdatedEvent Tests")
class UserUpdatedEventTest {

    @Test
    @DisplayName("should_createEvent_when_builderUsed")
    void should_createEvent_when_builderUsed() {
        // Given
        Map<String, Object> updatedFields = Map.of(
                "firstName", "Jane",
                "bio", "Updated bio"
        );
        Map<String, Object> previousValues = Map.of(
                "firstName", "John",
                "bio", "Old bio"
        );

        // When
        UserUpdatedEvent event = UserUpdatedEvent.builder()
                .username("john.doe")
                .updatedFields(updatedFields)
                .previousValues(previousValues)
                .updatedBy("admin.user")
                .build();

        // Then
        assertThat(event.getUsername()).isEqualTo("john.doe");
        assertThat(event.getUpdatedFields()).isEqualTo(updatedFields);
        assertThat(event.getPreviousValues()).isEqualTo(previousValues);
        assertThat(event.getUserId()).isEqualTo("admin.user");
    }

    @Test
    @DisplayName("should_useUsernameAsAggregateId_when_eventCreated")
    void should_useUsernameAsAggregateId_when_eventCreated() {
        // Given
        String username = "jane.smith";

        // When
        UserUpdatedEvent event = UserUpdatedEvent.builder()
                .username(username)
                .updatedFields(Map.of("email", "new.email@example.com"))
                .updatedBy("system")
                .build();

        // Then - Story 1.16.2: aggregateId should be username (String)
        assertThat(event.getAggregateId()).isEqualTo(username);
    }

    @Test
    @DisplayName("should_haveEventMetadata_when_created")
    void should_haveEventMetadata_when_created() {
        // When
        UserUpdatedEvent event = UserUpdatedEvent.builder()
                .username("test.user")
                .updatedFields(Map.of("lastName", "NewName"))
                .updatedBy("admin")
                .build();

        // Then
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("UserUpdatedEvent");
        assertThat(event.getEventName()).isEqualTo("UserUpdatedEvent");
        assertThat(event.getAggregateType()).isEqualTo("User");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("should_allowNullPreviousValues_when_notProvided")
    void should_allowNullPreviousValues_when_notProvided() {
        // When
        UserUpdatedEvent event = UserUpdatedEvent.builder()
                .username("test.user")
                .updatedFields(Map.of("bio", "New bio"))
                .previousValues(null)
                .updatedBy("system")
                .build();

        // Then
        assertThat(event.getPreviousValues()).isNull();
    }

    @Test
    @DisplayName("should_throwException_when_usernameIsNull")
    void should_throwException_when_usernameIsNull() {
        // When/Then
        assertThatThrownBy(() ->
                UserUpdatedEvent.builder()
                        .username(null)
                        .updatedFields(Map.of("email", "test@example.com"))
                        .updatedBy("admin")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("username");
    }

    @Test
    @DisplayName("should_throwException_when_updatedFieldsIsNull")
    void should_throwException_when_updatedFieldsIsNull() {
        // When/Then
        assertThatThrownBy(() ->
                UserUpdatedEvent.builder()
                        .username("test.user")
                        .updatedFields(null)
                        .updatedBy("admin")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedFields");
    }
}
