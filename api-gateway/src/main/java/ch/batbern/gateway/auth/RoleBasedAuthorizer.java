package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.exception.AuthorizationException;
import ch.batbern.gateway.auth.model.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBasedAuthorizer {

    private final AuditLogger auditLogger;

    // Role hierarchy: organizer > partner/speaker > attendee
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
        "organizer", Set.of("events:*", "speakers:*", "partners:*", "content:*"),
        "speaker", Set.of("speakers:read", "speakers:update", "content:read"),
        "partner", Set.of("partners:*", "content:read"),
        "attendee", Set.of("content:read")
    );

    private static final Map<String, String> RESOURCE_PREFIXES = Map.of(
        "/api/events", "events",
        "/api/speakers", "speakers",
        "/api/partners", "partners",
        "/api/content", "content"
    );

    public boolean hasPermission(UserContext userContext, String resource, String action) {
        if (userContext == null) {
            throw new AuthorizationException("User context is required");
        }

        log.debug("Checking permission for user: {} on resource: {} action: {}",
            userContext.getUserId(), resource, action);

        boolean granted = checkPermission(userContext.getRole(), resource, action);

        auditLogger.logAuthorizationDecision(userContext.getUserId(), resource, granted);

        return granted;
    }

    public void enforcePermission(UserContext userContext, String resource, String action) {
        if (!hasPermission(userContext, resource, action)) {
            throw new AuthorizationException("Access denied to resource: " + resource);
        }
    }

    private boolean checkPermission(String role, String resource, String action) {
        if (role == null) {
            return false;
        }

        Set<String> rolePermissions = ROLE_PERMISSIONS.get(role.toLowerCase());
        if (rolePermissions == null) {
            log.warn("Unknown role: {}", role);
            return false;
        }

        String resourceType = extractResourceType(resource);
        if (resourceType == null) {
            log.warn("Could not determine resource type for: {}", resource);
            return false;
        }

        // Check for wildcard permission (e.g., "events:*")
        if (rolePermissions.contains(resourceType + ":*")) {
            return true;
        }

        // Check for specific action permission (e.g., "speakers:read")
        String specificPermission = resourceType + ":" + action.toLowerCase();
        return rolePermissions.contains(specificPermission);
    }

    private String extractResourceType(String resource) {
        for (Map.Entry<String, String> entry : RESOURCE_PREFIXES.entrySet()) {
            if (resource.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}