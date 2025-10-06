package ch.batbern.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Instant timestamp;
    private String path;
    private Integer status;
    private String error;
    private String message;
    private String correlationId;
    private String severity;
    private Map<String, Object> details;
    private String stackTrace; // Only in dev/staging
}
