package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UnauthorizedExceptionTest {

    @Test
    void should_extendBATbernException_when_created() {
        // When
        UnauthorizedException exception = new UnauthorizedException("Unauthorized");

        // Then
        assertThat(exception).isInstanceOf(BATbernException.class);
    }

    @Test
    void should_haveUnauthorizedException_when_accessDenied() {
        // Given
        String message = "Invalid authentication token";

        // When
        UnauthorizedException exception = new UnauthorizedException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_UNAUTHORIZED);
        assertThat(exception.getSeverity()).isEqualTo(BATbernException.Severity.HIGH);
    }

    @Test
    void should_useDefaultMessage_when_noMessageProvided() {
        // When
        UnauthorizedException exception = new UnauthorizedException();

        // Then
        assertThat(exception.getMessage()).isEqualTo("Authentication required to access this resource");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_UNAUTHORIZED);
        assertThat(exception.getSeverity()).isEqualTo(BATbernException.Severity.HIGH);
    }
}
