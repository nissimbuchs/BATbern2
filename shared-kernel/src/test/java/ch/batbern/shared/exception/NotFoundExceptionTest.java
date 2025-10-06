package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class NotFoundExceptionTest {

    @Test
    void should_extendBATbernException_when_created() {
        // When
        NotFoundException exception = new NotFoundException("Resource not found");

        // Then
        assertThat(exception).isInstanceOf(BATbernException.class);
    }

    @Test
    void should_haveNotFoundException_when_resourceMissing() {
        // Given
        String resourceType = "Event";
        String resourceId = "123";

        // When
        NotFoundException exception = new NotFoundException(resourceType, resourceId);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Event with ID '123' not found");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_NOT_FOUND);
        assertThat(exception.getSeverity()).isEqualTo(BATbernException.Severity.LOW);
        assertThat(exception.getDetails())
            .containsEntry("resourceType", "Event")
            .containsEntry("resourceId", "123");
    }

    @Test
    void should_createSimpleNotFoundException_when_onlyMessageProvided() {
        // When
        NotFoundException exception = new NotFoundException("Speaker not found");

        // Then
        assertThat(exception.getMessage()).isEqualTo("Speaker not found");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_NOT_FOUND);
        assertThat(exception.getSeverity()).isEqualTo(BATbernException.Severity.LOW);
    }
}
