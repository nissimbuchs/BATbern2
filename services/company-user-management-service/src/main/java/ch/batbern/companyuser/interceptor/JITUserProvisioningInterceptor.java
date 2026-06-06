package ch.batbern.companyuser.interceptor;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserPreferences;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.event.UserCreatedEvent;
import ch.batbern.shared.utils.LoggingUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Locale;
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

    /**
     * Request attribute carrying the {@link User} this interceptor resolved (found,
     * linked, or JIT-created) for the authenticated principal. Story 12.12 review
     * (finding #6): {@code FederatedAvatarImportInterceptor} runs immediately after this
     * one on the same paths and previously re-ran the identical
     * {@code findByCognitoUserId} SELECT on every request — sharing the resolved user
     * here removes that permanent duplicate query.
     */
    public static final String RESOLVED_USER_ATTRIBUTE =
            JITUserProvisioningInterceptor.class.getName() + ".resolvedUser";

    private final UserRepository userRepository;
    private final UserAdditionalEmailRepository userAdditionalEmailRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** For parsing the `custom:preferences` JSON blob set by the signup form. */
    private static final ObjectMapper PREFERENCES_MAPPER = new ObjectMapper();

    /**
     * Supported UI language codes that fit the {@code pref_language VARCHAR(2)} column
     * (UserPreferences.java:30). The 10th supported locale, {@code gsw-BE}, is intentionally
     * absent: its 2-char primary subtag {@code gsw} does not exist, so Swiss-German falls
     * back to the {@code @PrePersist} default {@code "de"}. Keep in sync with
     * V15__remove_language_check_constraint.sql and web-frontend i18n config.
     */
    private static final Set<String> SUPPORTED_LANGUAGE_CODES =
            Set.of("de", "en", "fr", "it", "rm", "es", "fi", "nl", "ja");

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
            Optional<User> existingByCognitoId = userRepository.findByCognitoUserId(cognitoUserId);
            if (existingByCognitoId.isPresent()) {
                request.setAttribute(RESOLVED_USER_ATTRIBUTE, existingByCognitoId.get());
                return true;
            }

            // Extract user information from JWT.
            //
            // The signup form (authService.ts) packs first/last name into the
            // `custom:preferences` JSON attribute, NOT the standard given_name /
            // family_name. PostConfirmation Lambda reads from `custom:preferences`.
            // When PostConfirmation fails (e.g. username collision blocks INSERT)
            // and JIT runs as fallback, we'd otherwise create accounts with empty
            // first/last names — 2026-05-18 incident with nikolay.borissov.2 /
            // elmar.boschung.2. Read the same source as PostConfirmation.
            String email = jwt.getClaimAsString("email");
            String firstName = jwt.getClaimAsString("given_name");
            String lastName = jwt.getClaimAsString("family_name");
            if (firstName == null || firstName.isEmpty() || lastName == null || lastName.isEmpty()) {
                String[] fromPrefs = extractNamesFromPreferences(jwt);
                if ((firstName == null || firstName.isEmpty()) && fromPrefs[0] != null) {
                    firstName = fromPrefs[0];
                }
                if ((lastName == null || lastName.isEmpty()) && fromPrefs[1] != null) {
                    lastName = fromPrefs[1];
                }
            }

            // Check if a pre-existing user record exists for this email (e.g. added by organizer
            // before the user self-registered in Cognito). If so, link the Cognito ID to that record
            // instead of creating a duplicate.
            if (email != null && !email.isEmpty()) {
                Optional<User> existingByEmail = userRepository.findByEmailIgnoreCase(email);
                if (existingByEmail.isPresent()) {
                    User existing = existingByEmail.get();
                    existing.setCognitoUserId(cognitoUserId);
                    userRepository.save(existing);
                    request.setAttribute(RESOLVED_USER_ATTRIBUTE, existing);
                    log.info("Linked Cognito user to existing DB record via email",
                            mapOf("cognitoUserId", cognitoUserId, "username", existing.getUsername(), "email", email));
                    return true;
                }
            }

            // Epic 12 follow-up — duplicate guard. Before JIT-creating a brand-new user, check
            // whether this email is a VERIFIED additional email (Story A {@code verified_at}) of
            // an existing user. If so, the PreSignUp Lambda should have linked this federated
            // identity into that owner; reaching here means linking did not happen. Creating a
            // user now would produce a duplicate ATTENDEE for an email that already belongs to a
            // real account, so we skip creation entirely. We deliberately do NOT resolve the
            // request to the owner (that would grant the unlinked session the owner's identity at
            // service level only — Cognito still split — a half-linked state worse than a
            // degraded session; the real link belongs to PreSignUp) and we NEVER touch the
            // owner's cognito_user_id.
            if (email != null && !email.isEmpty()
                    && userAdditionalEmailRepository.findVerifiedByEmailIgnoreCase(email).isPresent()) {
                log.warn("Skipping JIT creation: email is a verified additional email of an existing "
                                + "user; PreSignUp account-linking should have linked this identity",
                        mapOf("cognitoUserId", cognitoUserId, "email", LoggingUtils.maskEmail(email)));
                return true;
            }

            // No record at all — perform JIT provisioning (create new user)
            log.info("User not found in database, performing JIT provisioning",
                    mapOf("cognitoUserId", cognitoUserId));

            // Generate username from first/last name or email (firstname.lastname format required)
            String username = generateUsername(firstName, lastName, email);

            // Extract roles from authorities
            Set<Role> roles = extractRolesFromAuthorities(authentication.getAuthorities());

            // Story 12.3: capture the chosen UI language from `custom:preferences`, mirroring
            // post-confirmation.ts (`const language = preferences.language || 'de'`). Federated
            // users never hit PostConfirmation, so JIT is the only place their language is
            // captured. When absent/malformed, leave preferences unset so the @PrePersist
            // default ("de", UserPreferences.java:32) applies — no behaviour change for the
            // name-only signups that reach JIT today.
            String language = extractLanguageFromPreferences(jwt);

            // Create new user
            User.UserBuilder builder = User.builder()
                    .cognitoUserId(cognitoUserId)
                    .username(username)
                    .email(email != null ? email : "")
                    .firstName(firstName != null ? firstName : "")
                    .lastName(lastName != null ? lastName : "")
                    .roles(roles)
                    .isActive(true);
            if (language != null && !language.isEmpty()) {
                builder.preferences(UserPreferences.builder().language(language).build());
            }
            User newUser = builder.build();

            User savedUser = userRepository.save(newUser);
            request.setAttribute(RESOLVED_USER_ATTRIBUTE, savedUser);

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
     * Read first/last name from the Cognito `custom:preferences` JSON attribute.
     *
     * The signup form (web-frontend/src/services/auth/authService.ts:215-241)
     * packs profile data as JSON in this single attribute because
     * given_name/family_name are not in our Cognito write-attribute schema.
     *
     * Returns [firstName, lastName] with null entries when missing; never throws.
     */
    private String[] extractNamesFromPreferences(Jwt jwt) {
        String raw = jwt.getClaimAsString("custom:preferences");
        if (raw == null || raw.isEmpty()) {
            return new String[] {null, null};
        }
        try {
            JsonNode node = PREFERENCES_MAPPER.readTree(raw);
            String first = node.path("firstName").asText(null);
            String last  = node.path("lastName").asText(null);
            return new String[] {
                (first != null && !first.isEmpty()) ? first : null,
                (last  != null && !last.isEmpty())  ? last  : null
            };
        } catch (Exception e) {
            log.warn("Failed to parse custom:preferences JSON during JIT provisioning: {}", e.getMessage());
            return new String[] {null, null};
        }
    }

    /**
     * Read the UI {@code language} from the Cognito {@code custom:preferences} JSON attribute.
     *
     * Mirrors {@code post-confirmation.ts} (`const language = preferences.language || 'de'`):
     * the signup form / federated attribute mapping packs the chosen language into the same
     * single JSON attribute as first/last name. JIT is the only provisioning path federated
     * users hit, so it must carry the language too.
     *
     * Returns the language code (e.g. "fr"/"en"/"de") or {@code null} when absent/empty/
     * malformed; never throws (the create path must stay non-blocking).
     */
    private String extractLanguageFromPreferences(Jwt jwt) {
        String raw = jwt.getClaimAsString("custom:preferences");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = PREFERENCES_MAPPER.readTree(raw);
            return normalizeLanguage(node.path("language").asText(null));
        } catch (Exception e) {
            log.warn("Failed to parse custom:preferences JSON for language during JIT provisioning: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * Normalize a raw language value to a supported 2-char code, or null.
     * <p>
     * Story 12.3 review: {@code pref_language} is {@code VARCHAR(2)}; a raw BCP-47 tag such as
     * {@code gsw-BE} / {@code fr-CH} (the frontend sends {@code i18n.language} verbatim, and
     * federated SSO delivers region-tagged codes) would overflow the column and abort the
     * INSERT — silently dropping JIT provisioning for that identity. We take the primary
     * subtag ({@code fr-CH -> fr}), lowercase it, and accept it only if it is a supported
     * 2-char code; anything else returns null so the {@code @PrePersist} default {@code "de"}
     * applies. Also defuses non-string JSON values ({@code asText} coercions like {@code "123"})
     * and stray casing/whitespace.
     *
     * @param raw the language value parsed from {@code custom:preferences} (may be null)
     * @return a supported 2-char code, or null to fall back to the default
     */
    private String normalizeLanguage(String raw) {
        if (raw == null) {
            return null;
        }
        String primary = raw.trim().toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
        return SUPPORTED_LANGUAGE_CODES.contains(primary) ? primary : null;
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
