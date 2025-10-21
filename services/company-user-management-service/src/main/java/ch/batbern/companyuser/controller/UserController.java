package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.*;
import ch.batbern.companyuser.dto.generated.CreateUserRequest;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.service.ProfilePictureService;
import ch.batbern.companyuser.service.UserSearchService;
import ch.batbern.companyuser.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for User Management
 * Handles user profile CRUD operations with security and validation
 *
 * Story 1.14-2 Task 11: REST Controllers (GREEN phase)
 * AC: 1, 2, 3, 5
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserSearchService userSearchService;
    private final ProfilePictureService profilePictureService;
    private final SecurityContextHelper securityContextHelper;
    private final UserRepository userRepository;

    /**
     * AC1: Get current authenticated user
     * GET /api/v1/users/me?include=company,preferences,settings
     *
     * @param include Optional resources to expand (company, preferences, settings, roles)
     * @return Current user profile
     */
    @GetMapping("/me")
    @Timed(value = "users.getCurrentUser", description = "Time to get current authenticated user", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> getCurrentUser(
            @RequestParam(required = false) String include) {
        log.debug("Getting current authenticated user with include: {}", include);

        UserResponse response = (include != null && !include.isEmpty())
                ? userService.getCurrentUser(include)
                : userService.getCurrentUser();

        return ResponseEntity.ok(response);
    }

    /**
     * AC2: Update current user profile
     * PUT /api/v1/users/me
     *
     * @param request Update request with validation
     * @return Updated user profile
     */
    @PutMapping("/me")
    @Timed(value = "users.updateCurrentUser", description = "Time to update current user profile", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> updateCurrentUser(
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating current user profile");

        UserResponse response = userService.updateCurrentUser(request);

        return ResponseEntity.ok(response);
    }

    /**
     * AC4: Create new user (Organizer/Admin only)
     * POST /api/v1/users
     * Story 2.5.2 - User Management Frontend
     *
     * @param request Create user request with validation
     * @return Created user profile with 201 status
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Timed(value = "users.createUser", description = "Time to create new user (admin/organizer)", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating new user: {}", request.getEmail());

        UserResponse response = userService.createUser(request);

        return ResponseEntity.status(201).body(response);
    }

    /**
     * AC3: List users (admin/organizer only)
     * GET /api/v1/users?filter={}&role={}&company={}&page={}&limit={}
     *
     * @param filter Advanced JSON filter (Task 14)
     * @param role Filter by role
     * @param company Filter by company ID
     * @param page Page number (default 0)
     * @param limit Page size (default 20)
     * @return Paginated list of users
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Timed(value = "users.listUsers", description = "Time to list users (admin/organizer)", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        log.debug("Listing users with filters: role={}, company={}, page={}, limit={}",
                role, company, page, limit);

        List<UserResponse> users = userService.listUsers(role, company);

        // TODO: Implement pagination (Task 14)
        Map<String, Object> response = Map.of(
                "users", users,
                "pagination", Map.of(
                        "page", page,
                        "limit", limit,
                        "total", users.size()
                )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * AC5: Get user by username
     * GET /api/v1/users/{username}?include=company,roles,preferences
     *
     * @param username User username (Story 1.16.2: meaningful ID)
     * @param include Optional resources to expand
     * @return User profile
     */
    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(
            @PathVariable String username,
            @RequestParam(required = false) String include) {
        log.debug("Getting user by username: {} with include: {}", username, include);

        UserResponse response = (include != null && !include.isEmpty())
                ? userService.getUserByUsername(username, include)
                : userService.getUserByUsername(username);

        return ResponseEntity.ok(response);
    }

    /**
     * AC12: Get-or-create user (for domain service integration)
     * POST /api/v1/users/get-or-create
     *
     * @param request Get-or-create request
     * @return User response with created flag
     */
    @PostMapping("/get-or-create")
    @PreAuthorize("hasAnyRole('SYSTEM', 'SERVICE')")  // Service-to-service auth
    @Timed(value = "users.getOrCreateUser", description = "Time to get or create user (service-to-service)", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<GetOrCreateUserResponse> getOrCreateUser(
            @Valid @RequestBody GetOrCreateUserRequest request) {
        log.info("Get-or-create user for email: {}", request.getEmail());

        GetOrCreateUserResponse response = userService.getOrCreateUser(request);

        return ResponseEntity.ok(response);
    }

    /**
     * AC4: Search users with autocomplete and caching
     * GET /api/v1/users/search?query={}&role={}
     *
     * Performance: <100ms P95 with Caffeine cache
     *
     * @param query Search query (first name or last name)
     * @param role Optional role filter
     * @return List of matching users (max 20 for autocomplete)
     */
    @GetMapping("/search")
    @Timed(value = "users.searchUsers", description = "Time to search users with caching", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam String query,
            @RequestParam(required = false) String role) {
        log.debug("Searching users with query: {} and role: {}", query, role);

        Role roleFilter = role != null ? Role.valueOf(role.toUpperCase()) : null;
        List<UserResponse> results = userSearchService.searchUsers(query, roleFilter);

        return ResponseEntity.ok(results);
    }

    /**
     * AC11: Delete user (GDPR compliance)
     * DELETE /api/v1/users/{username}
     *
     * @param username User username to delete
     * @return No content
     */
    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "users.deleteUser", description = "Time to delete user (GDPR compliance)", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        log.warn("Deleting user (GDPR): {}", username);

        userService.deleteUser(username);

        return ResponseEntity.noContent().build();
    }

    /**
     * AC10: Request presigned URL for profile picture upload
     * POST /api/v1/users/me/picture/presigned-url
     *
     * @param request Upload request with file metadata
     * @return Presigned upload URL with metadata
     */
    @PostMapping("/me/picture/presigned-url")
    @Timed(value = "users.profilePicture.requestPresignedUrl", description = "Time to generate presigned URL for profile picture", percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PresignedUploadUrl> requestProfilePictureUploadUrl(
            @Valid @RequestBody ProfilePictureUploadRequest request) {
        log.info("Requesting presigned URL for profile picture upload: {}", request.getFileName());

        // Get current user from security context (Story 1.16.2: username-based lookup)
        String currentUsername = securityContextHelper.getCurrentUserId();
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(currentUsername));

        PresignedUploadUrl response = profilePictureService.generateProfilePictureUploadUrl(
            user.getId(),  // UUID for internal use
            user.getUsername(),  // username for S3 key
            request.getFileName(),
            request.getFileSize()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * AC10: Confirm profile picture upload completion
     * POST /api/v1/users/me/picture/confirm
     *
     * @param request Confirmation request with file ID
     * @return CloudFront URL for the uploaded picture
     */
    @PostMapping("/me/picture/confirm")
    public ResponseEntity<ProfilePictureUploadConfirmResponse> confirmProfilePictureUpload(
            @Valid @RequestBody ProfilePictureUploadConfirmRequest request) {
        log.info("Confirming profile picture upload: fileId={}", request.getFileId());

        // Get current user from security context (Story 1.16.2: username-based lookup)
        String currentUsername = securityContextHelper.getCurrentUserId();
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(currentUsername));

        profilePictureService.confirmProfilePictureUpload(
            user.getId(),  // UUID for internal use
            user.getUsername(),  // username for S3 key
            request.getFileId(),
            request.getFileExtension()
        );

        // Fetch updated user to get CloudFront URL
        UserResponse updatedUser = userService.getCurrentUser();

        ProfilePictureUploadConfirmResponse response = ProfilePictureUploadConfirmResponse.builder()
            .profilePictureUrl(updatedUser.getProfilePictureUrl())
            .build();

        return ResponseEntity.ok(response);
    }
}
