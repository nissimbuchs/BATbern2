package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.events.UserCreatedEvent;
import ch.batbern.companyuser.events.UserDeletedEvent;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.exception.UserValidationException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.service.SlugGenerationService;
import io.micrometer.core.annotation.Counted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * UserService - Core business logic for user management
 * Story 1.16.2: Implements dual-identifier pattern (UUID internal, username public)
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final CognitoIntegrationService cognitoService;
    private final DomainEventPublisher eventPublisher;
    private final UserSearchService searchService;
    private final SecurityContextHelper securityContext;
    private final SlugGenerationService slugService;
    private final UserResponseMapper responseMapper;

    /**
     * Get current authenticated user
     * Story 1.16.2: SecurityContext returns Cognito user ID (sub claim from JWT)
     * AC1: Current user retrieval
     * AC14: Resource expansion support
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        log.debug("Fetching current authenticated user");
        String cognitoUserId = securityContext.getCurrentUserId();  // Returns Cognito user ID (sub claim from JWT)
        User user = userRepository.findByCognitoUserId(cognitoUserId)
                .orElseThrow(() -> new UserNotFoundException(cognitoUserId));
        return responseMapper.mapToResponse(user);
    }

    /**
     * Get current authenticated user with resource expansion
     * AC14: Resource expansion (?include=company,preferences,settings)
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String include) {
        UserResponse response = getCurrentUser();
        if (include != null && !include.isEmpty()) {
            response = expandResources(response, include);
        }
        return response;
    }

    /**
     * Update current authenticated user
     * Story 1.16.2: Uses Cognito user ID from SecurityContext for lookup
     * AC2: Cognito sync on update
     */
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        log.info("Updating current user profile");
        String cognitoUserId = securityContext.getCurrentUserId();  // Returns Cognito user ID (sub claim from JWT)

        User user = userRepository.findByCognitoUserId(cognitoUserId)
                .orElseThrow(() -> new UserNotFoundException(cognitoUserId));

        // Update fields
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        User updatedUser = userRepository.save(user);

        // NOTE: Cognito sync removed - DB is source of truth for user data
        // Cognito is only used for authentication (JWT tokens)
        // TODO: If needed, user can update email via Cognito pre-token-generation hook

        // Invalidate search cache
        searchService.invalidateCache();

        // Publish domain event (Story 1.16.2: String IDs)
        // TODO: Track updated fields and previous values for better event audit trail
        java.util.Map<String, Object> updatedFields = new java.util.HashMap<>();
        if (request.getFirstName() != null) updatedFields.put("firstName", request.getFirstName());
        if (request.getLastName() != null) updatedFields.put("lastName", request.getLastName());
        if (request.getEmail() != null) updatedFields.put("email", request.getEmail());
        if (request.getBio() != null) updatedFields.put("bio", request.getBio());

        UserUpdatedEvent event = new UserUpdatedEvent(
            updatedUser.getUsername(),  // aggregateId = username
            updatedFields,
            null,  // previousValues - TODO: implement in future
            updatedUser.getUsername()  // userId = username (who performed the update)
        );
        eventPublisher.publish(event);

        log.info("User updated successfully: {}", updatedUser.getUsername());
        return responseMapper.mapToResponse(updatedUser);
    }

    /**
     * List users with filters (admin/organizer only)
     * Story 1.16.2: Public API uses username
     * AC3: List users with role, company, search, and JSON filters
     */
    @Transactional(readOnly = true)
    public java.util.List<UserResponse> listUsers(String roleFilter, String companyFilter, String search, String jsonFilter) {
        log.debug("User Service Listing users with filters: role={}, company={}, search={}, filter={}",
                roleFilter, companyFilter, search, jsonFilter);

        java.util.List<User> users;

        if (roleFilter != null && companyFilter != null) {
            // Both filters
            Role role = parseRole(roleFilter);
            log.debug("Filtering by role: {} and company: {}", role, companyFilter);
            users = userRepository.findByRolesContaining(role).stream()
                    .filter(u -> companyFilter.equals(u.getCompanyId()))
                    .toList();
        } else if (roleFilter != null) {
            // Role filter only
            Role role = parseRole(roleFilter);
            log.debug("Filtering by role: {}", role);
            users = userRepository.findByRolesContaining(role);
            log.debug("Found {} users with role {}", users.size(), role);
        } else if (companyFilter != null) {
            // Company filter only
            users = userRepository.findByCompanyId(companyFilter);
        } else {
            // No filters
            users = userRepository.findAll();
        }

        // Apply search filter (name, email, or username)
        if (search != null && !search.isBlank()) {
            String searchLower = search.toLowerCase();
            users = users.stream()
                    .filter(u ->
                            (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(searchLower)) ||
                            (u.getLastName() != null && u.getLastName().toLowerCase().contains(searchLower)) ||
                            (u.getEmail() != null && u.getEmail().toLowerCase().contains(searchLower)) ||
                            (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchLower))
                    )
                    .toList();
        }

        // Apply JSON filter (active status, etc.)
        if (jsonFilter != null && !jsonFilter.isBlank()) {
            users = applyJsonFilter(users, jsonFilter);
        }

        return users.stream()
                .map(responseMapper::mapToResponse)
                .toList();
    }

    /**
     * Apply JSON filter to user list
     * Supports: {"active": true/false}
     */
    private java.util.List<User> applyJsonFilter(java.util.List<User> users, String jsonFilter) {
        try {
            // URL decode the filter if needed (frontend sends URL-encoded JSON)
            String decodedFilter = jsonFilter;
            try {
                decodedFilter = java.net.URLDecoder.decode(jsonFilter, java.nio.charset.StandardCharsets.UTF_8);
                log.debug("URL-decoded filter from '{}' to '{}'", jsonFilter, decodedFilter);
            } catch (Exception e) {
                log.debug("Filter does not need URL decoding, using as-is: {}", jsonFilter);
            }

            // Simple JSON parsing for active filter
            if (decodedFilter.contains("\"active\":true") || decodedFilter.contains("\"active\": true")) {
                java.util.List<User> filtered = users.stream().filter(User::isActive).toList();
                log.debug("Active=true filter applied: {} -> {} users", users.size(), filtered.size());
                return filtered;
            } else if (decodedFilter.contains("\"active\":false") || decodedFilter.contains("\"active\": false")) {
                java.util.List<User> filtered = users.stream().filter(u -> !u.isActive()).toList();
                log.debug("Active=false filter applied: {} -> {} users", users.size(), filtered.size());
                return filtered;
            }
            log.debug("No active filter matched, returning {} users", users.size());
            return users;
        } catch (Exception e) {
            log.warn("Failed to parse JSON filter: {}", jsonFilter, e);
            return users;
        }
    }

    /**
     * Parse role string to Role enum with validation
     */
    private Role parseRole(String roleStr) {
        try {
            return Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UserValidationException("role",
                String.format("Invalid role: %s. Valid roles are: ORGANIZER, SPEAKER, PARTNER, ATTENDEE", roleStr));
        }
    }

    /**
     * Get user by username
     * Story 1.16.2: Public API uses username for lookups
     * AC5: Get user with resource expansion
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return responseMapper.mapToResponse(user);
    }

    /**
     * Get user by username with resource expansion
     * AC14: Resource expansion (?include=company,preferences,settings)
     */
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username, String include) {
        UserResponse response = getUserByUsername(username);
        if (include != null && !include.isEmpty()) {
            response = expandResources(response, include);
        }
        return response;
    }

    /**
     * Delete user (GDPR compliance)
     * Story 1.16.2: Public API uses username
     * AC11: GDPR delete user
     */
    @Counted(value = "users.gdprDeletions", description = "Count of GDPR user deletions")
    public void deleteUser(String username) {
        log.info("Deleting user (GDPR): {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // TODO: Check business rules (e.g., last organizer) in future task
        // TODO: Cascade delete across domain services in future task
        // TODO: Audit logging in future task

        userRepository.delete(user);

        // Publish domain event (Story 1.16.2: String IDs)
        // Note: For delete, we need to get the current user performing the action
        String deletedByUsername = getUsernameFromCurrentContext();
        UserDeletedEvent event = new UserDeletedEvent(
            user.getUsername(),  // aggregateId = username
            user.getEmail(),
            "GDPR compliance",  // reason
            deletedByUsername  // userId = username (who performed the deletion)
        );
        eventPublisher.publish(event);

        log.info("User deleted successfully: {}", username);
    }

    /**
     * Get-or-create pattern for domain services
     * Story 1.16.2: Returns username as userId (meaningful ID, not UUID)
     * AC12: Get-or-create user with idempotency
     */
    public GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request) {
        log.info("Get-or-create user for email: {}", request.getEmail());

        return userRepository.findByEmail(request.getEmail())
                .map(existingUser -> {
                    log.debug("User already exists: {}", existingUser.getUsername());
                    return new GetOrCreateUserResponse()
                            .username(existingUser.getUsername())  // Story 1.16.2: username
                            .created(false)
                            .user(responseMapper.mapToResponse(existingUser));
                })
                .orElseGet(() -> {
                    if (request.getCreateIfMissing() != null && request.getCreateIfMissing()) {
                        log.info("Creating new user: {}", request.getEmail());
                        User newUser = createNewUser(request);
                        return new GetOrCreateUserResponse()
                                .username(newUser.getUsername())  // Story 1.16.2: username
                                .created(true)
                                .cognitoUserId(newUser.getCognitoUserId())
                                .user(responseMapper.mapToResponse(newUser));
                    } else {
                        throw new UserNotFoundException("User not found: " + request.getEmail());
                    }
                });
    }

    /**
     * Create new user (for ORGANIZER/ADMIN via API)
     * Story 2.5.2 AC4: User Creation
     *
     * @param request Create user request from frontend
     * @return Created user response
     */
    @Transactional
    public UserResponse createUser(ch.batbern.companyuser.dto.generated.CreateUserRequest request) {
        log.info("Creating new user: {}", request.getEmail());

        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserValidationException("User with email " + request.getEmail() + " already exists");
        }

        // NOTE: No longer creating user in Cognito - invitation-based flow
        // Organizer creates DB record → Send invitation email → User signs up via registration page
        // → Cognito pre-token-generation hook will populate cognitoUserId on first login
        // TODO: Implement invitation system (generate token, send email, registration page validation)

        // Story 1.16.2: Generate username from first/last name
        String baseUsername = slugService.generateUsername(request.getFirstName(), request.getLastName());
        String username = slugService.ensureUniqueUsername(baseUsername, userRepository::existsByUsername);

        // Determine initial roles (default to ATTENDEE if not specified)
        Set<Role> initialRoles = request.getInitialRoles() != null && !request.getInitialRoles().isEmpty()
                ? request.getInitialRoles().stream()
                    .map(roleEnum -> Role.valueOf(roleEnum.getValue()))
                    .collect(java.util.stream.Collectors.toSet())
                : Set.of(Role.ATTENDEE);

        User user = User.builder()
                .cognitoUserId(null)  // Will be populated by Cognito hook on first login
                .email(request.getEmail())
                .username(username)  // Story 1.16.2: Generated username
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .companyId(request.getCompanyId())  // Story 1.16.2: company name
                .bio(request.getBio())
                .roles(initialRoles)
                .build();

        User savedUser = userRepository.save(user);

        // Publish domain event (Story 1.16.2: String IDs)
        String createdByUsername = getUsernameFromCurrentContext();
        UserCreatedEvent event = new UserCreatedEvent(
            savedUser.getUsername(),  // aggregateId = username
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            savedUser.getCompanyId(),  // company name
            savedUser.getCognitoUserId(),  // cognitoUserId
            createdByUsername  // createdBy = username (who created this user)
        );
        eventPublisher.publish(event);

        log.info("User created successfully: {}", savedUser.getUsername());
        return responseMapper.mapToResponse(savedUser);
    }

    /**
     * Create new user with username generation
     * Story 1.16.2: Generate username using SlugGenerationService
     */
    private User createNewUser(GetOrCreateUserRequest request) {
        // Create user in Cognito if needed (AC2)
        String cognitoUserId = (request.getCognitoSync() != null && request.getCognitoSync())
                ? cognitoService.createCognitoUser(request)
                : null;

        // Story 1.16.2: Generate username from first/last name
        String baseUsername = slugService.generateUsername(request.getFirstName(), request.getLastName());
        String username = slugService.ensureUniqueUsername(baseUsername, userRepository::existsByUsername);

        User user = User.builder()
                .cognitoUserId(cognitoUserId)
                .email(request.getEmail())
                .username(username)  // Story 1.16.2: Generated username
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .companyId(request.getCompanyId())  // Story 1.16.2: company name
                .roles(Set.of(Role.ATTENDEE))
                .build();

        User savedUser = userRepository.save(user);

        // Publish domain event (Story 1.16.2: String IDs)
        // Note: For create, get the current user performing the action
        String createdByUsername = getUsernameFromCurrentContext();
        UserCreatedEvent event = new UserCreatedEvent(
            savedUser.getUsername(),  // aggregateId = username
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            savedUser.getCompanyId(),  // company name
            savedUser.getCognitoUserId(),  // cognitoUserId
            createdByUsername  // createdBy = username (who created this user)
        );
        eventPublisher.publish(event);

        log.info("User created successfully: {}", savedUser.getUsername());
        return savedUser;
    }

    /**
     * Expand resources based on include parameter
     * AC14: Resource expansion (?include=company,preferences,settings)
     * @param response Base user response
     * @param include Comma-separated list of resources to expand
     * @return User response with expanded resources
     */
    private UserResponse expandResources(UserResponse response, String include) {
        String[] resources = include.split(",");

        for (String resource : resources) {
            switch (resource.trim().toLowerCase()) {
                case "company":
                    if (response.getCompanyId() != null) {
                        // TODO: Fetch company details from Company Management Service in Task 14
                        // For now, return minimal company info
                        ch.batbern.companyuser.dto.generated.Company company =
                            new ch.batbern.companyuser.dto.generated.Company()
                                .id(response.getCompanyId())
                                .name(response.getCompanyId());  // Placeholder
                        response.setCompany(company);
                    }
                    break;
                case "preferences":
                    // Fetch user with preferences
                    User user = userRepository.findByUsername(response.getId())
                            .orElseThrow(() -> new UserNotFoundException(response.getId()));
                    if (user.getPreferences() != null) {
                        response.setPreferences(mapPreferencesToDTO(user.getPreferences()));
                    }
                    break;
                case "settings":
                    // Fetch user with settings
                    User userWithSettings = userRepository.findByUsername(response.getId())
                            .orElseThrow(() -> new UserNotFoundException(response.getId()));
                    if (userWithSettings.getSettings() != null) {
                        response.setSettings(mapSettingsToDTO(userWithSettings.getSettings()));
                    }
                    break;
                case "roles":
                    // Roles are already included in base response
                    break;
                default:
                    log.warn("Unknown resource for expansion: {}", resource);
            }
        }

        return response;
    }

    /**
     * Map domain UserPreferences to generated DTO
     */
    private ch.batbern.companyuser.dto.generated.UserPreferences mapPreferencesToDTO(
            ch.batbern.companyuser.domain.UserPreferences domain) {
        return new ch.batbern.companyuser.dto.generated.UserPreferences()
                .theme(ch.batbern.companyuser.dto.generated.UserPreferences.ThemeEnum.valueOf(
                    domain.getTheme().toUpperCase()))
                .language(ch.batbern.companyuser.dto.generated.UserPreferences.LanguageEnum.valueOf(
                    domain.getLanguage().toUpperCase()))
                .emailNotifications(domain.isEmailNotifications());
    }

    /**
     * Map domain UserSettings to generated DTO
     */
    private ch.batbern.companyuser.dto.generated.UserSettings mapSettingsToDTO(
            ch.batbern.companyuser.domain.UserSettings domain) {
        return new ch.batbern.companyuser.dto.generated.UserSettings()
                .twoFactorEnabled(domain.isTwoFactorEnabled())
                .profileVisibility(ch.batbern.companyuser.dto.generated.UserSettings.ProfileVisibilityEnum.valueOf(
                    domain.getProfileVisibility().toUpperCase()));
    }

    /**
     * Helper method to get username from current security context
     * Looks up user by Cognito User ID and returns their username
     * Story 4.1.5: Returns "anonymous" for unauthenticated requests (anonymous registration)
     */
    private String getUsernameFromCurrentContext() {
        try {
            String cognitoUserId = securityContext.getCurrentUserId();
            return userRepository.findByCognitoUserId(cognitoUserId)
                    .map(User::getUsername)
                    .orElse("system");  // Fallback for system operations
        } catch (SecurityException e) {
            // Story 4.1.5: Anonymous requests (no authenticated user)
            log.debug("No authenticated user in context, using 'anonymous' for audit");
            return "anonymous";
        }
    }

}
