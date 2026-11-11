package ch.batbern.companyuser.controller;

import java.util.List;
import java.util.Map;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.InvitationCredentialsResponse;
import ch.batbern.companyuser.dto.generated.PaginatedUserResponse;
import ch.batbern.companyuser.dto.generated.PatchUserProfileRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserResponse;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.exception.UserValidationException;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.utils.LoggingUtils;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal user API for the other services (EMS, PCS, API gateway), specified in
 * {@code docs/api/internal/cums-internal.openapi.yml}.
 *
 * <p>{@code /internal/users} is reachable only inside the VPC through Service Connect: the API
 * gateway does not route {@code /internal/**} and denies it explicitly. ADR-016 decision 3: the
 * caller forwards the user's JWT when there is one and never has to; these endpoints authorize by
 * operation, never by the user's role (the role check stays on the public endpoint where the user
 * entered, in EMS). The reads answer the same for every caller, so their results are safe to cache
 * per username. The writes record who acted: the forwarded user, else {@code system:<job>}, the
 * job named per operation in the spec. Not part of the public users-api contract.
 *
 * <p>The public {@code /api/v1/users} directory (list, search, single user) is organizer-only;
 * see {@link UserController}. Provision, profile patch and invitation credentials moved here from
 * the public API in Epic 17 entry 6.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private static final int DEFAULT_LIMIT = 100;

    /** The one consumer of provision and invitation credentials: EMS speaker workflow. */
    static final String SPEAKER_WORKFLOW_JOB = "ems-speaker-workflow";
    /** The one consumer of the profile patch: EMS content submission. */
    static final String CONTENT_SUBMISSION_JOB = "ems-content-submission";

    private final UserService userService;
    private final SecurityContextHelper securityContextHelper;

    /** One user, with optional {@code include=company,roles,preferences}. 404 if unknown. */
    @GetMapping("/internal/users/{username}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable String username,
            @RequestParam(required = false) String include) {
        UserResponse response = (include != null && !include.isEmpty())
                ? userService.getUserByUsername(username, include)
                : userService.getUserByUsername(username);
        return ResponseEntity.ok(response);
    }

    /**
     * #1183: the usernames owning an address (primary, or verified additional); {@code []} if none.
     * EMS uses it for the by-email deregistration across a person's profiles. Internal only: it
     * reveals whether an address is known, so it must never be routed publicly.
     */
    @GetMapping("/internal/email-owners")
    public ResponseEntity<List<String>> emailOwners(@RequestParam String email) {
        return ResponseEntity.ok(userService.ownersOfEmail(email));
    }

    /** Users filtered by {@code role} and/or {@code company}, 1-based paging. */
    @GetMapping("/internal/users")
    public ResponseEntity<PaginatedUserResponse> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String company,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        log.debug("Internal user list: role={}, company={}, page={}, limit={}", role, company, page, limit);
        Page<UserResponse> usersPage = userService.listUsersPaginated(
                role, company, null, null, Math.max(0, page - 1), limit);
        return ResponseEntity.ok(UserController.toPaginatedResponse(usersPage, page, limit));
    }

    /**
     * Get-or-create a user by email (AC12, ADR-005). EMS calls it for anonymous event registration
     * and speaker provisioning. A verified additional email resolves to its owner; an unverified
     * one answers 409 {@code EMAIL_LINKED_TO_ACCOUNT}. Internal only: it creates users and returns
     * full records, so it is not part of the public API.
     */
    @PostMapping("/internal/users/get-or-create")
    @Timed(value = "users.getOrCreateUser",
            description = "Time to get or create user (service-to-service)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<GetOrCreateUserResponse> getOrCreateUser(
            @Valid @RequestBody GetOrCreateUserRequest request) {
        log.info("Get-or-create user (internal)");
        return ResponseEntity.ok(userService.getOrCreateUser(request));
    }

    /**
     * Provision a speaker (Story 11.C.2): create the user or find them by e-mail, grant SPEAKER,
     * create the Cognito login silently. Idempotent. Grants SPEAKER only; any other role is 400.
     */
    @PostMapping("/internal/users/provision")
    @Timed(value = "users.provisionUser",
            description = "Time to provision a user with role (Story 11.C.2)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<ProvisionUserResponse> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        // `additionalProperties: false`: the generated DTO absorbs unknown fields via @JsonAnySetter
        rejectUnknownFields("ProvisionUserRequest", request.getAdditionalProperties());
        String actor = securityContextHelper.actorOr(SPEAKER_WORKFLOW_JOB);
        log.info("POST /internal/users/provision: email={}, role={}, actor={}",
                LoggingUtils.maskEmail(request.getEmail()), request.getRole(), actor);
        return ResponseEntity.ok(userService.provisionUserWithRole(request, actor));
    }

    /**
     * Patch {@code bio} and {@code profilePictureUrl} (Story 11.C.2). EMS content submission checks
     * that a speaker submits only for themselves before it calls; this operation does not look at
     * the caller's role.
     */
    @PatchMapping("/internal/users/{username}/profile")
    @Timed(value = "users.patchUserProfile",
            description = "Time to patch user profile fields (Story 11.C.2)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<UserResponse> patchUserProfile(
            @PathVariable String username, @Valid @RequestBody PatchUserProfileRequest request) {
        rejectUnknownFields("PatchUserProfileRequest", request.getAdditionalProperties());
        String actor = securityContextHelper.actorOr(CONTENT_SUBMISSION_JOB);
        log.info("PATCH /internal/users/{}/profile (actor={})", username, actor);
        return ResponseEntity.ok(userService.patchUserProfile(username, request, actor));
    }

    /**
     * Issue (or skip) Cognito temporary credentials at READY to INVITED (Story 11.E.2). Idempotent.
     * The temporary password is in the answer only; it is never stored.
     */
    @PostMapping("/internal/users/{username}/issue-invitation-credentials")
    @Timed(value = "users.issueInvitationCredentials",
            description = "Time to issue invitation credentials (Story 11.E.2)",
            percentiles = {0.5, 0.95, 0.99})
    public ResponseEntity<InvitationCredentialsResponse> issueInvitationCredentials(@PathVariable String username) {
        String actor = securityContextHelper.actorOr(SPEAKER_WORKFLOW_JOB);
        InvitationCredentialsResponse response = userService.issueInvitationCredentials(username, actor);
        log.info("Issued invitation credentials: actor={} target={} action={}",
                actor, username, response.getAction());
        return ResponseEntity.ok(response);
    }

    private static void rejectUnknownFields(String schema, Map<String, Object> unknown) {
        if (unknown != null && !unknown.isEmpty()) {
            throw new UserValidationException("request",
                    "Unknown fields not allowed on " + schema + ": " + unknown.keySet());
        }
    }
}
