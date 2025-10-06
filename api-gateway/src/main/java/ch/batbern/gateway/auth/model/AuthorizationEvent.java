package ch.batbern.gateway.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationEvent {

    private String userId;
    private String resource;
    private String action;
    private boolean granted;
    private LocalDateTime timestamp;
    private String requestId;
    private String sessionId;
    private String clientIp;

    public static AuthorizationEvent of(String userId, String resource, boolean granted) {
        return AuthorizationEvent.builder()
            .userId(userId)
            .resource(resource)
            .granted(granted)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static AuthorizationEvent withMetadata(String userId, String resource, boolean granted,
                                                  String requestId, String sessionId) {
        return AuthorizationEvent.builder()
            .userId(userId)
            .resource(resource)
            .granted(granted)
            .requestId(requestId)
            .sessionId(sessionId)
            .timestamp(LocalDateTime.now())
            .build();
    }
}