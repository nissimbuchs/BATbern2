package ch.batbern.companyuser.controller;

import ch.batbern.shared.utils.LoggingUtils;
import ch.batbern.companyuser.api.generated.GdprComplianceApi;
import ch.batbern.companyuser.api.generated.ProfilePictureApi;
import ch.batbern.companyuser.api.generated.RoleManagementApi;
import ch.batbern.companyuser.api.generated.UserAccountApi;
import ch.batbern.companyuser.api.generated.UserManagementApi;
import ch.batbern.companyuser.api.generated.UserSearchApi;
import ch.batbern.companyuser.api.generated.UserAdministrationApi;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.AddAdditionalEmailRequest;
import ch.batbern.companyuser.dto.generated.AdditionalEmail;
import ch.batbern.companyuser.dto.generated.AdditionalEmailVerificationCheckResponse;
import ch.batbern.companyuser.dto.generated.AdditionalEmailVerificationConfirmResponse;
import ch.batbern.companyuser.dto.generated.ConfirmAdditionalEmailVerificationRequest;
import ch.batbern.companyuser.dto.generated.CreateUserRequest;
import ch.batbern.companyuser.dto.generated.PaginatedUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateAdditionalEmailRequest;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UpdateUserRolesRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.dto.generated.UserRolesResponse;
import ch.batbern.companyuser.dto.generated.UserSearchResponse;
import ch.batbern.companyuser.dto.generated.UserReconciliationReport;
import ch.batbern.companyuser.dto.generated.UserSyncStatus;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.PrimaryEmailService;
import ch.batbern.companyuser.service.UserSearchService;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.api.SortCriteria;
import ch.batbern.shared.api.SortDirection;
import ch.batbern.shared.api.SortParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for User Management.
 *
 * <p>Phase 7 (ADR-006 / api-consolidation): contract-first — this controller
 * {@code implements} the generated {@code users-api.openapi.yml} interfaces
 * ({@code UserManagementApi}, {@code UserAccountApi}, {@code ProfilePictureApi},
 * {@code RoleManagementApi}, {@code UserSearchApi}, {@code GdprComplianceApi},
 * {@code UserAdministrationApi}), which carry the HTTP method/path mappings and request/response
 * DTO types. The class-level {@code @RequestMapping("/api/v1")} supplies the version prefix the
 * interface paths omit (e.g. interface {@code /users/me} → {@code /api/v1/users/me}).
 *
 * <p>The machine-only operations (provision, profile patch, invitation credentials) left this
 * controller for {@link InternalUserController} (Epic 17 entry 6, ADR-016).
 *
 * <p>Story 4.10: a user's profile and picture are one set, {@code /users/{username}}, for the
 * user themself and for organizers ({@code @userOwnership.isCurrentUser(#username)} next to the
 * role rule); the {@code /users/me} twins are gone. {@code GET /users/me} stays as the self read.
 *
 * <p>Method-level {@code @PreAuthorize} / {@code @Timed} stay on the implementation (the generated
 * interfaces carry neither); behaviour matches the pre-wiring controller exactly.
 *
 * <p>The formerly-undocumented endpoints — service-to-service {@code GET /users/by-company},
 * {@code POST /users/admin/reconcile}, {@code GET /users/admin/sync-status} — are now contract-first
 * too (spec ops added under User Management / User Administration tags).
 *
 * <p>Story 17.11a: profile picture uploads (presign, confirm, import from a URL) run on the upload
 * contract, purpose {@code profile-picture} ({@link UploadsController}); only the removal is here.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserController implements UserManagementApi, UserAccountApi, ProfilePictureApi,
        RoleManagementApi, UserSearchApi, GdprComplianceApi,
        UserAdministrationApi {

    private final UserService userService;
    private final UserSearchService userSearchService;
    private final UserRepository userRepository;
    private final ch.batbern.companyuser.service.UserReconciliationService reconciliationService;
    private final ch.batbern.companyuser.service.RoleService roleService;
    private final PrimaryEmailService primaryEmailService;

    /** Lenient parser for the JSON {@code filter} query param (ADR-013 §3). */
    private static final ObjectMapper LIST_FILTER_MAPPER = new ObjectMapper();

    /** Default autocomplete page size, matching {@code default: 20} in users-api.openapi.yml. */
    private static final int DEFAULT_SEARCH_LIMIT = 20;

    // ------------------------------------------------------------------------
    // UserManagementApi
    // ------------------------------------------------------------------------

    /** AC1: Get current authenticated user (?include=company,preferences,settings,roles). */
    @Override
    @Timed(value = "users.getCurrentUser",
            description = "Time to get current authenticated user",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> getCurrentUser(String include) {
        log.debug("Getting current authenticated user with include: {}", include);

        UserResponse response = (include != null && !include.isEmpty())
                ? userService.getCurrentUser(include)
                : userService.getCurrentUser();

        return ResponseEntity.ok(response);
    }

    /** AC4: Create new user (Organizer/Admin only). Story 2.5.2. */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.createUser",
            description = "Time to create new user (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> createUser(CreateUserRequest createUserRequest) {
        log.info("Creating new user: {}", LoggingUtils.maskEmail(createUserRequest.getEmail()));

        UserResponse response = userService.createUser(createUserRequest);

        return ResponseEntity.status(201).body(response);
    }

    /**
     * AC3: List users (ADR-013 §3 single list-query vocabulary). {@code role}/{@code company}
     * are expressed inside the JSON {@code filter} (e.g. {@code {"role":"ORGANIZER"}});
     * ordering via {@code sort} ({@code -createdAt}). {@code include} is accepted for contract
     * conformance and not applied on the list.
     *
     * <p>Organizer directory: ORGANIZER or ADMIN only. Services list users through
     * {@code GET /internal/users} ({@link InternalUserController}).
     */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Timed(value = "users.listUsers",
            description = "Time to list users (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PaginatedUserResponse> listUsers(
            String filter, String sort, Integer page, Integer limit,
            String include, String search) {
        int pageNumber = page != null ? page : 1;
        int pageSize = limit != null ? limit : 20;

        // Derive the legacy service args from the JSON:API filter/sort vocabulary.
        String role = extractFilterString(filter, "role");
        String company = extractFilterString(filter, "company");
        if (company == null) {
            company = extractFilterString(filter, "companyId");
        }
        // Shared SortParser is the canonical sort vocabulary (see EventSearchService/CompanyQueryService).
        // Only the first key is honoured here (the service whitelists a single sortable field).
        List<SortCriteria> sortCriteria = SortParser.parse(sort);
        String sortBy = sortCriteria.isEmpty() ? null : sortCriteria.get(0).getField();
        String sortDir = (!sortCriteria.isEmpty()
                && sortCriteria.get(0).getDirection() == SortDirection.DESC) ? "desc" : "asc";

        log.debug("UserController listing users: filter={}, search={}, sort={}, page={}, limit={}",
                filter, search, sort, pageNumber, pageSize);

        // Convert 1-based page to 0-based for service layer
        int pageIndex = Math.max(0, pageNumber - 1);

        // Use optimized paginated service method (server-side filter + sort + pagination)
        Page<UserResponse> usersPage = userService.listUsersPaginated(
                role, company, search, filter, pageIndex, pageSize, sortBy, sortDir);

        return ResponseEntity.ok(toPaginatedResponse(usersPage, pageNumber, pageSize));
    }

    /**
     * AC5: Get user by username (?include=company,roles,preferences). Story 1.16.2.
     *
     * <p>ORGANIZER or ADMIN only. A user reads their own record via {@code /users/me}; services
     * use {@code GET /internal/users/{username}} ({@link InternalUserController}).
     */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<UserResponse> getUserByUsername(String username, String include) {
        log.debug("Getting user by username: {} with include: {}", username, include);

        UserResponse response = (include != null && !include.isEmpty())
                ? userService.getUserByUsername(username, include)
                : userService.getUserByUsername(username);

        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------------
    // UserSearchApi
    // ------------------------------------------------------------------------

    /**
     * AC4: Search users with autocomplete and caching ({@code <100ms} P95 with Caffeine cache).
     * Returns the slim {@link UserSearchResponse} projection (id/email/name/company/roles/photo).
     * ORGANIZER or ADMIN only.
     */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Timed(value = "users.searchUsers",
            description = "Time to search users with caching",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<List<UserSearchResponse>> searchUsers(String query, String role, Integer limit) {
        log.debug("Searching users with query: {}, role: {}, limit: {}", query, role, limit);

        Role roleFilter = role != null ? Role.valueOf(role.toUpperCase()) : null;
        int effectiveLimit = limit != null ? limit : DEFAULT_SEARCH_LIMIT;

        // The service clamps and applies the limit in SQL. It used to cap at 20 internally, so
        // this stream `.limit(...)` could only ever narrow further and `limit` above 20 was
        // silently ignored (bug fix 2026-09-09).
        List<UserSearchResponse> results =
                userSearchService.searchUsers(query, roleFilter, effectiveLimit).stream()
                        .map(UserController::toSearchResponse)
                        .toList();

        return ResponseEntity.ok(results);
    }

    // ------------------------------------------------------------------------
    // GdprComplianceApi
    // ------------------------------------------------------------------------

    /** AC11: Delete user (GDPR compliance). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.deleteUser",
            description = "Time to delete user (GDPR compliance)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> deleteUser(String username) {
        log.warn("Deleting user (GDPR): {}", username);

        userService.deleteUser(username);

        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------
    // RoleManagementApi
    // ------------------------------------------------------------------------

    /** AC8: Get user roles. */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.getUserRoles",
            description = "Time to get user roles",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserRolesResponse> getUserRoles(String username) {
        log.info("Getting roles for user: {}", username);

        var roles = roleService.getUserRoles(username);
        var rolesDto = roles.stream()
                .map(role -> UserRolesResponse.RolesEnum.valueOf(role.name()))
                .toList();

        return ResponseEntity.ok(new UserRolesResponse()
                .username(username)
                .roles(rolesDto));
    }

    /** AC8: Update user roles. */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.updateUserRoles",
            description = "Time to update user roles",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserRolesResponse> updateUserRoles(
            String username, UpdateUserRolesRequest updateUserRolesRequest) {
        log.info("Updating roles for user: {}", username);

        // Convert from DTO enum to domain enum
        var domainRoles = updateUserRolesRequest.getRoles().stream()
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

    // ------------------------------------------------------------------------
    // UserAccountApi — additional emails (Story 10.32) + verification (v2)
    // ------------------------------------------------------------------------

    /** Story 10.32 — Register an additional email on the caller's profile. */
    @Override
    @Timed(value = "users.additionalEmails.add",
            description = "Time to add an additional email to the current user",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdditionalEmail> addAdditionalEmail(AddAdditionalEmailRequest addAdditionalEmailRequest) {
        log.info("Adding additional email for current user");
        AdditionalEmail created = userService.addAdditionalEmail(addAdditionalEmailRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * #1244 step 2 — a confirmed additional address becomes the primary (login) address.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> makeAdditionalEmailPrimary(UUID emailId) {
        primaryEmailService.makePrimary(emailId);
        return ResponseEntity.noContent().build();
    }

    /**
     * #1244 — switch the copy of event mails for one of the caller's additional addresses.
     * 404 if not the caller's, 409 {@code NOT_VERIFIED} when switching it on for an unconfirmed one.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdditionalEmail> updateAdditionalEmail(UUID emailId,
            UpdateAdditionalEmailRequest updateAdditionalEmailRequest) {
        return ResponseEntity.ok(userService.updateAdditionalEmail(emailId, updateAdditionalEmailRequest));
    }

    /**
     * Story 10.32 — Remove an additional email from the caller's profile. Keyed by the row id since
     * Story 4.10d (the address never goes in the path); another user's id is 404.
     */
    @Override
    @Timed(value = "users.additionalEmails.delete",
            description = "Time to remove an additional email from the current user",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAdditionalEmail(UUID emailId) {
        log.info("Removing additional email for current user");
        userService.deleteAdditionalEmail(emailId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Additional-email verification (v2) — resend the verification email for one of the caller's
     * own (still unverified) additional emails. 204 on success; 404 if not the caller's; 409
     * ({@code ALREADY_VERIFIED}) if already verified.
     */
    @Override
    @Timed(value = "users.additionalEmails.resendVerification",
            description = "Time to resend an additional-email verification email",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> resendAdditionalEmailVerification(UUID emailId) {
        log.info("Resending additional-email verification for current user");
        userService.resendVerification(emailId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Additional-email verification (v2) — public GET-check of a verification token. Validates the
     * token and returns the masked email + status WITHOUT mutating state. The token IS the
     * credential — no JWT.
     */
    @Override
    @Timed(value = "users.additionalEmails.verifyCheck",
            description = "Time to check an additional-email verification token",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<AdditionalEmailVerificationCheckResponse> checkAdditionalEmailVerification(String token) {
        log.info("Checking additional-email verification token");
        return ResponseEntity.ok(userService.checkVerificationToken(token));
    }

    /**
     * Additional-email verification (v2) — public POST-confirm of a verification token. POST-only
     * so mail-scanner GET prefetches cannot verify. Sets {@code verified_at}; idempotent.
     */
    @Override
    @Timed(value = "users.additionalEmails.verifyConfirm",
            description = "Time to confirm an additional-email verification token",
            percentiles = {0.5, 0.95, 0.99})
    @PreAuthorize("permitAll()")
    public ResponseEntity<AdditionalEmailVerificationConfirmResponse> confirmAdditionalEmailVerification(
            ConfirmAdditionalEmailVerificationRequest confirmAdditionalEmailVerificationRequest) {
        log.info("Confirming additional-email verification token");
        return ResponseEntity.ok(
                userService.confirmVerificationToken(confirmAdditionalEmailVerificationRequest.getToken()));
    }

    // ------------------------------------------------------------------------
    // ProfilePictureApi
    // ------------------------------------------------------------------------

    /** Remove a user's profile picture: the user themself or an organizer (Story 4.10). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER') or @userOwnership.isCurrentUser(#username)")
    @Timed(value = "users.profilePicture.remove",
            description = "Time to remove a profile picture",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> removeProfilePictureForUser(String username) {
        log.info("Removing profile picture for user: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        user.setProfilePictureUrl(null);
        user.setProfilePictureS3Key(null);
        userRepository.save(user);

        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------
    // Profile update and organizer-only profile operations
    // ------------------------------------------------------------------------

    /**
     * Partially update a user's profile: the user themself or an organizer/admin (Story 4.10, one
     * endpoint replacing {@code PATCH /users/me} and {@code PUT /users/{username}}). Consent
     * ({@code termsAccepted}) is self only, enforced in {@link UserService#updateUser}.
     */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN') or @userOwnership.isCurrentUser(#username)")
    @Timed(value = "users.patchUser",
            description = "Time to partially update a user profile",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> patchUser(String username, UpdateUserRequest updateUserRequest) {
        log.info("Patching user {}", username);
        return ResponseEntity.ok(userService.updateUser(username, updateUserRequest));
    }

    /**
     * Story 1.2.5: Manual user reconciliation (Admin only). Triggers manual sync from Cognito to
     * Database. POST /api/v1/users/admin/reconcile.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.admin.reconcile",
            description = "Time to reconcile users (Cognito to DB)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserReconciliationReport> reconcileUsers() {
        log.info("Manual user reconciliation triggered by admin");

        ch.batbern.companyuser.service.UserReconciliationService.ReconciliationReport report =
            reconciliationService.reconcileUsers();

        UserReconciliationReport response = new UserReconciliationReport()
            .orphanedUsersDeactivated(report.getOrphanedUsers())
            .missingUsersCreated(report.getMissingUsers())
            .durationMs(report.getDurationMs())
            .errors(report.getErrors())
            .success(report.getErrors().isEmpty())
            .message(buildReconciliationMessage(report));

        log.info("User reconciliation completed: created={}, deactivated={}, duration={}ms",
            report.getMissingUsers(), report.getOrphanedUsers(), report.getDurationMs());

        return ResponseEntity.ok(response);
    }

    /**
     * Story 1.2.5: Check sync status (Admin only). Compares Cognito and Database.
     * GET /api/v1/users/admin/sync-status.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.admin.syncStatus",
            description = "Time to check sync status",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserSyncStatus> getSyncStatus() {
        log.debug("Checking Cognito-Database sync status");

        ch.batbern.companyuser.service.UserReconciliationService.SyncStatus status =
            reconciliationService.checkSyncStatus();

        UserSyncStatus response = new UserSyncStatus()
            .cognitoUserCount(status.getCognitoUserCount())
            .databaseUserCount(status.getDatabaseUserCount())
            .missingInDatabase(status.getMissingInDatabase())
            .orphanedInDatabase(status.getOrphanedInDatabase())
            .missingCognitoIds(status.getMissingCognitoIds())
            .inSync(status.isInSync())
            .message(buildSyncStatusMessage(status));

        log.debug("Sync status: cognito={}, db={}, missing={}, orphaned={}, inSync={}",
            status.getCognitoUserCount(), status.getDatabaseUserCount(),
            status.getMissingInDatabase(), status.getOrphanedInDatabase(), status.isInSync());

        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /** Build the generated {@link PaginatedUserResponse} from a service page (1-based page number). */
    static PaginatedUserResponse toPaginatedResponse(Page<UserResponse> usersPage, int page, int limit) {
        ch.batbern.shared.api.PaginationMetadata paginationMetadata =
            new ch.batbern.shared.api.PaginationMetadata();
        paginationMetadata.setPage(page);  // 1-based for API
        paginationMetadata.setLimit(limit);
        paginationMetadata.setTotalItems(usersPage.getTotalElements());
        paginationMetadata.setTotalPages(usersPage.getTotalPages());
        paginationMetadata.setHasNext(usersPage.hasNext());
        paginationMetadata.setHasPrev(usersPage.hasPrevious());

        PaginatedUserResponse response = new PaginatedUserResponse();
        response.setData(usersPage.getContent());
        response.setPagination(paginationMetadata);
        return response;
    }

    /** Map the full {@link UserResponse} to the slim search projection. */
    private static UserSearchResponse toSearchResponse(UserResponse u) {
        return new UserSearchResponse()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .companyId(u.getCompanyId())
                .roles(u.getRoles() == null ? null
                        : u.getRoles().stream().map(UserResponse.RolesEnum::getValue).toList())
                .profilePictureUrl(u.getProfilePictureUrl());
    }

    /**
     * Extract a top-level string value (e.g. {@code role}, {@code company}) from the JSON
     * {@code filter} query param. Lenient: a malformed/absent filter yields {@code null}.
     */
    private static String extractFilterString(String filter, String key) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        try {
            JsonNode node = LIST_FILTER_MAPPER.readTree(filter).get(key);
            return (node != null && node.isValueNode()) ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Build human-readable reconciliation message. */
    private String buildReconciliationMessage(
            ch.batbern.companyuser.service.UserReconciliationService.ReconciliationReport report) {
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

    /** Build human-readable sync status message. */
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
