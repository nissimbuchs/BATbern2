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
public class AuthenticationEvent {

    private String userId;
    private String email;
    private String attemptType;
    private boolean success;
    private String errorMessage;
    private String clientIp;
    private String userAgent;
    private LocalDateTime timestamp;
    private String sessionId;

    public static AuthenticationEvent success(String userId, String email, String attemptType,
                                              String clientIp, String userAgent) {
        return AuthenticationEvent.builder()
            .userId(userId)
            .email(email)
            .attemptType(attemptType)
            .success(true)
            .clientIp(clientIp)
            .userAgent(userAgent)
            .timestamp(LocalDateTime.now())
            .build();
    }

    public static AuthenticationEvent failure(String userId, String email, String attemptType,
                                              String errorMessage, String clientIp, String userAgent) {
        return AuthenticationEvent.builder()
            .userId(userId)
            .email(email)
            .attemptType(attemptType)
            .success(false)
            .errorMessage(errorMessage)
            .clientIp(clientIp)
            .userAgent(userAgent)
            .timestamp(LocalDateTime.now())
            .build();
    }
}