package ch.batbern.companyuser.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Validation error response with field-level errors
 * AC4: Detailed validation error messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private Map<String, String> validationErrors;
}
