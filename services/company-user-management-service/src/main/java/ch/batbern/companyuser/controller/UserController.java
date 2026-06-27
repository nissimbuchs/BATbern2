package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.DomainIntegrationApi;
import ch.batbern.companyuser.api.generated.GdprComplianceApi;
import ch.batbern.companyuser.api.generated.ProfilePictureApi;
import ch.batbern.companyuser.api.generated.RoleManagementApi;
import ch.batbern.companyuser.api.generated.UserAccountApi;
import ch.batbern.companyuser.api.generated.UserManagementApi;
import ch.batbern.companyuser.api.generated.UserSearchApi;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.ReconciliationReportDTO;
import ch.batbern.companyuser.dto.SyncStatusDTO;
import ch.batbern.companyuser.dto.generated.AddAdditionalEmailRequest;
import ch.batbern.companyuser.dto.generated.AdditionalEmail;
import ch.batbern.companyuser.dto.generated.AdditionalEmailVerificationCheckResponse;
import ch.batbern.companyuser.dto.generated.AdditionalEmailVerificationConfirmResponse;
import ch.batbern.companyuser.dto.generated.ConfirmAdditionalEmailVerificationRequest;
import ch.batbern.companyuser.dto.generated.CreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.InvitationCredentialsResponse;
import ch.batbern.companyuser.dto.generated.PaginatedUserResponse;
import ch.batbern.companyuser.dto.generated.PatchUserProfileRequest;
import ch.batbern.companyuser.dto.generated.PresignedUploadUrl;
import ch.batbern.companyuser.dto.generated.ProfilePictureUploadConfirmRequest;
import ch.batbern.companyuser.dto.generated.ProfilePictureUploadConfirmResponse;
import ch.batbern.companyuser.dto.generated.ProfilePictureUploadRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UpdateUserRolesRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.dto.generated.UserRolesResponse;
import ch.batbern.companyuser.dto.generated.UserSearchResponse;
import ch.batbern.companyuser.exception.UserValidationException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.service.ImageUrlFetcher;
import ch.batbern.companyuser.service.ProfilePictureService;
import ch.batbern.companyuser.service.UserSearchService;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.api.SortCriteria;
import ch.batbern.shared.api.SortDirection;
import ch.batbern.shared.api.SortParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for User Management.
 *
 * <p>Phase 7 (ADR-006 / api-consolidation): contract-first — this controller
 * {@code implements} the generated {@code users-api.openapi.yml} interfaces
 * ({@code UserManagementApi}, {@code UserAccountApi}, {@code ProfilePictureApi},
 * {@code RoleManagementApi}, {@code UserSearchApi}, {@code DomainIntegrationApi},
 * {@code GdprComplianceApi}), which carry the HTTP method/path mappings and request/response
 * DTO types. The class-level {@code @RequestMapping("/api/v1")} supplies the version prefix the
 * interface paths omit (e.g. interface {@code /users/me} → {@code /api/v1/users/me}).
 *
 * <p>Method-level {@code @PreAuthorize} / {@code @Timed} stay on the implementation (the generated
 * interfaces carry neither); behaviour matches the pre-wiring controller exactly.
 *
 * <p>Five endpoints have no generated interface counterpart (no {@code users-api} operation) and
 * remain hand-rolled below with explicit {@code @…Mapping}: the service-to-service
 * {@code GET /users/by-company}, the admin {@code PUT /users/{username}},
 * {@code POST /users/{username}/profile-picture/upload-from-url},
 * {@code POST /users/admin/reconcile}, and {@code GET /users/admin/sync-status}. They are flagged
 * for a spec addition as a follow-up (Phase 2-style).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserController implements UserManagementApi, UserAccountApi, ProfilePictureApi,
        RoleManagementApi, UserSearchApi, DomainIntegrationApi, GdprComplianceApi {

    private final UserService userService;
    private final UserSearchService userSearchService;
    private final ProfilePictureService profilePictureService;
    private final SecurityContextHelper securityContextHelper;
    private final UserRepository userRepository;
    private final ch.batbern.companyuser.service.UserReconciliationService reconciliationService;
    private final ch.batbern.companyuser.service.RoleService roleService;

    /** Lenient parser for the JSON {@code filter} query param (ADR-013 §3). */
    private static final ObjectMapper LIST_FILTER_MAPPER = new ObjectMapper();

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

    /** AC2: Partially update current user profile. */
    @Override
    @Timed(value = "users.patchCurrentUser",
            description = "Time to partially update current user profile",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> patchCurrentUser(UpdateUserRequest updateUserRequest) {
        log.info("Patching current user profile");

        UserResponse response = userService.updateCurrentUser(updateUserRequest);

        return ResponseEntity.ok(response);
    }

    /** AC4: Create new user (Organizer/Admin only). Story 2.5.2. */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER')")
    @Timed(value = "users.createUser",
            description = "Time to create new user (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> createUser(CreateUserRequest createUserRequest) {
        log.info("Creating new user: {}", createUserRequest.getEmail());

        UserResponse response = userService.createUser(createUserRequest);

        return ResponseEntity.status(201).body(response);
    }

    /**
     * AC3: List users (ADR-013 §3 single list-query vocabulary). {@code role}/{@code company}
     * are expressed inside the JSON {@code filter} (e.g. {@code {"role":"ORGANIZER"}});
     * ordering via {@code sort} ({@code -createdAt}). {@code fields}/{@code include} are accepted
     * for contract conformance; sparse-fieldset on the list is not applied server-side.
     *
     * <p>No {@code @PreAuthorize}: Story 10.26 — the Lambda email forwarder and internal service
     * clients call {@code GET /api/v1/users?filter={"role":…}} without auth (routes via NAT GW).
     * Security is enforced at the filter chain ({@code SecurityConfig.permitAll}).
     */
    @Override
    @Timed(value = "users.listUsers",
            description = "Time to list users (admin/organizer)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PaginatedUserResponse> listUsers(
            String filter, String sort, Integer page, Integer limit,
            String fields, String include, String search) {
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

    /** AC5: Get user by username (?include=company,roles,preferences). Story 1.16.2. */
    @Override
    public ResponseEntity<UserResponse> getUserByUsername(String username, String include) {
        log.debug("Getting user by username: {} with include: {}", username, include);

        UserResponse response = (include != null && !include.isEmpty())
                ? userService.getUserByUsername(username, include)
                : userService.getUserByUsername(username);

        return ResponseEntity.ok(response);
    }

    /**
     * Story 11.C.2 (AR14): Patch user profile fields (bio, profilePictureUrl).
     *
     * <p>Authorization: ORGANIZER, ADMIN, or SPEAKER. SPEAKERS may patch only their own profile
     * (enforced below; throws {@link AccessDeniedException} otherwise).
     */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SPEAKER')")
    @Timed(value = "users.patchUserProfile",
            description = "Time to patch user profile fields (Story 11.C.2)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> patchUserProfile(
            String username, PatchUserProfileRequest patchUserProfileRequest) {
        // P2 (review patch): enforce `additionalProperties: false` at the controller level
        // (the generated DTO silently absorbs unknown fields via `@JsonAnySetter`).
        if (patchUserProfileRequest.getAdditionalProperties() != null
                && !patchUserProfileRequest.getAdditionalProperties().isEmpty()) {
            throw new UserValidationException(
                    "request",
                    "Unknown fields not allowed on PatchUserProfileRequest: "
                            + patchUserProfileRequest.getAdditionalProperties().keySet());
        }
        log.info("PATCH /api/v1/users/{}/profile", username);

        // Method-level role-scope enforcement: a SPEAKER that is not also ORGANIZER/ADMIN
        // may patch only their own profile. The @PreAuthorize already restricts principals.
        if (!securityContextHelper.hasRole("ORGANIZER") && !securityContextHelper.hasRole("ADMIN")) {
            String currentUsername = securityContextHelper.getCurrentUsername();
            // P3 (review patch): case-insensitive username comparison. The JWT issues canonical-case
            // usernames; URL path-segments can be CDN-lowercased or mistyped.
            if (currentUsername == null || !currentUsername.equalsIgnoreCase(username)) {
                log.warn("Cross-speaker profile patch rejected: caller={}, target={}",
                        currentUsername, username);
                throw new AccessDeniedException("SPEAKER may only patch their own profile");
            }
        }

        UserResponse response = userService.patchUserProfile(username, patchUserProfileRequest);

        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------------
    // UserSearchApi
    // ------------------------------------------------------------------------

    /**
     * AC4: Search users with autocomplete and caching ({@code <100ms} P95 with Caffeine cache).
     * Returns the slim {@link UserSearchResponse} projection (id/email/name/company/roles/photo).
     */
    @Override
    @Timed(value = "users.searchUsers",
            description = "Time to search users with caching",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<List<UserSearchResponse>> searchUsers(String query, String role, Integer limit) {
        log.debug("Searching users with query: {}, role: {}, limit: {}", query, role, limit);

        Role roleFilter = role != null ? Role.valueOf(role.toUpperCase()) : null;
        int effectiveLimit = limit != null ? limit : 20;

        List<UserSearchResponse> results = userSearchService.searchUsers(query, roleFilter).stream()
                .limit(effectiveLimit)
                .map(UserController::toSearchResponse)
                .toList();

        return ResponseEntity.ok(results);
    }

    // ------------------------------------------------------------------------
    // DomainIntegrationApi
    // ------------------------------------------------------------------------

    /**
     * AC12: Get-or-create user (for domain service integration). Story 4.1.5: public for anonymous
     * event registration (ADR-005) — SecurityConfig allows public access for this endpoint.
     */
    @Override
    @Timed(value = "users.getOrCreateUser",
            description = "Time to get or create user (service-to-service)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<GetOrCreateUserResponse> getOrCreateUser(GetOrCreateUserRequest getOrCreateUserRequest) {
        log.info("Get-or-create user for email: {}", getOrCreateUserRequest.getEmail());

        GetOrCreateUserResponse response = userService.getOrCreateUser(getOrCreateUserRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * Story 11.E.2 (AR15, FR9): Issue (or skip) Cognito temp credentials at invitation time.
     * Service-to-service endpoint called by {@code SpeakerWorkflowService.runInvitedHook} at
     * READY → INVITED. Idempotent.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.issueInvitationCredentials",
            description = "Time to issue invitation credentials (Story 11.E.2)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<InvitationCredentialsResponse> issueInvitationCredentials(String username) {
        // Story 11.E.2 review patch (D3): narrow to hasRole('ORGANIZER') and log the
        // actor → target → action triple for post-incident review.
        String actor = securityContextHelper.getCurrentUsername();
        log.info("POST /api/v1/users/{}/issue-invitation-credentials (actor={})",
                username, actor != null ? actor : "<unknown>");
        InvitationCredentialsResponse response = userService.issueInvitationCredentials(username);
        log.info("Issued invitation credentials: actor={} target={} action={}",
                actor != null ? actor : "<unknown>", username, response.getAction());
        return ResponseEntity.ok(response);
    }

    /**
     * Story 11.C.2 (AR13): Provision a User with a role. Service-to-service endpoint called by
     * {@code SpeakerWorkflowService.transition()} at the CONTACTED → READY hook. Idempotent.
     */
    @Override
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Timed(value = "users.provisionUser",
            description = "Time to provision a user with role (Story 11.C.2)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<ProvisionUserResponse> provisionUserWithRole(ProvisionUserRequest provisionUserRequest) {
        // P2 (review patch): enforce `additionalProperties: false` at the controller level.
        if (provisionUserRequest.getAdditionalProperties() != null
                && !provisionUserRequest.getAdditionalProperties().isEmpty()) {
            throw new UserValidationException(
                    "request",
                    "Unknown fields not allowed on ProvisionUserRequest: "
                            + provisionUserRequest.getAdditionalProperties().keySet());
        }
        log.info("POST /api/v1/users/provision — email: {}, role: {}",
                provisionUserRequest.getEmail(), provisionUserRequest.getRole());

        ProvisionUserResponse response = userService.provisionUserWithRole(provisionUserRequest);

        return ResponseEntity.ok(response);
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
    public ResponseEntity<AdditionalEmail> addAdditionalEmail(AddAdditionalEmailRequest addAdditionalEmailRequest) {
        log.info("Adding additional email for current user");
        AdditionalEmail created = userService.addAdditionalEmail(addAdditionalEmailRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Story 10.32 — Remove an additional email from the caller's profile. Under Spring Boot 3's
     * {@code PathPatternParser} the {@code {email}} segment captures the full address (incl. the
     * trailing {@code .com}) without the legacy {@code :.+} regex. Frontend callers MUST
     * {@code encodeURIComponent} the email — see {@code userAccountApi.ts}.
     */
    @Override
    @Timed(value = "users.additionalEmails.delete",
            description = "Time to remove an additional email from the current user",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> deleteAdditionalEmail(String email) {
        log.info("Removing additional email for current user");
        userService.deleteAdditionalEmail(email);
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
    public ResponseEntity<Void> resendAdditionalEmailVerification(String email) {
        log.info("Resending additional-email verification for current user");
        userService.resendVerification(email);
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
    public ResponseEntity<AdditionalEmailVerificationConfirmResponse> confirmAdditionalEmailVerification(
            ConfirmAdditionalEmailVerificationRequest confirmAdditionalEmailVerificationRequest) {
        log.info("Confirming additional-email verification token");
        return ResponseEntity.ok(
                userService.confirmVerificationToken(confirmAdditionalEmailVerificationRequest.getToken()));
    }

    // ------------------------------------------------------------------------
    // ProfilePictureApi
    // ------------------------------------------------------------------------

    /** AC10: Request presigned URL for the caller's own profile picture upload. */
    @Override
    @Timed(value = "users.profilePicture.requestPresignedUrl",
            description = "Time to generate presigned URL for profile picture",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PresignedUploadUrl> requestProfilePictureUploadUrl(
            ProfilePictureUploadRequest profilePictureUploadRequest) {
        log.info("Requesting presigned URL for profile picture upload: {}",
                profilePictureUploadRequest.getFileName());

        String currentUsername = securityContextHelper.getCurrentUsername();
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(currentUsername));

        PresignedUploadUrl response = profilePictureService.generateProfilePictureUploadUrl(
            user.getId(),
            user.getUsername(),
            profilePictureUploadRequest.getFileName(),
            profilePictureUploadRequest.getFileSize());

        return ResponseEntity.ok(response);
    }

    /** AC10: Confirm the caller's own profile picture upload completion. */
    @Override
    public ResponseEntity<ProfilePictureUploadConfirmResponse> confirmProfilePictureUpload(
            ProfilePictureUploadConfirmRequest profilePictureUploadConfirmRequest) {
        log.info("Confirming profile picture upload: fileId={}",
                profilePictureUploadConfirmRequest.getFileId());

        String currentUsername = securityContextHelper.getCurrentUsername();
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(currentUsername));

        profilePictureService.confirmProfilePictureUpload(
            user.getId(),
            user.getUsername(),
            profilePictureUploadConfirmRequest.getFileId(),
            profilePictureUploadConfirmRequest.getFileExtension());

        UserResponse updatedUser = userService.getCurrentUser();

        return ResponseEntity.ok(toConfirmResponse(updatedUser.getProfilePictureUrl()));
    }

    /**
     * AC13: Remove the current user's own profile picture. The literal {@code /me/picture} mapping
     * takes precedence over {@code /{username}/picture}, so a self-removal no longer falls through
     * to the admin handler with a literal {@code username="me"}.
     */
    @Override
    @Timed(value = "users.profilePicture.remove",
            description = "Time to remove own profile picture",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> removeOwnProfilePicture() {
        String currentUsername = securityContextHelper.getCurrentUsername();
        log.info("Removing own profile picture for user: {}", currentUsername);

        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(currentUsername));

        user.setProfilePictureUrl(null);
        user.setProfilePictureS3Key(null);
        userRepository.save(user);

        return ResponseEntity.noContent().build();
    }

    /** Admin: Request presigned URL for a specific user's profile picture upload. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.requestPresignedUrl",
            description = "Time to generate presigned URL for user profile picture (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<PresignedUploadUrl> requestProfilePictureUploadUrlForUser(
            String username, ProfilePictureUploadRequest profilePictureUploadRequest) {
        log.info("Admin requesting presigned URL for profile picture upload for user: {}, file: {}",
                username, profilePictureUploadRequest.getFileName());

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        PresignedUploadUrl response = profilePictureService.generateProfilePictureUploadUrl(
            user.getId(),
            user.getUsername(),
            profilePictureUploadRequest.getFileName(),
            profilePictureUploadRequest.getFileSize());

        return ResponseEntity.ok(response);
    }

    /** Admin: Confirm a specific user's profile picture upload completion. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.confirm",
            description = "Time to confirm profile picture upload for user (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<ProfilePictureUploadConfirmResponse> confirmProfilePictureUploadForUser(
            String username, ProfilePictureUploadConfirmRequest profilePictureUploadConfirmRequest) {
        log.info("Admin confirming profile picture upload for user: {}, fileId={}",
                username, profilePictureUploadConfirmRequest.getFileId());

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        profilePictureService.confirmProfilePictureUpload(
            user.getId(),
            user.getUsername(),
            profilePictureUploadConfirmRequest.getFileId(),
            profilePictureUploadConfirmRequest.getFileExtension());

        UserResponse updatedUser = userService.getUserByUsername(username);

        return ResponseEntity.ok(toConfirmResponse(updatedUser.getProfilePictureUrl()));
    }

    /** Admin: Remove a specific user's profile picture. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.remove",
            description = "Time to remove profile picture for user (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Void> removeProfilePictureForUser(String username) {
        log.info("Admin removing profile picture for user: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ch.batbern.companyuser.exception.UserNotFoundException(username));

        user.setProfilePictureUrl(null);
        user.setProfilePictureS3Key(null);
        userRepository.save(user);

        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------
    // Undocumented endpoints (no users-api operation) — hand-rolled; spec follow-up.
    // ------------------------------------------------------------------------

    /**
     * Service-to-service: list users by company and role. VPC-internal only — authorization
     * enforced at the filter chain ({@code VpcInternalAuthorizationManager}).
     * GET /api/v1/users/by-company?company={companyName}&role={role}
     */
    @GetMapping("/users/by-company")
    @Timed(value = "users.listUsersByCompany",
            description = "Time to list users by company (service-to-service)")
    public ResponseEntity<PaginatedUserResponse> listUsersByCompany(
            @RequestParam String company,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        log.debug("Service-to-service: listing users by company={}, role={}", company, role);

        int pageIndex = Math.max(0, page - 1);
        Page<UserResponse> usersPage = userService.listUsersPaginated(role, company, null, null, pageIndex, limit);

        return ResponseEntity.ok(toPaginatedResponse(usersPage, page, limit));
    }

    /**
     * Update user profile by username (Organizer/Admin only). Allows organizers/admins to update
     * any user's profile. PUT /api/v1/users/{username}.
     */
    @PutMapping("/users/{username}")
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
     * Admin: Upload profile picture from URL for a specific user. Fetches an image from a URL and
     * uploads it directly to S3, bypassing the frontend (avoids binary corruption). Used for batch
     * imports of speaker portraits. POST /api/v1/users/{username}/profile-picture/upload-from-url.
     */
    @PostMapping("/users/{username}/profile-picture/upload-from-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed(value = "users.profilePicture.admin.uploadFromUrl",
            description = "Time to upload profile picture from URL for user (admin)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<Map<String, String>> uploadProfilePictureFromUrl(
            @PathVariable String username,
            @RequestBody Map<String, String> requestBody) {

        String url = requestBody.get("url");
        String suggestedFilename = requestBody.getOrDefault("filename", "profile");

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Admin uploading profile picture from URL for user: {}, url: {}", username, url);

        try {
            // Shared fetch + image validation pipeline (12.12 review, finding #7); 5MB cap.
            ImageUrlFetcher.FetchedImage image = ImageUrlFetcher.fetch(url, 5 * 1024 * 1024);

            String filename = suggestedFilename + "." + image.extension();

            String profilePictureUrl = profilePictureService.uploadProfilePictureDirectly(
                    username, image.body(), filename, image.contentType());

            log.info("Successfully uploaded profile picture for user: {}, URL: {}", username, profilePictureUrl);

            return ResponseEntity.ok(Map.of("profilePictureUrl", profilePictureUrl));

        } catch (ImageUrlFetcher.ImageFetchException e) {
            log.error("Error fetching profile picture from URL for user: {}: {}", username, e.getMessage());
            return switch (e.getReason()) {
                case HTTP_STATUS -> ResponseEntity.status(e.getStatusCode()).build();
                case NOT_AN_IMAGE -> ResponseEntity.badRequest().build();
                case TOO_LARGE -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                case IO -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            };
        }
    }

    /**
     * Story 1.2.5: Manual user reconciliation (Admin only). Triggers manual sync from Cognito to
     * Database. POST /api/v1/users/admin/reconcile.
     */
    @PostMapping("/users/admin/reconcile")
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
     * Story 1.2.5: Check sync status (Admin only). Compares Cognito and Database.
     * GET /api/v1/users/admin/sync-status.
     */
    @GetMapping("/users/admin/sync-status")
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

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    /** Build the generated {@link PaginatedUserResponse} from a service page (1-based page number). */
    private static PaginatedUserResponse toPaginatedResponse(Page<UserResponse> usersPage, int page, int limit) {
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

    /** Build the generated confirm response from a (possibly null) profile-picture URL. */
    private static ProfilePictureUploadConfirmResponse toConfirmResponse(URI profilePictureUrl) {
        return new ProfilePictureUploadConfirmResponse().profilePictureUrl(profilePictureUrl);
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
