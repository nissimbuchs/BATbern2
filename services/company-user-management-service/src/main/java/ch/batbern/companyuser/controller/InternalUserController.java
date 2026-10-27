package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.PaginatedUserResponse;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal user API for the other services (EMS, PCS, API gateway).
 *
 * <p>{@code /internal/users} is NOT JWT-protected. It is reachable only inside the VPC through
 * Service Connect: the API gateway does not route {@code /internal/**} and denies it explicitly,
 * same pattern as PCS {@code /internal/partner-meetings/rsvps}. Services call it without the end
 * user's JWT, so the answer never depends on who triggered the lookup and is safe to cache per
 * username. Not part of the public users-api contract.
 *
 * <p>The public {@code /api/v1/users} directory (list, search, single user) is organizer-only;
 * see {@link UserController}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private static final int DEFAULT_LIMIT = 100;

    private final UserService userService;

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
}
