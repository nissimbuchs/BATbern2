package ch.batbern.shared.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

/**
 * User Created Event
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * Published when a user is created in the database (PostConfirmation or JIT provisioning)
 * <p>
 * Can be consumed by:
 * - Cache invalidation services
 * - Analytics services
 * - Audit logging services
 * - Email notification services
 */
@Getter
public class UserCreatedEvent extends ApplicationEvent {

    private final UUID userId;
    private final String cognitoUserId;
    private final String email;
    private final List<String> roles;
    private final String source; // POST_CONFIRMATION or JIT_PROVISIONING

    public UserCreatedEvent(Object source, UUID userId, String cognitoUserId, String email,
                             List<String> roles, String eventSource) {
        super(source);
        this.userId = userId;
        this.cognitoUserId = cognitoUserId;
        this.email = email;
        this.roles = roles;
        this.source = eventSource;
    }
}
