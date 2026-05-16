package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.PatchUserProfileRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final CompanyService companyService;
    private final RoleService roleService;

    /**
     * Get current authenticated user
     * Story 1.16.2: SecurityContext returns username (custom:username claim from JWT)
     * ADR-003: Use username for user identification, not UUID
     * AC1: Current user retrieval
     * AC14: Resource expansion support
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        log.debug("Fetching current authenticated user");
        String username = securityContext.getCurrentUsername();  // Returns username (custom:username claim from JWT)
        // Fallback to cognitoUserId lookup when custom:username is not yet in JWT
        // (e.g. first login after organizer pre-registered the user; Cognito account just linked via JIT)
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByCognitoUserId(securityContext.getCurrentUserId()))
                .orElseThrow(() -> new UserNotFoundException(username));
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
     * Story 1.16.2: Uses username from SecurityContext for lookup
     * ADR-003: Use username for user identification, not UUID
     * AC2: Cognito sync on update
     */
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        log.info("Updating current user profile");
        String username = securityContext.getCurrentUsername();  // Returns username (custom:username claim from JWT)

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

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
        if (request.getCompanyId() != null) {
            user.setCompanyId(request.getCompanyId());
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
        if (request.getFirstName() != null) {
            updatedFields.put("firstName", request.getFirstName());
        }
        if (request.getLastName() != null) {
            updatedFields.put("lastName", request.getLastName());
        }
        if (request.getEmail() != null) {
            updatedFields.put("email", request.getEmail());
        }
        if (request.getBio() != null) {
            updatedFields.put("bio", request.getBio());
        }
        if (request.getCompanyId() != null) {
            updatedFields.put("companyId", request.getCompanyId());
        }

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
     * Update user profile by username (for organizer/admin)
     * Allows admins/organizers to update any user's profile
     *
     * @param username Username of user to update
     * @param request Update request with validation
     * @return Updated user profile
     */
    public UserResponse updateUserByUsername(String username, UpdateUserRequest request) {
        log.info("Updating user {} by admin/organizer", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

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
        if (request.getCompanyId() != null) {
            user.setCompanyId(request.getCompanyId());
        }

        User updatedUser = userRepository.save(user);

        // Invalidate search cache
        searchService.invalidateCache();

        // Publish domain event
        java.util.Map<String, Object> updatedFields = new java.util.HashMap<>();
        if (request.getFirstName() != null) {
            updatedFields.put("firstName", request.getFirstName());
        }
        if (request.getLastName() != null) {
            updatedFields.put("lastName", request.getLastName());
        }
        if (request.getEmail() != null) {
            updatedFields.put("email", request.getEmail());
        }
        if (request.getBio() != null) {
            updatedFields.put("bio", request.getBio());
        }
        if (request.getCompanyId() != null) {
            updatedFields.put("companyId", request.getCompanyId());
        }

        // Get current user performing the update
        String updatedBy = securityContext.getCurrentUserId();

        UserUpdatedEvent event = new UserUpdatedEvent(
            updatedUser.getUsername(),  // aggregateId = username
            updatedFields,
            null,  // previousValues - TODO: implement in future
            updatedBy  // userId = admin/organizer who performed the update
        );
        eventPublisher.publish(event);

        log.info("User {} updated successfully by {}", updatedUser.getUsername(), updatedBy);
        return responseMapper.mapToResponse(updatedUser);
    }

    /**
     * List users with filters (admin/organizer only)
     * Story 1.16.2: Public API uses username
     * AC3: List users with role, company, search, and JSON filters
     *
     * @deprecated Use listUsersPaginated() for better performance
     */
    @Deprecated
    @Transactional(readOnly = true)
    public java.util.List<UserResponse> listUsers(String roleFilter, String companyFilter, String search,
            String jsonFilter) {
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
                            (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(searchLower))
                            || (u.getLastName() != null && u.getLastName().toLowerCase().contains(searchLower))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(searchLower))
                            || (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchLower))
                    )
                    .toList();
        }

        // Apply JSON filter (active status, etc.)
        if (jsonFilter != null && !jsonFilter.isBlank()) {
            users = applyJsonFilter(users, jsonFilter);
        }

        // Sort alphabetically by lastName, then firstName
        return users.stream()
                .sorted((u1, u2) -> {
                    int lastNameCompare = compareNullable(u1.getLastName(), u2.getLastName());
                    if (lastNameCompare != 0) {
                        return lastNameCompare;
                    }
                    return compareNullable(u1.getFirstName(), u2.getFirstName());
                })
                .map(responseMapper::mapToResponse)
                .toList();
    }

    /**
     * List users with filters and database-level pagination (admin/organizer only)
     * Performance optimized version using JOIN FETCH and proper pagination
     *
     * @param roleFilter Role filter (ORGANIZER, SPEAKER, etc.)
     * @param companyFilter Company ID filter
     * @param search Search query (name, email, username)
     * @param jsonFilter JSON filter (active status, etc.)
     * @param page Page number (0-based)
     * @param limit Page size
     * @return Page of user responses with pagination metadata
     */
    private static final Set<String> ALLOWED_USER_SORT_FIELDS = Set.of("name", "email", "company");

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsersPaginated(String roleFilter,
            String companyFilter, String search, String jsonFilter,
            int page, int limit) {
        return listUsersPaginated(roleFilter, companyFilter,
                search, jsonFilter, page, limit, "name", "asc");
    }

    @Transactional(readOnly = true)
    // CHECKSTYLE.OFF: ParameterNumber - search with sort/filter/pagination params
    public Page<UserResponse> listUsersPaginated(String roleFilter,
            String companyFilter, String search, String jsonFilter,
            int page, int limit, String sortBy, String sortDir) {
    // CHECKSTYLE.ON: ParameterNumber
        log.debug("UserService: Paginated listing - role={}, company={},"
                + " search={}, filter={}, page={}, limit={}, sort={}/{}",
                roleFilter, companyFilter, search, jsonFilter,
                page, limit, sortBy, sortDir);

        String safeSortBy = ALLOWED_USER_SORT_FIELDS.contains(sortBy)
                ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        // Map frontend sort field names to entity property names
        String entitySortField = switch (safeSortBy) {
            case "name" -> "lastName";
            case "email" -> "email";
            case "company" -> "companyId";
            default -> "lastName";
        };

        Sort sort = Sort.by(direction, entitySortField);
        // Add secondary sort by firstName for name sort
        if ("name".equals(safeSortBy)) {
            sort = sort.and(Sort.by(direction, "firstName"));
        }

        Pageable pageable = PageRequest.of(page, limit, sort);
        Page<User> usersPage;

        // Use optimized repository methods with JOIN FETCH
        // Database-level search filtering when search parameter is provided
        if (search != null && !search.isBlank()) {
            // Search queries (database-level filtering)
            if (roleFilter != null && companyFilter != null) {
                // Search + role + company filters
                Role role = parseRole(roleFilter);
                log.debug("Searching with role: {} and company: {}", role, companyFilter);
                usersPage = userRepository.searchUsersByRoleAndCompanyWithRoles(search, role, companyFilter, pageable);
            } else if (roleFilter != null) {
                // Search + role filter
                Role role = parseRole(roleFilter);
                log.debug("Searching with role: {}", role);
                usersPage = userRepository.searchUsersByRoleWithRoles(search, role, pageable);
            } else if (companyFilter != null) {
                // Search + company filter
                log.debug("Searching with company: {}", companyFilter);
                usersPage = userRepository.searchUsersByCompanyWithRoles(search, companyFilter, pageable);
            } else {
                // Search only
                log.debug("Searching users: {}", search);
                usersPage = userRepository.searchUsersWithRoles(search, pageable);
            }
        } else {
            // No search - use existing filter queries
            if (roleFilter != null && companyFilter != null) {
                // Both filters
                Role role = parseRole(roleFilter);
                log.debug("Filtering by role: {} and company: {}", role, companyFilter);
                usersPage = userRepository.findByRoleAndCompanyWithRoles(role, companyFilter, pageable);
            } else if (roleFilter != null) {
                // Role filter only
                Role role = parseRole(roleFilter);
                log.debug("Filtering by role: {}", role);
                usersPage = userRepository.findByRolesContainingWithRoles(role, pageable);
            } else if (companyFilter != null) {
                // Company filter only
                usersPage = userRepository.findByCompanyIdWithRoles(companyFilter, pageable);
            } else {
                // No filters
                usersPage = userRepository.findAllWithRoles(pageable);
            }
        }

        // Apply JSON filter (active status, etc.)
        Page<User> filteredPage = usersPage;
        if (jsonFilter != null && !jsonFilter.isBlank()) {
            List<User> jsonFiltered = applyJsonFilter(usersPage.getContent(), jsonFilter);
            filteredPage = new org.springframework.data.domain.PageImpl<>(
                    jsonFiltered, pageable, jsonFiltered.size());
        }

        // Map to response DTOs
        return filteredPage.map(responseMapper::mapToResponse);
    }

    /**
     * Helper method to compare nullable strings (null values sorted last)
     */
    private int compareNullable(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 0;
        }
        if (s1 == null) {
            return 1;  // null comes after non-null
        }
        if (s2 == null) {
            return -1; // null comes after non-null
        }
        return s1.compareToIgnoreCase(s2);
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
        String deletedByUsername = getAuditUsername();
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
     * Story 3.2: Enhanced matching - also matches by name when unambiguous
     * AC12: Get-or-create user with idempotency
     */
    public GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request) {
        log.info("Get-or-create user for email: {}, name: {} {}",
                request.getEmail(), request.getFirstName(), request.getLastName());

        // 1. Try to find by email first (primary match)
        Optional<User> userByEmail = userRepository.findByEmail(request.getEmail());
        if (userByEmail.isPresent()) {
            User existingUser = userByEmail.get();
            log.debug("User found by email: {}", existingUser.getUsername());
            return new GetOrCreateUserResponse()
                    .username(existingUser.getUsername())  // Story 1.16.2: username
                    .created(false)
                    .user(responseMapper.mapToResponse(existingUser));
        }

        // 2. Try to find by name (Story 3.2: Batch import name matching)
        // Only match by name if unambiguous (exactly one user found)
        List<User> usersByName = userRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(
                request.getFirstName(),
                request.getLastName()
        );

        if (usersByName.size() == 1) {
            // Unambiguous name match - use this user
            User existingUser = usersByName.get(0);
            log.info("User found by unambiguous name match: {} (firstName: {}, lastName: {})",
                    existingUser.getUsername(), request.getFirstName(), request.getLastName());

            // Update user's email to the provided email (historical data correction)
            if (existingUser.getEmail() == null || existingUser.getEmail().isEmpty()
                    || existingUser.getEmail().endsWith("@batbern.ch")) {
                log.info("Updating email for user {} from {} to {}",
                        existingUser.getUsername(), existingUser.getEmail(), request.getEmail());
                existingUser.setEmail(request.getEmail());
                existingUser = userRepository.save(existingUser);
            }

            return new GetOrCreateUserResponse()
                    .username(existingUser.getUsername())
                    .created(false)
                    .user(responseMapper.mapToResponse(existingUser));
        } else if (usersByName.size() > 1) {
            log.warn("Ambiguous name match for {} {} - found {} users with same name",
                    request.getFirstName(), request.getLastName(), usersByName.size());
            // Fall through to create new user (ambiguous match)
        }

        // 3. No match found or ambiguous match - create new user if allowed
        if (request.getCreateIfMissing() != null && request.getCreateIfMissing()) {
            log.info("Creating new user: {} (email: {}, name: {} {})",
                    request.getEmail(), request.getEmail(), request.getFirstName(), request.getLastName());
            User newUser = createNewUser(request);
            return new GetOrCreateUserResponse()
                    .username(newUser.getUsername())  // Story 1.16.2: username
                    .created(true)
                    .cognitoUserId(newUser.getCognitoUserId())
                    .user(responseMapper.mapToResponse(newUser));
        } else {
            throw new UserNotFoundException("User not found: " + request.getEmail());
        }
    }

    /**
     * Provision a User with a role (idempotent).
     *
     * <p>Story 11.C.2 (AR13). Used by
     * {@link ch.batbern.events.service.SpeakerWorkflowService#transition} at the
     * CONTACTED → READY hook (called via {@code UserApiClient.provisionUserWithRole}).
     *
     * <p>Behaviour:
     * <ul>
     *   <li>If a User exists by email (case-insensitive lookup, matching the existing
     *       {@link #getOrCreateUser} contract), grants the requested role if it is not
     *       already held and returns the existing username with {@code created=false}.</li>
     *   <li>If the User does not exist, creates the User row, grants the requested role,
     *       and returns the generated username with {@code created=true}.</li>
     *   <li>Idempotent: re-calling for an already-provisioned user is a no-op and returns
     *       the same username. The {@code role_assignments} UNIQUE constraint plus
     *       {@link RoleService#addRole}'s duplicate-detection backs the guarantee at the
     *       persistence layer.</li>
     * </ul>
     *
     * <p>Cognito wiring is deliberately stubbed: the {@code temporaryPassword} field on
     * the response is always {@code null} in Story 11.C.2 and is reserved for Story 11.E.2
     * to populate once {@code AdminCreateUser}/{@code AdminSetUserPassword} are wired.
     *
     * @param request username (optional), email (required), firstName, lastName, role (required)
     * @return canonical username + {@code created} flag + {@code temporaryPassword=null}
     * @throws UserValidationException if email or role is missing/invalid
     */
    @Counted(value = "users.provisioning", description = "Count of user provisioning calls (Story 11.C.2)")
    public ProvisionUserResponse provisionUserWithRole(ProvisionUserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new UserValidationException("email", "Email is required for user provisioning");
        }
        if (request.getRole() == null) {
            throw new UserValidationException("role", "Role is required for user provisioning");
        }
        Role roleToGrant = parseRole(request.getRole().getValue());

        // P0 (review patch): role-grant whitelist. A non-ADMIN caller (ORGANIZER) must not be able to
        // grant arbitrary roles via this endpoint — Epic 11 only needs SPEAKER. Anything else is a 403
        // unless the caller is ADMIN.
        if (!securityContext.hasRole("ADMIN") && roleToGrant != Role.SPEAKER) {
            throw new UserValidationException(
                    "role",
                    "Non-ADMIN callers may only provision the SPEAKER role via /users/provision (got "
                            + roleToGrant + ")");
        }

        // P1 (review patch): normalize email to lowercase for both lookup AND persist so
        // "Jane@x.com" and "jane@x.com" resolve to the same User row. Idempotency claim now holds.
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        log.info("Provisioning user (Story 11.C.2): email={}, role={}", normalizedEmail, roleToGrant);

        // 1. Look up existing user by email (case-insensitive). Matches the getOrCreateUser contract.
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // Idempotent role grant — RoleService.addRole is a no-op if the role is already held.
            roleService.addRole(user.getUsername(), roleToGrant);
            log.info("Provisioning: existing user {} has role {} (created=false)",
                    user.getUsername(), roleToGrant);
            return new ProvisionUserResponse()
                    .username(user.getUsername())
                    .created(false)
                    .temporaryPassword(null);  // Story 11.E.2 wires Cognito
        }

        // P1 (review patch): firstName/lastName are optional in the API spec; SlugGenerationService
        // rejects blank inputs. Derive sensible defaults from the email local-part so the request
        // succeeds without forcing the caller to supply names (Story 11.E.2 may not have them yet).
        String firstName = request.getFirstName();
        String lastName = request.getLastName();
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            String localPart = normalizedEmail.contains("@")
                    ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                    : normalizedEmail;
            String[] parts = localPart.split("[._\\-+]", 2);
            if (firstName == null || firstName.isBlank()) {
                firstName = parts[0].isBlank() ? "Speaker" : capitalize(parts[0]);
            }
            if (lastName == null || lastName.isBlank()) {
                lastName = parts.length > 1 && !parts[1].isBlank()
                        ? capitalize(parts[1])
                        : "Unknown";
            }
            log.debug("Provisioning: derived firstName='{}' lastName='{}' from email local-part", firstName, lastName);
        }

        // 2. No user yet — create one, then grant the role.
        // Reuse the GetOrCreateUserRequest path so a single create-policy lives in one place.
        // cognitoSync=false: Cognito provisioning is Story 11.E.2's responsibility.
        GetOrCreateUserRequest createRequest = new GetOrCreateUserRequest()
                .email(normalizedEmail)
                .firstName(firstName)
                .lastName(lastName)
                .createIfMissing(true)
                .cognitoSync(false);

        User created;
        try {
            created = createNewUser(createRequest);
        } catch (DataIntegrityViolationException e) {
            // P1 (review patch): another caller raced us to createNewUser for the same email
            // and won. The email UNIQUE constraint fired. Treat as idempotent: re-fetch and
            // grant the role.
            log.info("Provisioning: lost create race for email={} — re-fetching as idempotent existing-user case",
                    normalizedEmail);
            User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "DataIntegrityViolation on createNewUser but email not found on re-fetch: "
                                    + normalizedEmail, e));
            roleService.addRole(user.getUsername(), roleToGrant);
            return new ProvisionUserResponse()
                    .username(user.getUsername())
                    .created(false)
                    .temporaryPassword(null);
        }

        // The default initial role from createNewUser is ATTENDEE. Grant the requested role if it's not
        // already there. addRole is idempotent per RoleService.addRole(...) duplicate-detection.
        roleService.addRole(created.getUsername(), roleToGrant);

        log.info("Provisioning: created user {} with role {} (created=true)", created.getUsername(), roleToGrant);
        return new ProvisionUserResponse()
                .username(created.getUsername())
                .created(true)
                .temporaryPassword(null);  // Story 11.E.2 wires Cognito (AdminCreateUser + AdminSetUserPassword)
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    /**
     * Patch user profile fields (bio, profilePictureUrl).
     *
     * <p>Story 11.C.2 (AR14). Called by the consolidated
     * {@code ContentSubmissionService} when an organizer (on behalf) or a speaker (self)
     * submits content that includes a CV blurb or a portrait.
     *
     * <p>Per ADR-009 §"Decision 2" + ADR-007 + Confirmed Decision §6.7: {@code bio} and
     * {@code profilePictureUrl} are owned by the User entity (single source of truth) and
     * are overwritten globally — there is no per-event snapshot.
     *
     * <p>Authorization (controller-enforced): ORGANIZER, ADMIN, or SPEAKER. SPEAKERS may
     * patch only their own profile (controller verifies the path variable matches the
     * current username).
     *
     * @param username target user's username
     * @param request  bio (nullable) + profilePictureUrl (nullable); ≥1 must be non-null
     * @return updated {@link UserResponse}
     * @throws UserNotFoundException   if the username does not exist
     * @throws UserValidationException if both fields are null (no-op patches rejected)
     */
    public UserResponse patchUserProfile(String username, PatchUserProfileRequest request) {
        if (request == null
                || (request.getBio() == null && request.getProfilePictureUrl() == null)) {
            throw new UserValidationException(
                    "patchUserProfile",
                    "At least one of 'bio' or 'profilePictureUrl' must be present");
        }

        // P2 (review patch): treat blank-but-non-null values as "no change". Prevents accidental
        // data loss when a UI submits an empty textarea (`{"bio":""}` previously silently cleared bio).
        // To explicitly clear a field, the caller should send `null` (which by the null-check above
        // is treated as "no change") — clearing is intentionally not supported via this endpoint.
        boolean bioPresent = request.getBio() != null && !request.getBio().isBlank();
        boolean picturePresent = request.getProfilePictureUrl() != null && !request.getProfilePictureUrl().isBlank();
        if (!bioPresent && !picturePresent) {
            throw new UserValidationException(
                    "patchUserProfile",
                    "At least one of 'bio' or 'profilePictureUrl' must be non-blank");
        }

        log.info("Patching user profile (Story 11.C.2): username={}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (bioPresent) {
            user.setBio(request.getBio());
        }
        if (picturePresent) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        User saved = userRepository.save(user);

        // Invalidate user-search caches (matches updateUserByUsername behaviour).
        searchService.invalidateCache();

        // Publish a UserUpdatedEvent so downstream listeners can refresh their view.
        java.util.Map<String, Object> updatedFields = new java.util.HashMap<>();
        if (bioPresent) {
            updatedFields.put("bio", request.getBio());
        }
        if (picturePresent) {
            updatedFields.put("profilePictureUrl", request.getProfilePictureUrl());
        }
        UserUpdatedEvent event = new UserUpdatedEvent(
                saved.getUsername(),
                updatedFields,
                null,
                getAuditUsername());
        eventPublisher.publish(event);

        log.info("User profile patched: username={}, fields={}", saved.getUsername(), updatedFields.keySet());
        return responseMapper.mapToResponse(saved);
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
        String createdByUsername = getAuditUsername();
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

        // Get or create company if display name provided (for anonymous registrations)
        String companyId = null;
        if (request.getCompanyId() != null && !request.getCompanyId().trim().isEmpty()) {
            // Treat companyId as display name and get/create company
            ch.batbern.companyuser.domain.Company company = companyService.getOrCreateCompany(request.getCompanyId());
            companyId = company.getName();  // Use the generated slug as companyId
            log.debug("Associated user with company: {} (display: {})", companyId, company.getDisplayName());
        }

        User user = User.builder()
                .cognitoUserId(cognitoUserId)
                .email(request.getEmail())
                .username(username)  // Story 1.16.2: Generated username
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .companyId(companyId)  // Story 1.16.2: company name (slug)
                .roles(new java.util.HashSet<>(Set.of(Role.ATTENDEE)))  // mutable so callers can add roles
                .build();

        User savedUser = userRepository.save(user);

        // Publish domain event (Story 1.16.2: String IDs)
        // Note: For create, get the current user performing the action
        String createdByUsername = getAuditUsername();
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
     * Get preferences for current authenticated user
     * Story 2.6 AC21, AC24, AC28: Retrieve user preferences
     *
     * @return User preferences DTO
     */
    public ch.batbern.companyuser.dto.generated.UserPreferences getCurrentUserPreferences() {
        String username = securityContext.getCurrentUsername();
        log.debug("Getting preferences for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.getPreferences() == null) {
            // Initialize with defaults if not set
            user.setPreferences(ch.batbern.companyuser.domain.UserPreferences.builder().build());
            userRepository.save(user);
        }

        return mapPreferencesToDTO(user.getPreferences());
    }

    /**
     * Update preferences for current authenticated user
     * Story 2.6 AC21: Persist theme changes
     * Story 2.6 AC24: Persist timezone changes
     * Story 2.6 AC28: Persist notification preferences
     *
     * @param preferencesDTO Updated preferences
     * @return Updated preferences DTO
     */
    @Transactional
    public ch.batbern.companyuser.dto.generated.UserPreferences updateCurrentUserPreferences(
            ch.batbern.companyuser.dto.generated.UserPreferences preferencesDTO) {
        String username = securityContext.getCurrentUsername();
        log.info("Updating preferences for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Update domain preferences from DTO
        if (user.getPreferences() == null) {
            user.setPreferences(new ch.batbern.companyuser.domain.UserPreferences());
        }

        ch.batbern.companyuser.domain.UserPreferences prefs = user.getPreferences();
        prefs.setTheme(preferencesDTO.getTheme().getValue());
        prefs.setLanguage(preferencesDTO.getLanguage().getValue());
        prefs.setEmailNotifications(preferencesDTO.getEmailNotifications());
        prefs.setInAppNotifications(preferencesDTO.getInAppNotifications());
        prefs.setPushNotifications(preferencesDTO.getPushNotifications());
        prefs.setNotificationFrequency(preferencesDTO.getNotificationFrequency().getValue());

        // Convert String to LocalTime for quiet hours
        if (preferencesDTO.getQuietHoursStart() != null) {
            prefs.setQuietHoursStart(LocalTime.parse(preferencesDTO.getQuietHoursStart()));
        }
        if (preferencesDTO.getQuietHoursEnd() != null) {
            prefs.setQuietHoursEnd(LocalTime.parse(preferencesDTO.getQuietHoursEnd()));
        }

        userRepository.save(user);

        log.info("Successfully updated preferences for user: {}", username);

        return mapPreferencesToDTO(user.getPreferences());
    }

    /**
     * Get settings for current authenticated user
     * Story 2.6 AC34: Retrieve user settings
     *
     * @return User settings DTO
     */
    public ch.batbern.companyuser.dto.generated.UserSettings getCurrentUserSettings() {
        String username = securityContext.getCurrentUsername();
        log.debug("Getting settings for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.getSettings() == null) {
            // Initialize with defaults if not set
            user.setSettings(ch.batbern.companyuser.domain.UserSettings.builder().build());
            userRepository.save(user);
        }

        return mapSettingsToDTO(user.getSettings());
    }

    /**
     * Update settings for current authenticated user
     * Story 2.6 AC34: Persist privacy settings
     *
     * @param settingsDTO Updated settings
     * @return Updated settings DTO
     */
    @Transactional
    public ch.batbern.companyuser.dto.generated.UserSettings updateCurrentUserSettings(
            ch.batbern.companyuser.dto.generated.UserSettings settingsDTO) {
        String username = securityContext.getCurrentUsername();
        log.info("Updating settings for user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Update domain settings from DTO
        if (user.getSettings() == null) {
            user.setSettings(new ch.batbern.companyuser.domain.UserSettings());
        }

        ch.batbern.companyuser.domain.UserSettings settings = user.getSettings();
        settings.setProfileVisibility(settingsDTO.getProfileVisibility().getValue());
        settings.setShowEmail(settingsDTO.getShowEmail());
        settings.setShowCompany(settingsDTO.getShowCompany());
        settings.setShowActivityHistory(settingsDTO.getShowActivityHistory());
        settings.setAllowMessaging(settingsDTO.getAllowMessaging());
        settings.setAllowCalendarSync(settingsDTO.getAllowCalendarSync());
        settings.setTimezone(settingsDTO.getTimezone());
        settings.setTwoFactorEnabled(settingsDTO.getTwoFactorEnabled());

        userRepository.save(user);

        log.info("Successfully updated settings for user: {}", username);

        return mapSettingsToDTO(user.getSettings());
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
                .emailNotifications(domain.isEmailNotifications())
                .inAppNotifications(domain.isInAppNotifications())
                .pushNotifications(domain.isPushNotifications())
                .notificationFrequency(ch.batbern.companyuser.dto.generated
                    .UserPreferences.NotificationFrequencyEnum.valueOf(
                        domain.getNotificationFrequency().toUpperCase()))
                .quietHoursStart(domain.getQuietHoursStart() != null
                    ? domain.getQuietHoursStart().toString() : null)
                .quietHoursEnd(domain.getQuietHoursEnd() != null
                    ? domain.getQuietHoursEnd().toString() : null);
    }

    /**
     * Map domain UserSettings to generated DTO
     */
    private ch.batbern.companyuser.dto.generated.UserSettings mapSettingsToDTO(
            ch.batbern.companyuser.domain.UserSettings domain) {
        return new ch.batbern.companyuser.dto.generated.UserSettings()
                .profileVisibility(ch.batbern.companyuser.dto.generated.UserSettings.ProfileVisibilityEnum.valueOf(
                    domain.getProfileVisibility().toUpperCase()))
                .showEmail(domain.isShowEmail())
                .showCompany(domain.isShowCompany())
                .showActivityHistory(domain.isShowActivityHistory())
                .allowMessaging(domain.isAllowMessaging())
                .allowCalendarSync(domain.isAllowCalendarSync())
                .timezone(domain.getTimezone())
                .twoFactorEnabled(domain.isTwoFactorEnabled());
    }

    /**
     * Helper method to get username from current security context for audit purposes
     * Uses SecurityContextHelper which reads custom:username claim from JWT
     * Story 4.1.5: Returns "anonymous" for unauthenticated requests (anonymous registration)
     */
    private String getAuditUsername() {
        try {
            return securityContext.getCurrentUsername();
        } catch (SecurityException e) {
            // Story 4.1.5: Anonymous requests (no authenticated user)
            log.debug("No authenticated user in context, using 'anonymous' for audit");
            return "anonymous";
        }
    }

}
