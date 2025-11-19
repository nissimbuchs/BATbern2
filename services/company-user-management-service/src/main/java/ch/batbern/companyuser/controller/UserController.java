package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.dto.ProfilePictureUploadConfirmRequest;
import ch.batbern.companyuser.dto.ProfilePictureUploadConfirmResponse;
import ch.batbern.companyuser.dto.ProfilePictureUploadRequest;
import ch.batbern.companyuser.dto.ReconciliationReportDTO;
import ch.batbern.companyuser.dto.SyncStatusDTO;
import ch.batbern.companyuser.dto.generated.CreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.PaginatedUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UpdateUserRolesRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.dto.generated.UserRolesResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private final ch.batbern.companyuser.service.UserReconciliationService reconciliationService;
    private final ch.batbern.companyuser.service.RoleService roleService;

    /**
     * AC1: Get current authenticated user
     * GET /api/v1/users/me?include=company,preferences,settings
     *
     * @param include Optional resources to expand (company, preferences, settings, roles)
     * @return Current user profile
     */
    @GetMapping("/me")
    @Timed(value = "users.getCurrentUser",
            description = "Time to get current authenticated user",
            percentiles = {0.5, 0.95, 0.99})
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
    @Timed(value = "users.updateCurrentUser",
            description = "Time to update current user profile",
            percentiles = {0.5, 0.95, 0.99})
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
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.createUser",
            description = "Time to create new user (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
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
     * @param page Page number - 1-based (default 1, first page)
     * @param limit Page size (default 20)
     * @return Paginated list of users
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.listUsers",
            description = "Time to list users (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PaginatedUserResponse> listUsers(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        log.debug("UserController Listing users with filters: role={}, company={}, search={}, page={}, limit={}",
                role, company, search, page, limit);

        List<UserResponse> allUsers = userService.listUsers(role, company, search, filter);

        // Convert 1-based page to 0-based index for calculation
        int pageIndex = Math.max(0, page - 1);

        // Calculate pagination
        int total = allUsers.size();
        int totalPages = (int) Math.ceil((double) total / limit);
        int startIndex = pageIndex * limit;
        int endIndex = Math.min(startIndex + limit, total);

        // Slice the data to the requested page
        List<UserResponse> pageData = (startIndex < total)
            ? allUsers.subList(startIndex, endIndex)
            : List.of();

        // Build pagination metadata (using 1-based page numbers)
        ch.batbern.shared.api.PaginationMetadata paginationMetadata =
            new ch.batbern.shared.api.PaginationMetadata();
        paginationMetadata.setPage(page);
        paginationMetadata.setLimit(limit);
        paginationMetadata.setTotalItems((long) total);
        paginationMetadata.setTotalPages(totalPages);
        paginationMetadata.setHasNext(page < totalPages);
        paginationMetadata.setHasPrev(page > 1);

        // Use generated PaginatedUserResponse
        PaginatedUserResponse response = new PaginatedUserResponse();
        response.setData(pageData);  // CRITICAL: Return paginated data, not all users
        response.setPagination(paginationMetadata);

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
     * Update user profile by username (Organizer/Admin only)
     * PUT /api/v1/users/{username}
     *
     * Allows organizers/admins to update any user's profile
     *
     * @param username User username to update
     * @param request Update request with validation
     * @return Updated user profile
     */
    @PutMapping("/{username}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Timed(value = "users.updateUserByUsername",
            description = "Time to update user by username (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> updateUserByUsername(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating user {} by organizer/admin", username);

        UserResponse response = userService.updateUserByUsername(username, request);

        return ResponseEntity.ok(response);
    }

    /**
     * AC12: Get-or-create user (for domain service integration)
     * POST /api/v1/users/get-or-create
     *
     * Story 4.1.5: Made public for anonymous event registration (ADR-005)
     * Security config allows public access for this endpoint
     *
     * @param request Get-or-create request
     * @return User response with created flag
     */
    @PostMapping("/get-or-create")
    // Story 4.1.5: Removed @PreAuthorize to allow anonymous registration
    @Timed(value = "users.getOrCreateUser",
            description = "Time to get or create user (service-to-service)",
            percentiles = {0.5, 0.95, 0.99})
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
    @Timed(value = "users.searchUsers",
            description = "Time to search users with caching",
            percentiles = {0.5, 0.95, 0.99})
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
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.deleteUser",
            description = "Time to delete user (GDPR compliance)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        log.warn("Deleting user (GDPR): {}", username);

        userService.deleteUser(username);

        return ResponseEntity.noContent().build();
    }

    /**
     * AC8: Get user roles
     * GET /api/v1/users/{username}/roles
     *
     * @param username User username
     * @return User roles
     */
    @GetMapping("/{username}/roles")
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.getUserRoles",
            description = "Time to get user roles",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserRolesResponse> getUserRoles(@PathVariable String username) {
        log.info("Getting roles for user: {}", username);

        var roles = roleService.getUserRoles(username);
        var rolesDto = roles.stream()
                .map(role -> UserRolesResponse.RolesEnum.valueOf(role.name()))
                .toList();

        return ResponseEntity.ok(new UserRolesResponse()
                .username(username)
                .roles(rolesDto));
    }

    /**
     * AC8: Update user roles
     * PUT /api/v1/users/{username}/roles
     *
     * @param username User username
     * @param request Role update request
     * @return Updated user roles
     */
    @PutMapping("/{username}/roles")
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.updateUserRoles",
            description = "Time to update user roles",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserRolesResponse> updateUserRoles(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        log.info("Updating roles for user: {}", username);

        // Convert from DTO enum to domain enum
        var domainRoles = request.getRoles().stream()
                .map(roleEnum -> Role.valueOf(roleEnum.name()))
                .collect(java.util.stream.Collectors.toSet());

        var updatedRoles = roleService.setRoles(username, domainRoles);

        // Convert back to DTO enum
        var rolesDto = updatedRoles.stream()
                .map(role -> UserRolesResponse.RolesEnum.valueOf(role.name()))
                .toList();

        return ResponseEntity.ok(new UserRolesResponse()
                .username(username)
                .roles(rolesDto));
    }

    /**
     * AC10: Request presigned URL for profile picture upload
     * POST /api/v1/users/me/picture/presigned-url
     *
     * @param request Upload request with file metadata
     * @return Presigned upload URL with metadata
     */
    @PostMapping("/me/picture/presigned-url")
    @Timed(value = "users.profilePicture.requestPresignedUrl",
            description = "Time to generate presigned URL for profile picture",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PresignedUploadUrl> requestProfilePictureUploadUrl(
            @Valid @RequestBody ProfilePictureUploadRequest request) {
        log.info("Requesting presigned URL for profile picture upload: {}", request.getFileName());

        // Get current user from security context (Story 1.16.2: username-based lookup)
        String currentUsername = securityContextHelper.getCurrentUsername();
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
        String currentUsername = securityContextHelper.getCurrentUsername();
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
            .profilePictureUrl(updatedUser.getProfilePictureUrl() != null ?
                updatedUser.getProfilePictureUrl().toString() : null)
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Admin endpoint: Request presigned URL for profile picture upload for a specific user
     * POST /api/v1/users/{username}/picture/presigned-url
     *
     * Allows organizers to upload profile pictures for other users
     *
     * @param username Target user's username
     * @param request Upload request with file metadata
     * @return Presigned upload URL with metadata
     */
    @PostMapping("/{username}/picture/presigned-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.requestPresignedUrl",
            description = "Time to generate presigned URL for user profile picture (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PresignedUploadUrl> requestProfilePictureUploadUrlForUser(
            @PathVariable String username,
            @Valid @RequestBody ProfilePictureUploadRequest request) {
        log.info("Admin requesting presigned URL for profile picture upload for user: {}, file: {}",
                username, request.getFileName());

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        PresignedUploadUrl response = profilePictureService.generateProfilePictureUploadUrl(
            user.getId(),  // UUID for internal use
            user.getUsername(),  // username for S3 key
            request.getFileName(),
            request.getFileSize()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Admin endpoint: Confirm profile picture upload completion for a specific user
     * POST /api/v1/users/{username}/picture/confirm
     *
     * Allows organizers to confirm profile picture uploads for other users
     *
     * @param username Target user's username
     * @param request Confirmation request with file ID
     * @return CloudFront URL for the uploaded picture
     */
    @PostMapping("/{username}/picture/confirm")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.confirm",
            description = "Time to confirm profile picture upload for user (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<ProfilePictureUploadConfirmResponse> confirmProfilePictureUploadForUser(
            @PathVariable String username,
            @Valid @RequestBody ProfilePictureUploadConfirmRequest request) {
        log.info("Admin confirming profile picture upload for user: {}, fileId={}", username, request.getFileId());

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        profilePictureService.confirmProfilePictureUpload(
            user.getId(),  // UUID for internal use
            user.getUsername(),  // username for S3 key
            request.getFileId(),
            request.getFileExtension()
        );

        // Fetch updated user to get CloudFront URL
        UserResponse updatedUser = userService.getUserByUsername(username);

        ProfilePictureUploadConfirmResponse response = ProfilePictureUploadConfirmResponse.builder()
            .profilePictureUrl(updatedUser.getProfilePictureUrl() != null ?
                updatedUser.getProfilePictureUrl().toString() : null)
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Admin endpoint: Remove profile picture for a specific user
     * DELETE /api/v1/users/{username}/picture
     *
     * Allows organizers to remove profile pictures for other users
     *
     * @param username Target user's username
     * @return No content on success
     */
    @DeleteMapping("/{username}/picture")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.remove",
            description = "Time to remove profile picture for user (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> removeProfilePictureForUser(@PathVariable String username) {
        log.info("Admin removing profile picture for user: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        // Clear profile picture fields
        user.setProfilePictureUrl(null);
        user.setProfilePictureS3Key(null);
        userRepository.save(user);

        return ResponseEntity.noContent().build();
    }

    /**
     * Story 1.2.5: Manual user reconciliation (Admin only)
     * POST /api/v1/users/admin/reconcile
     *
     * Triggers manual sync from Cognito to Database
     * Creates missing database users for Cognito accounts
     * Deactivates orphaned database users (deleted in Cognito)
     *
     * @return Reconciliation report with sync statistics
     */
    @PostMapping("/admin/reconcile")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.admin.reconcile",
            description = "Time to reconcile users (Cognito to DB)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<ReconciliationReportDTO> reconcileUsers() {
        log.info("Manual user reconciliation triggered by admin");

        ch.batbern.companyuser.service.UserReconciliationService.ReconciliationReport report =
            reconciliationService.reconcileUsers();

        ReconciliationReportDTO response = ReconciliationReportDTO.builder()
            .orphanedUsersDeactivated(report.getOrphanedUsers())
            .missingUsersCreated(report.getMissingUsers())
            .durationMs(report.getDurationMs())
            .errors(report.getErrors())
            .success(report.getErrors().isEmpty())
            .message(buildReconciliationMessage(report))
            .build();

        log.info("User reconciliation completed: created={}, deactivated={}, duration={}ms",
            report.getMissingUsers(), report.getOrphanedUsers(), report.getDurationMs());

        return ResponseEntity.ok(response);
    }

    /**
     * Story 1.2.5: Check sync status (Admin only)
     * GET /api/v1/users/admin/sync-status
     *
     * Checks synchronization status between Cognito and Database
     * Returns counts and list of users out of sync
     *
     * @return Sync status with comparison statistics
     */
    @GetMapping("/admin/sync-status")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.admin.syncStatus",
            description = "Time to check sync status",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<SyncStatusDTO> getSyncStatus() {
        log.debug("Checking Cognito-Database sync status");

        ch.batbern.companyuser.service.UserReconciliationService.SyncStatus status =
            reconciliationService.checkSyncStatus();

        SyncStatusDTO response = SyncStatusDTO.builder()
            .cognitoUserCount(status.getCognitoUserCount())
            .databaseUserCount(status.getDatabaseUserCount())
            .missingInDatabase(status.getMissingInDatabase())
            .orphanedInDatabase(status.getOrphanedInDatabase())
            .missingCognitoIds(status.getMissingCognitoIds())
            .inSync(status.isInSync())
            .message(buildSyncStatusMessage(status))
            .build();

        log.debug("Sync status: cognito={}, db={}, missing={}, orphaned={}, inSync={}",
            status.getCognitoUserCount(), status.getDatabaseUserCount(),
            status.getMissingInDatabase(), status.getOrphanedInDatabase(), status.isInSync());

        return ResponseEntity.ok(response);
    }

    /**
     * Build human-readable reconciliation message
     */
    private String buildReconciliationMessage(ch.batbern.companyuser.service.UserReconciliationService.ReconciliationReport report) {
        if (!report.getErrors().isEmpty()) {
            return String.format("Reconciliation completed with %d error(s)", report.getErrors().size());
        }

        if (report.getMissingUsers() == 0 && report.getOrphanedUsers() == 0) {
            return "All users are in sync";
        }

        return String.format("Synchronized %d user(s): %d created, %d deactivated",
            report.getMissingUsers() + report.getOrphanedUsers(),
            report.getMissingUsers(),
            report.getOrphanedUsers());
    }

    /**
     * Build human-readable sync status message
     */
    private String buildSyncStatusMessage(ch.batbern.companyuser.service.UserReconciliationService.SyncStatus status) {
        if (status.getMessage() != null) {
            return status.getMessage();
        }

        if (status.isInSync()) {
            return String.format("All %d user(s) are in sync", status.getCognitoUserCount());
        }

        return String.format("Out of sync: %d missing in database, %d orphaned in database",
            status.getMissingInDatabase(),
            status.getOrphanedInDatabase());
    }
}
