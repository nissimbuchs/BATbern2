package ch.batbern.gateway.routing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardResponse<T> {

    private boolean success;
    private T data;
    private ErrorInfo error;
    private Map<String, Object> metadata;
    private String requestId;
    private LocalDateTime timestamp;

    public static <T> StandardResponse<T> success(T data, String requestId) {
        return StandardResponse.<T>builder()
            .success(true)
            .data(data)
            .requestId(requestId)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> StandardResponse<T> error(ErrorInfo error, String requestId) {
        return StandardResponse.<T>builder()
            .success(false)
            .error(error)
            .requestId(requestId)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static <T> StandardResponse<T> successWithMetadata(T data, String requestId, Map<String, Object> metadata) {
        return StandardResponse.<T>builder()
            .success(true)
            .data(data)
            .metadata(metadata)
            .requestId(requestId)
            .timestamp(LocalDateTime.now())
            .build();
    }
}