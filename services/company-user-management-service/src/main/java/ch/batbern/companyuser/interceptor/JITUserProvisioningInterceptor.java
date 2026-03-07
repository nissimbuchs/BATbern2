package ch.batbern.companyuser.interceptor;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.event.UserCreatedEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * JIT (Just-In-Time) User Provisioning Interceptor
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC2: JIT provisioning creates database user on first API request
 * <p>
 * Purpose:
 * - Check if authenticated Cognito user exists in database
 * - Create database user if missing (JIT provisioning)
 * - Extract roles from JWT custom claims
 * - Publish UserCreatedEvent for observability
 * - Allow request to continue (non-blocking even on errors)
 * <p>
 * Flow:
 * 1. Extract Cognito user ID from JWT
 * 2. Check if user exists in database
 * 3. If not, create user with roles from JWT
 * 4. Publish UserCreatedEvent
 * 5. Continue request (return true)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JITUserProvisioningInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Pre-handle method called before controller execution
     * <p>
     * Performs JIT provisioning for authenticated users not yet in database
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @param handler  Chosen handler to execute
     * @return true to continue request, false to stop
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // Get authentication from security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Skip if not authenticated or not JWT token
            if (authentication == null || !(authentication instanceof JwtAuthenticationToken)) {
                return true;
            }

            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();

            // Extract Cognito user ID (subject claim)
            String cognitoUserId = jwt.getSubject();
            if (cognitoUserId == null || cognitoUserId.isEmpty()) {
                log.warn("JWT token missing subject claim, skipping JIT provisioning");
                return true;
            }

            // Check if user already exists by Cognito ID
            if (userRepository.findByCognitoUserId(cognitoUserId).isPresent()) {
                return true;
            }

            // Extract user information from JWT
            String email = jwt.getClaimAsString("email");
            String firstName = jwt.getClaimAsString("given_name");
            String lastName = jwt.getClaimAsString("family_name");

            // Check if a pre-existing user record exists for this email (e.g. added by organizer
            // before the user self-registered in Cognito). If so, link the Cognito ID to that record
            // instead of creating a duplicate.
            if (email != null && !email.isEmpty()) {
                Optional<User> existingByEmail = userRepository.findByEmail(email);
                if (existingByEmail.isPresent()) {
                    User existing = existingByEmail.get();
                    existing.setCognitoUserId(cognitoUserId);
                    userRepository.save(existing);
                    log.info("Linked Cognito user to existing DB record via email",
                            mapOf("cognitoUserId", cognitoUserId, "username", existing.getUsername(), "email", email));
                    return true;
                }
            }

            // No record at all — perform JIT provisioning (create new user)
            log.info("User not found in database, performing JIT provisioning",
                    mapOf("cognitoUserId", cognitoUserId));

            // Generate username from first/last name or email (firstname.lastname format required)
            String username = generateUsername(firstName, lastName, email);

            // Extract roles from authorities
            Set<Role> roles = extractRolesFromAuthorities(authentication.getAuthorities());

            // Create new user
            User newUser = User.builder()
                    .cognitoUserId(cognitoUserId)
                    .username(username)
                    .email(email != null ? email : "")
                    .firstName(firstName != null ? firstName : "")
                    .lastName(lastName != null ? lastName : "")
                    .roles(roles)
                    .isActive(true)
                    .build();

            User savedUser = userRepository.save(newUser);

            log.info("JIT provisioning completed successfully",
                    mapOf(
                        "cognitoUserId", cognitoUserId,
                        "username", username,
                        "email", email,
                        "roles", roles
                    ));

            publishUserCreatedEvent(savedUser, "JIT_PROVISIONING");

        } catch (Exception e) {
            // Log error but DON'T block request (non-blocking requirement)
            log.error("JIT provisioning failed, allowing request to continue", e);
        }

        // Always return true to continue request
        return true;
    }

    /**
     * Generate username from first name, last name, or email
     * <p>
     * Format: firstname.lastname (lowercase, required by chk_username_format constraint)
     * Example: John Doe -> john.doe
     * Example: John Doe (duplicate) -> john.doe.2
     * Example: nissim@buchs.be (no names) -> user.nissim
     *
     * @param firstName User first name from JWT (given_name)
     * @param lastName  User last name from JWT (family_name)
     * @param email     User email (fallback if names not available)
     * @return Generated username matching pattern ^[a-z]+\.[a-z]+(\.[0-9]+)?$
     */
    private String generateUsername(String firstName, String lastName, String email) {
        String username;

        // Prefer first.last name if both available
        if (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty()) {
            username = firstName.toLowerCase().replaceAll("[^a-z]", "")
                       + "."
                       + lastName.toLowerCase().replaceAll("[^a-z]", "");
        } else if (email != null && !email.isEmpty()) {
            // Fall back to email local part if it contains a dot
            String emailLocal = email.split("@")[0].toLowerCase().replaceAll("[^a-z.]", "");
            if (emailLocal.contains(".")) {
                username = emailLocal;
            } else {
                // Email doesn't contain dot, prepend "user."
                username = "user." + emailLocal;
            }
        } else {
            // Last resort: no name or email available — use 'user.unknown' base; uniqueness enforced below
            log.warn("No first name, last name, or email available during JIT provisioning; using 'user.unknown' base");
            username = "user.unknown";
        }

        // Check if username exists, add numeric suffix if needed
        String finalUsername = username;
        int suffix = 2;
        while (userRepository.existsByUsername(finalUsername)) {
            finalUsername = username + "." + suffix;
            suffix++;
        }

        return finalUsername;
    }

    /**
     * Extract roles from Spring Security authorities
     * <p>
     * Authorities format: "ROLE_ATTENDEE", "ROLE_ORGANIZER", etc.
     * Extracts role name after "ROLE_" prefix
     *
     * @param authorities Spring Security authorities
     * @return Set of roles
     */
    private Set<Role> extractRolesFromAuthorities(java.util.Collection<? extends GrantedAuthority> authorities) {
        Set<Role> roles = new HashSet<>();

        for (GrantedAuthority authority : authorities) {
            String authorityName = authority.getAuthority();

            // Extract role after "ROLE_" prefix
            if (authorityName.startsWith("ROLE_")) {
                String roleName = authorityName.substring(5); // Remove "ROLE_" prefix
                try {
                    Role role = Role.valueOf(roleName);
                    roles.add(role);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown role in JWT: {}", roleName);
                }
            }
        }

        // Default to ATTENDEE if no roles found
        if (roles.isEmpty()) {
            roles.add(Role.ATTENDEE);
        }

        return roles;
    }

    /**
     * Publish UserCreatedEvent for observability
     *
     * @param user   Created user
     * @param source Event source (JIT_PROVISIONING)
     */
    private void publishUserCreatedEvent(User user, String source) {
        try {
            UserCreatedEvent event = new UserCreatedEvent(
                    this,
                    user.getId(),
                    user.getCognitoUserId(),
                    user.getEmail(),
                    user.getRoles().stream().map(Role::name).toList(),
                    source
            );
            eventPublisher.publishEvent(event);

            log.debug("Published UserCreatedEvent",
                    mapOf(
                        "userId", user.getId(),
                        "source", source
                    ));
        } catch (Exception e) {
            log.error("Failed to publish UserCreatedEvent", e);
        }
    }

    // Map.of helper (Java 11 compatibility)
    private <K, V> java.util.Map<K, V> mapOf(Object... entries) {
        java.util.Map<K, V> map = new java.util.HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }
}
