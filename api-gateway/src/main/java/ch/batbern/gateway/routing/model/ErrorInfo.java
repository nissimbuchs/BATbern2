package ch.batbern.gateway.routing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorInfo {

    private String code;
    private String message;
    private String field;
    private Map<String, Object> details;

    public static ErrorInfo of(String code, String message) {
        return ErrorInfo.builder()
            .code(code)
            .message(message)
            .build();
    }

    public static ErrorInfo withField(String code, String message, String field) {
        return ErrorInfo.builder()
            .code(code)
            .message(message)
            .field(field)
            .build();
    }

    public static ErrorInfo withDetails(String code, String message, Map<String, Object> details) {
        return ErrorInfo.builder()
            .code(code)
            .message(message)
            .details(details)
            .build();
    }
}