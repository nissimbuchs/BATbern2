package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ServiceExceptionTest {

    @Test
    void should_extendBATbernException_when_created() {
        // When
        ServiceException exception = new ServiceException("Service error");

        // Then
        assertThat(exception).isInstanceOf(BATbernException.class);
    }

    @Test
    void should_haveServiceException_when_internalErrorOccurs() {
        // Given
        String message = "Database connection failed";

        // When
        ServiceException exception = new ServiceException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_SERVICE);
        assertThat(exception.getSeverity()).isEqualTo(BATbernException.Severity.CRITICAL);
    }

    @Test
    void should_includeCause_when_providedInConstructor() {
        // Given
        Throwable cause = new RuntimeException("Root cause");

        // When
        ServiceException exception = new ServiceException("Service unavailable", cause);

        // Then
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getMessage()).isEqualTo("Service unavailable");
    }

    @Test
    void should_includeDetails_when_providedInConstructor() {
        // Given
        Map<String, Object> details = Map.of(
            "service", "EventManagement",
            "operation", "createEvent"
        );

        // When
        ServiceException exception = new ServiceException("Operation failed", details);

        // Then
        assertThat(exception.getDetails())
            .containsEntry("service", "EventManagement")
            .containsEntry("operation", "createEvent");
    }
}
