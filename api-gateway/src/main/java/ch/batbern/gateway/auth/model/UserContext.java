package ch.batbern.gateway.auth.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserContext {
    private String userId;
    private String email;
    private boolean emailVerified;
    private String role;
    private String companyId;
    private Map<String, Object> preferences;
    private List<String> additionalRoles;
    private Instant issuedAt;
    private Instant expiresAt;
    private String sessionId;
    private Map<String, Object> customClaims;
}