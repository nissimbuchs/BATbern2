package ch.batbern.companyuser.service;

import java.util.LinkedHashSet;
import java.util.List;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.dto.generated.AddAdditionalEmailRequest;
import ch.batbern.companyuser.dto.generated.AdditionalEmail;
import ch.batbern.companyuser.dto.generated.AdditionalEmailVerificationCheckResponse;
import ch.batbern.companyuser.dto.generated.AdditionalEmailVerificationConfirmResponse;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserResponse;
import ch.batbern.companyuser.dto.generated.InvitationCredentialsResponse;
import ch.batbern.companyuser.dto.generated.PatchUserProfileRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserRequest;
import ch.batbern.companyuser.dto.generated.ProvisionUserResponse;
import ch.batbern.companyuser.dto.generated.UpdateAdditionalEmailRequest;
import ch.batbern.companyuser.dto.generated.UpdateUserRequest;
import ch.batbern.companyuser.dto.generated.UserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import ch.batbern.companyuser.events.UserCreatedEvent;
import ch.batbern.companyuser.events.UserDeletedEvent;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.companyuser.exception.AdditionalEmailAlreadyVerifiedException;
import ch.batbern.companyuser.exception.AdditionalEmailDuplicateException;
import ch.batbern.companyuser.exception.AdditionalEmailLimitReachedException;
import ch.batbern.companyuser.exception.AdditionalEmailNotFoundException;
import ch.batbern.companyuser.exception.AdditionalEmailNotVerifiedException;
import ch.batbern.companyuser.exception.EmailLinkedToAccountException;
import ch.batbern.companyuser.exception.UnprocessableInvitationStateException;
import ch.batbern.companyuser.exception.VerificationTokenExpiredException;
import ch.batbern.companyuser.exception.VerificationTokenInvalidException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.exception.UserValidationException;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.specification.UserSpecification;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.security.UserOwnership;
import ch.batbern.shared.api.DeterministicPaging;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.service.SlugGenerationService;
import ch.batbern.shared.utils.LoggingUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.annotation.Counted;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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

    /**
     * The system actor of {@code POST /internal/users/get-or-create} without a forwarded JWT (EMS
     * anonymous registration, speaker invitation from a job): {@code system:ems-get-or-create}.
     */
    public static final String GET_OR_CREATE_JOB = "ems-get-or-create";

    private final UserRepository userRepository;
    private final UserAdditionalEmailRepository additionalEmailRepository;
    private final CognitoIntegrationService cognitoService;
    private final DomainEventPublisher eventPublisher;
    private final UserSearchService searchService;
    private final SecurityContextHelper securityContext;
    private final UserOwnership userOwnership;
    private final SlugGenerationService slugService;
    private final UserResponseMapper responseMapper;
    private final CompanyService companyService;
    private final RoleService roleService;
    // Story 11.E.2: throwaway temp passwords at READY, fresh temp passwords at INVITED.
    private final PasswordGenerator passwordGenerator;
    // Additional-email verification (v2): stateless token + verification email.
    private final AdditionalEmailVerificationTokenService verificationTokenService;
    private final AdditionalEmailVerificationEmailService verificationEmailService;

    /**
     * Story 10.32: per-user cap on additional emails (Resolved Decision #3).
     * Configurable; default 5.
     */
    @Value("${batbern.user.additional-emails.max:5}")
    private int maxAdditionalEmailsPerUser;

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
     * AC14: Resource expansion (?include=company,settings)
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
     * Partially update a user's profile: {@code PATCH /users/{username}} (Story 4.10, one endpoint
     * for self-service and organizers; the controller's {@code @PreAuthorize} admits the user
     * themself or ORGANIZER/ADMIN). Only the fields present change.
     *
     * <p>{@code termsAccepted} is the user's own consent (Story 12.11, write-once): accepted only on
     * the caller's own profile, for anyone else it is 403. The {@link UserUpdatedEvent} records the
     * caller's username as the actor (it used to be the Cognito subject for organizer edits).
     *
     * @param username username of the user to update (ADR-003)
     * @param request  the fields to change
     * @return the updated profile
     */
    public UserResponse updateUser(String username, UpdateUserRequest request) {
        boolean self = userOwnership.isCurrentUser(username);
        if (request.getTermsAccepted() != null && !self) {
            throw new AccessDeniedException("Consent can only be given by the user themself");
        }
        log.info("Updating user {} ({})", username, self ? "self" : "organizer/admin");

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

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
        // Story 12.11 (AC4): WRITE-ONCE ToS/Privacy consent. Stamp the server clock on the
        // FIRST acceptance only; a repeated `true` is a no-op (original timestamp preserved),
        // and `false`/absent NEVER changes or revokes recorded consent.
        if (Boolean.TRUE.equals(request.getTermsAccepted()) && user.getTermsAcceptedAt() == null) {
            user.setTermsAcceptedAt(java.time.Instant.now());
        }

        // Flush so that @PreUpdate has set updatedAt before the response is built in this transaction.
        User updatedUser = userRepository.saveAndFlush(user);

        // DB is the source of truth for user data; Cognito is only used for authentication.
        searchService.invalidateCache();

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

        // The actor is a username (ADR-003): the user themself, or the organizer/admin who edited.
        String updatedBy = self ? updatedUser.getUsername() : securityContext.getCurrentUsername();

        UserUpdatedEvent event = new UserUpdatedEvent(
            updatedUser.getUsername(),  // aggregateId = username
            updatedFields,
            null,  // previousValues: not tracked
            updatedBy
        );
        eventPublisher.publish(event);

        log.info("User {} updated successfully by {}", updatedUser.getUsername(), updatedBy);
        return responseMapper.mapToResponse(updatedUser);
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

        // Note: ALLOWED_USER_SORT_FIELDS is an immutable Set.of(...) which throws NPE on contains(null),
        // so the null check must come first (sortBy is null when the `sort` query param is absent).
        String safeSortBy = (sortBy != null && ALLOWED_USER_SORT_FIELDS.contains(sortBy))
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

        // id breaks ties (#1131): names and companies repeat, and an OFFSET page over a tie is not stable
        Pageable pageable = DeterministicPaging.pageRequest(page, limit, sort);
        // Every filter in SQL, so the page, totalItems and the filters agree (#1153).
        Role role = roleFilter != null ? parseRole(roleFilter) : null;
        Specification<User> spec = Specification.allOf(
                UserSpecification.matchesSearch(search),
                UserSpecification.hasRole(role),
                UserSpecification.inCompany(companyFilter),
                UserSpecification.isActive(parseActiveFilter(jsonFilter)));
        Page<User> filteredPage = userRepository.findAll(spec, pageable);

        // Map to response DTOs
        return filteredPage.map(responseMapper::mapToResponse);
    }

    /**
     * The {@code active} flag of the JSON filter, or null when it does not set one.
     * The frontend sends URL-encoded JSON.
     */
    private Boolean parseActiveFilter(String jsonFilter) {
        if (jsonFilter == null || jsonFilter.isBlank()) {
            return null;
        }
        String decodedFilter = jsonFilter;
        try {
            decodedFilter = java.net.URLDecoder.decode(jsonFilter, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.debug("Filter does not need URL decoding, using as-is: {}", jsonFilter);
        }
        if (decodedFilter.contains("\"active\":true") || decodedFilter.contains("\"active\": true")) {
            return Boolean.TRUE;
        }
        if (decodedFilter.contains("\"active\":false") || decodedFilter.contains("\"active\": false")) {
            return Boolean.FALSE;
        }
        return null;
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
     * AC14: Resource expansion (?include=company,settings)
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

        // Deletes the CUMS row only: no other service consumes UserDeletedEvent, and there is no
        // last-organizer guard (#1120).

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
     * #1183: the username that owns {@code email}: the profile whose primary address it is, or the
     * profile that holds it as a VERIFIED additional address. An unverified additional address is a
     * claim and owns nothing. A DB trigger keeps an address from being both, so the list has at most one
     * entry; it is a list so the contract survives a future merge of profiles. Empty when nobody owns it.
     */
    @Transactional(readOnly = true)
    public List<String> ownersOfEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        String address = email.trim();
        LinkedHashSet<String> owners = new LinkedHashSet<>();
        userRepository.findByEmailIgnoreCase(address).ifPresent(user -> owners.add(user.getUsername()));
        additionalEmailRepository.findVerifiedByEmailIgnoreCase(address)
                .ifPresent(extra -> owners.add(extra.getUser().getUsername()));
        return List.copyOf(owners);
    }

    /**
     * Get-or-create pattern for domain services.
     *
     * Email is the only identity key. If the email exists, return the user; if
     * not, create a new account with a deduplicated username (handled by
     * {@link SlugGenerationService#ensureUniqueUsername}).
     *
     * An email that is another user's VERIFIED additional email resolves to that
     * user (same rule as the JIT duplicate guard). An UNVERIFIED additional email
     * cannot be attributed and cannot become a new primary email (DB trigger
     * {@code enforce_primary_email_not_other_additional}), so creating is refused
     * with {@link EmailLinkedToAccountException} (409) instead of failing with a 500.
     *
     * History: a former name-based fallback existed for the historical batch
     * import flow (Story 3.2). It was removed on 2026-05-18 because it caused
     * cross-account email leakage when a person re-registered after a job
     * change (the old account's stale `@oldemployer.ch` email was used for the
     * new registration's confirmation, leading to SES bounces). The batch
     * import path is no longer in active use. See
     * docs/plans/user-identity-collision-fix-plan.md.
     */
    public GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request) {
        // #1184: no name next to the masked address, it would undo the masking
        log.info("Get-or-create user for email: {}, company: {}",
                LoggingUtils.maskEmail(request.getEmail()), request.getCompanyId());

        Optional<User> userByEmail = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .or(() -> additionalEmailRepository.findVerifiedByEmailIgnoreCase(request.getEmail())
                        .map(UserAdditionalEmail::getUser));
        if (userByEmail.isPresent()) {
            User existingUser = userByEmail.get();
            log.debug("User found by email: {}", existingUser.getUsername());
            // BATbern59 badge fix: a returning attendee may now work somewhere else.
            // If they supply a company that resolves to a different slug than the one on
            // file, refresh the profile so the next badge/export is correct, and flag it
            // so the registration UI can let them know we updated their company.
            boolean companyUpdated = refreshCompanyIfChanged(existingUser, request.getCompanyId());
            return new GetOrCreateUserResponse()
                    .username(existingUser.getUsername())
                    .created(false)
                    .companyUpdated(companyUpdated)
                    .user(responseMapper.mapToResponse(existingUser));
        }

        if (request.getCreateIfMissing() != null && request.getCreateIfMissing()) {
            if (additionalEmailRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new EmailLinkedToAccountException(request.getEmail());
            }
            log.info("Creating new user: {}", LoggingUtils.maskEmail(request.getEmail()));
            User newUser = createNewUser(request, securityContext.actorOr(GET_OR_CREATE_JOB));
            return new GetOrCreateUserResponse()
                    .username(newUser.getUsername())
                    .created(true)
                    .companyUpdated(false)
                    .cognitoUserId(newUser.getCognitoUserId())
                    .user(responseMapper.mapToResponse(newUser));
        }
        // #1183: never the address in a 404 (it reaches the log and the caller)
        throw new UserNotFoundException("No user with this address", true);
    }

    /**
     * Refresh an existing user's company from a value supplied during get-or-create
     * (e.g. an event registration). Treats {@code suppliedCompany} as a display name,
     * resolves it to a company slug (get-or-create), and only writes when that slug
     * differs from the one already stored. A blank value is a no-op — registration
     * must never wipe an existing company.
     *
     * @return {@code true} if the profile company was changed and persisted.
     */
    private boolean refreshCompanyIfChanged(User existingUser, String suppliedCompany) {
        if (suppliedCompany == null || suppliedCompany.trim().isEmpty()) {
            return false;
        }
        String newSlug = companyService.getOrCreateCompany(suppliedCompany).getName();
        if (newSlug == null || newSlug.equals(existingUser.getCompanyId())) {
            return false;
        }
        String previousSlug = existingUser.getCompanyId();
        existingUser.setCompanyId(newSlug);
        // Flush: the get-or-create response carries this user's new updatedAt (@PreUpdate).
        User saved = userRepository.saveAndFlush(existingUser);
        searchService.invalidateCache();

        java.util.Map<String, Object> updatedFields = new java.util.HashMap<>();
        updatedFields.put("companyId", newSlug);
        eventPublisher.publish(new UserUpdatedEvent(
                saved.getUsername(),  // aggregateId = username
                updatedFields,
                null,                 // previousValues - not tracked here
                saved.getUsername()   // userId = self-service registration update
        ));
        log.info("Refreshed company for returning user {}: {} -> {} (supplied: '{}')",
                saved.getUsername(), previousSlug, newSlug, suppliedCompany);
        return true;
    }

    /**
     * Provision a User with a role (idempotent).
     *
     * <p>Story 11.C.2 (AR13). Used by EMS {@code SpeakerWorkflowService.transition} at the
     * CONTACTED → READY hook, through {@code POST /internal/users/provision} (Epic 17 entry 6).
     * The operation grants SPEAKER only: it authorizes by operation, not by the caller's role
     * (ADR-016 decision 3), so any other role answers 400.
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
     * <p>Story 11.E.2 (Resolved Q#1 Variant B): on the new-user branch, calls Cognito
     * {@code AdminCreateUser} silently ({@code MessageAction=SUPPRESS}) with a throwaway
     * temporary password. The Cognito user lands in {@code FORCE_CHANGE_PASSWORD} state.
     * The throwaway password is immediately discarded — the speaker's real temporary
     * password is issued at READY → INVITED via the sibling
     * {@link #issueInvitationCredentials} endpoint. The {@code temporaryPassword} field was
     * removed from {@link ProvisionUserResponse} in 11.E.2.
     *
     * <p>If Cognito {@code AdminCreateUser} fails (any error other than
     * {@code UsernameExistsException}), the surrounding {@code @Transactional} aborts and
     * the User + role-assignment rows are rolled back. EMS receives HTTP 502 and the
     * workflow transition aborts at CONTACTED → READY (no partial state).
     *
     * @param request username (optional), email (required), firstName, lastName, role (required)
     * @param actor   who acted: the forwarded user's username, else {@code system:<job>}; recorded
     *                as {@code createdBy} of a new user
     * @return canonical username + {@code created} flag
     * @throws UserValidationException if email or role is missing/invalid, or the role is not SPEAKER
     */
    @Counted(value = "users.provisioning", description = "Count of user provisioning calls (Story 11.C.2)")
    public ProvisionUserResponse provisionUserWithRole(ProvisionUserRequest request, String actor) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new UserValidationException("email", "Email is required for user provisioning");
        }
        if (request.getRole() == null) {
            throw new UserValidationException("role", "Role is required for user provisioning");
        }
        Role roleToGrant = parseRole(request.getRole().getValue());

        // The operation grants SPEAKER only (Epic 17 entry 6): /internal authorizes by operation,
        // never by the caller's role (ADR-016), so there is no ADMIN branch any more.
        if (roleToGrant != Role.SPEAKER) {
            throw new UserValidationException(
                    "role",
                    "/internal/users/provision grants the SPEAKER role only (got " + roleToGrant + ")");
        }

        // P1 (review patch): normalize email to lowercase for both lookup AND persist so
        // "Jane@x.com" and "jane@x.com" resolve to the same User row. Idempotency claim now holds.
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        log.info("Provisioning user (Story 11.C.2): email={}, role={}, actor={}",
                LoggingUtils.maskEmail(normalizedEmail), roleToGrant, actor);

        // 1. Look up existing user by email (case-insensitive). Matches the getOrCreateUser contract.
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // Idempotent role grant — RoleService.addRole is a no-op if the role is already held.
            roleService.addRole(user.getUsername(), roleToGrant);
            log.info("Provisioning: existing user {} has role {} (created=false)",
                    user.getUsername(), roleToGrant);
            // Epic 11 bug fix 2026-05-19 — the existing-user branch USED to assume the
            // Cognito user already exists "from a prior provisioning or organic signup".
            // That assumption breaks in local-dev (DB synced from staging at a time when
            // Cognito had the user, but staging Cognito has since lost the record) and
            // would also break in prod if a Cognito user were deleted out-of-band. The
            // speaker would then land in READY with no Cognito shell, and the subsequent
            // READY→INVITED `issueInvitationCredentials` call would 404 on
            // `cognitoService.getUserStatus`.
            //
            // `adminCreateUserSilently` is idempotent (catches `UsernameExistsException`
            // and logs a no-op), so this is safe to call unconditionally — the call is a
            // round-trip-only no-op when the Cognito user already exists, and creates the
            // missing shell when it doesn't.
            String throwawayTempPassword = passwordGenerator.generate();
            String newCognitoSub = cognitoService.adminCreateUserSilently(
                    normalizedEmail, throwawayTempPassword, user.getUsername());
            throwawayTempPassword = null;
            // Epic 11 bug fix 2026-05-19 — when we just created a NEW Cognito user,
            // sync the new `sub` back into user_profiles. Otherwise the DB row keeps
            // the stale sub from a deleted prior Cognito user and the
            // PreTokenGeneration Lambda's primary-key lookup misses on the next sign-in
            // (the speaker's JWT comes through with no `custom:role` claim, blank
            // dashboard). `newCognitoSub` is null when the Cognito user already existed
            // (idempotent no-op) — we leave the DB alone in that case.
            linkToNewLogin(user, newCognitoSub, "existing-user branch");
            return new ProvisionUserResponse()
                    .username(user.getUsername())
                    .created(false);
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
            created = createNewUser(createRequest, actor);
        } catch (DataIntegrityViolationException e) {
            // P1 (review patch): another caller raced us to createNewUser for the same email
            // and won. The email UNIQUE constraint fired. Treat as idempotent: re-fetch and
            // grant the role.
            log.info("Provisioning: lost create race for email={} — re-fetching as idempotent existing-user case",
                    LoggingUtils.maskEmail(normalizedEmail));
            User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "DataIntegrityViolation on createNewUser but email not found on re-fetch: "
                                    + normalizedEmail, e));
            roleService.addRole(user.getUsername(), roleToGrant);
            // Story 11.E.2: race-loser branch does NOT call AdminCreateUser — the parallel
            // caller that won the create race has already (or will) handle the Cognito shell.
            return new ProvisionUserResponse()
                    .username(user.getUsername())
                    .created(false);
        }

        // The default initial role from createNewUser is ATTENDEE. Grant the requested role if it's not
        // already there. addRole is idempotent per RoleService.addRole(...) duplicate-detection.
        roleService.addRole(created.getUsername(), roleToGrant);

        log.info("Provisioning: created user {} with role {} (created=true)", created.getUsername(), roleToGrant);

        // Story 11.E.2 (AC1): create the Cognito user shell with a throwaway password and
        // FORCE_CHANGE_PASSWORD status. The throwaway is immediately discarded — the real
        // temporary password is issued at READY → INVITED via issueInvitationCredentials.
        // Any failure here aborts the surrounding @Transactional, rolling back createNewUser
        // + addRole (per AC5 item 1).
        String throwawayTempPassword = passwordGenerator.generate();
        String newCognitoSub = cognitoService.adminCreateUserSilently(
                normalizedEmail, throwawayTempPassword, created.getUsername());
        // Discard the throwaway from this scope by reassigning. The local variable goes
        // out of scope on method return; the value is never logged or persisted.
        throwawayTempPassword = null;
        // Link the profile to the new login's sub, as the existing-user branch does, so the
        // PreTokenGeneration Lambda finds it by its primary key on the first sign-in.
        linkToNewLogin(created, newCognitoSub, "new-user branch");
        log.info("Cognito user provisioned for {} (status=FORCE_CHANGE_PASSWORD)",
                LoggingUtils.maskEmail(normalizedEmail));

        return new ProvisionUserResponse()
                .username(created.getUsername())
                .created(true);
    }

    /**
     * Stores the sub of a login {@code adminCreateUserSilently} just created in
     * {@code user_profiles.cognito_user_id}, so the PreTokenGeneration Lambda finds the profile by
     * its primary key. {@code newCognitoSub} is null when Cognito already had a login for the
     * address (idempotent no-op): the profile is left as it is.
     */
    private void linkToNewLogin(User user, String newCognitoSub, String where) {
        if (newCognitoSub != null && !newCognitoSub.equals(user.getCognitoUserId())) {
            user.setCognitoUserId(newCognitoSub);
            userRepository.save(user);
            log.info("Linked user {} to Cognito sub {} ({})", user.getUsername(), newCognitoSub, where);
        }
    }

    /**
     * Issue (or skip) Cognito temp credentials at READY → INVITED.
     *
     * <p>Story 11.E.2 (AC2 — Resolved Q#1 Variant B + Q#4). Called by EMS's
     * {@code SpeakerWorkflowService.runInvitedHook} via the sibling
     * {@code POST /internal/users/{username}/issue-invitation-credentials} endpoint.
     *
     * <p>Branches on the Cognito user's current status:
     * <ul>
     *   <li>{@code FORCE_CHANGE_PASSWORD} / {@code RESET_REQUIRED} → generate fresh
     *       temp password, call {@code AdminSetUserPassword(Permanent=false)}, return
     *       {@code FRESH_TEMP_PASSWORD}.</li>
     *   <li>{@code CONFIRMED} → no Cognito mutation; return
     *       {@code USE_EXISTING_PASSWORD} with {@code temporaryPassword=null}.</li>
     *   <li>{@code UNCONFIRMED} → defensive: treat as FORCE_CHANGE_PASSWORD (organic
     *       sign-ups do not occur in BATbern's invite-only flow, but the branch is safe).</li>
     *   <li>{@code ARCHIVED} / {@code COMPROMISED} / {@code UNKNOWN} → throw
     *       {@link UnprocessableInvitationStateException} (mapped to HTTP 422).</li>
     * </ul>
     *
     * <p>Annotated {@code @Transactional(readOnly = true)} — the DB layer only performs a
     * single read ({@code userRepository.findByUsername}); the Cognito calls
     * ({@code AdminGetUser}, {@code AdminSetUserPassword}) mutate external state but do not
     * touch the PostgreSQL transaction. {@code readOnly = true} accurately describes the
     * DB-transactional semantics (no rollback needed because no DB write occurs).
     *
     * <p>Review patch B2 (P15) noted this could mislead readers into expecting "no
     * side-effects"; the Javadoc here calls out the external mutation explicitly.
     * {@code NOT_SUPPORTED} would be more semantically pure but suspends the test
     * transaction so seeded fixtures aren't visible — keep {@code readOnly = true} and
     * document the external-mutation invariant in code comments.
     *
     * @param username target user's username (must exist in PostgreSQL)
     * @param actor    who acted: the forwarded user's username, else {@code system:<job>} (logged)
     * @return action discriminator + fresh temp password (or null)
     * @throws UserNotFoundException                   if {@code username} is not in CUMS
     * @throws UnprocessableInvitationStateException   if Cognito reports a status that needs operator intervention
     */
    // Epic 11 bug fix 2026-05-19 — was `@Transactional(readOnly = true)`. The self-heal
    // path below (Cognito user missing → create + re-link DB) needs write access to
    // user_profiles.cognito_user_id; readOnly would silently reject the update.
    @Transactional
    public InvitationCredentialsResponse issueInvitationCredentials(String username, String actor) {
        log.info("issueInvitationCredentials (Story 11.E.2): username={}, actor={}", username, actor);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Epic 11 bug fix 2026-05-19 — self-heal the "DB has user, Cognito doesn't"
        // case. Previously a missing Cognito user would throw 404 here, surfacing as
        // a misleading "User not found: <username>" error in EMS (the DB user IS
        // there; only the Cognito shell is missing). This happens when:
        //   - Local-dev DB was synced from staging at a time when the Cognito record
        //     existed, but the staging Cognito has since lost or rotated the user.
        //   - A pre-Epic 11 provisioning path (existing-user branch in
        //     `provisionUserWithRole`) skipped Cognito creation.
        //   - The Cognito user was deleted out-of-band.
        //
        // Since READY→INVITED IS the moment the operator commits to inviting the
        // speaker, transparently creating the missing Cognito shell + proceeding
        // with FRESH_TEMP_PASSWORD matches the operator's intent. The bug-prevention
        // fix in `provisionUserWithRole` means this branch only fires for speakers
        // promoted BEFORE the prevention fix landed (and for the manual out-of-band
        // delete case).
        UserStatusType status;
        try {
            status = cognitoService.getUserStatus(user.getEmail());
        } catch (UserNotFoundException ex) {
            log.warn("Cognito user missing for {} — self-healing by creating the shell",
                    LoggingUtils.maskEmail(user.getEmail()));
            String throwawayTempPassword = passwordGenerator.generate();
            String newCognitoSub = cognitoService.adminCreateUserSilently(
                    user.getEmail(), throwawayTempPassword, user.getUsername());
            throwawayTempPassword = null;
            // Epic 11 bug fix 2026-05-19 — sync the freshly-created sub back into
            // user_profiles. The DB row was carrying a stale sub from a deleted prior
            // Cognito user; without this re-link the PreTokenGeneration Lambda's
            // primary-key lookup misses on the next sign-in and the speaker's JWT
            // ends up with no `custom:role` claim (= blank dashboard).
            linkToNewLogin(user, newCognitoSub, "issueInvitationCredentials self-heal");
            // Newly-created Cognito users land in FORCE_CHANGE_PASSWORD; fall through
            // to the corresponding switch branch which issues a fresh known temp.
            status = UserStatusType.FORCE_CHANGE_PASSWORD;
        }
        log.info("Cognito status for {}: {}", LoggingUtils.maskEmail(user.getEmail()), status);

        return switch (status) {
            case FORCE_CHANGE_PASSWORD, RESET_REQUIRED -> {
                String fresh = passwordGenerator.generate();
                cognitoService.adminSetTemporaryPassword(user.getEmail(), fresh);
                yield new InvitationCredentialsResponse(fresh,
                        InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
            }
            case CONFIRMED -> new InvitationCredentialsResponse(null,
                    InvitationCredentialsResponse.ActionEnum.USE_EXISTING_PASSWORD);
            case UNCONFIRMED -> {
                log.warn("UNCONFIRMED Cognito user for {} — defensively treating as FORCE_CHANGE_PASSWORD",
                        LoggingUtils.maskEmail(user.getEmail()));
                String fresh = passwordGenerator.generate();
                cognitoService.adminSetTemporaryPassword(user.getEmail(), fresh);
                yield new InvitationCredentialsResponse(fresh,
                        InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
            }
            case ARCHIVED, COMPROMISED, UNKNOWN_TO_SDK_VERSION -> {
                String msg = String.format(
                        "Cognito user %s is in state %s — requires operator intervention "
                                + "before invitation credentials can be issued.",
                        LoggingUtils.maskEmail(user.getEmail()), status);
                throw new UnprocessableInvitationStateException(msg);
            }
            default -> {
                // Story 11.E.2 review patch (P16 / B4): if AWS Cognito SDK adds a new
                // UserStatusType value that this switch does not enumerate (e.g.
                // EXTERNAL_PROVIDER, a future MFA_PENDING, …), log a WARN so the unknown
                // status is traceable in CloudWatch before the 422 fires. The unenumerated
                // state may be a legitimate flow that simply isn't handled yet — surface it
                // for triage rather than silently rejecting future Cognito states.
                log.warn("Unenumerated Cognito UserStatusType for {}: {} — review the switch"
                        + " in UserService.issueInvitationCredentials. Falling back to 422.",
                        LoggingUtils.maskEmail(user.getEmail()), status);
                String msg = String.format(
                        "Cognito user %s is in state %s — requires operator intervention "
                                + "before invitation credentials can be issued.",
                        LoggingUtils.maskEmail(user.getEmail()), status);
                throw new UnprocessableInvitationStateException(msg);
            }
        };
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
     * @param actor    who acted: the forwarded user's username, else {@code system:<job>}; recorded
     *                 as {@code updatedBy} of the {@link UserUpdatedEvent}
     * @return updated {@link UserResponse}
     * @throws UserNotFoundException   if the username does not exist
     * @throws UserValidationException if both fields are null (no-op patches rejected)
     */
    public UserResponse patchUserProfile(String username, PatchUserProfileRequest request, String actor) {
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

        log.info("Patching user profile (Story 11.C.2): username={}, actor={}", username, actor);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (bioPresent) {
            user.setBio(request.getBio());
        }
        if (picturePresent) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        // Flush so that @PreUpdate has set updatedAt before the response is built in this transaction.
        User saved = userRepository.saveAndFlush(user);

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
                actor);
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
        log.info("Creating new user: {}", LoggingUtils.maskEmail(request.getEmail()));

        // Check if user already exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new UserValidationException("User with email " + request.getEmail() + " already exists");
        }

        // NOTE: No longer creating user in Cognito - invitation-based flow
        // Organizer creates DB record → Send invitation email → User signs up via registration page
        // → Cognito pre-token-generation hook will populate cognitoUserId on first login

        // Story 1.16.2: Generate username from first/last name
        String baseUsername = slugService.generateUsername(
                request.getFirstName(), request.getLastName(), request.getEmail());
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
    private User createNewUser(GetOrCreateUserRequest request, String createdByUsername) {
        // No Cognito login here: request.cognitoSync has no effect. A login comes from the sign-up page
        // (the PreTokenGeneration Lambda then fills cognitoUserId) or from issue-credentials.
        // Story 1.16.2: Generate username from first/last name
        String baseUsername = slugService.generateUsername(
                request.getFirstName(), request.getLastName(), request.getEmail());
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
                .email(request.getEmail())
                .username(username)  // Story 1.16.2: Generated username
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .companyId(companyId)  // Story 1.16.2: company name (slug)
                .roles(new java.util.HashSet<>(Set.of(Role.ATTENDEE)))  // mutable so callers can add roles
                .build();

        User savedUser = userRepository.save(user);

        // Publish domain event (Story 1.16.2: String IDs); createdBy is the caller's actor
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
     * AC14: Resource expansion (?include=company,settings)
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
                        response.setCompany(expandCompany(response.getCompanyId()));
                    }
                    break;
                case "settings":
                    // Story 4.9: one settings object (display, notifications, privacy); the former
                    // "preferences" include is merged into it
                    User userWithSettings = userRepository.findByUsername(response.getId())
                            .orElseThrow(() -> new UserNotFoundException(response.getId()));
                    response.setSettings(UserSettingsService.toDto(userWithSettings));
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
     * Expand the embedded company for {@code ?include=company}.
     *
     * <p>Company and User live in the same service, so this resolves the real
     * company (name + displayName + industry) directly via {@link CompanyService}
     * — needed so the profile company picker can show the human display name
     * rather than the ADR-003 slug. Falls back to a minimal placeholder if the
     * stored {@code companyId} no longer resolves (e.g. a legacy free-typed
     * reference) so a dangling reference never 500s the whole profile load.
     */
    private ch.batbern.companyuser.dto.generated.Company expandCompany(String companyId) {
        // Non-throwing lookup: catching a not-found thrown through CompanyService still left
        // this read transaction rollback-only and 500ed the whole profile (#1142).
        return companyService.findCompanyByName(companyId)
                .map(company -> new ch.batbern.companyuser.dto.generated.Company()
                        .id(company.getName())
                        .name(company.getName())
                        .displayName(company.getDisplayName())
                        .industry(company.getIndustry())
                        .website(company.getWebsite()))
                .orElseGet(() -> {
                    log.warn("Could not resolve company '{}' for profile expansion", companyId);
                    return new ch.batbern.companyuser.dto.generated.Company()
                            .id(companyId)
                            .name(companyId);
                });
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

    // ------------------------------------------------------------------
    // Story 10.32: additional email addresses per user
    // ------------------------------------------------------------------

    /**
     * Story 10.32 — register an additional email on the caller's profile.
     *
     * <p>Uniqueness is enforced globally (case-insensitive) across both the
     * primary {@code user_profiles.email} column and existing additional
     * emails. The per-user cap defaults to 5 (configurable via
     * {@code batbern.user.additional-emails.max}).
     */
    public AdditionalEmail addAdditionalEmail(@Valid AddAdditionalEmailRequest request) {
        String username = securityContext.getCurrentUsername();
        // P0-1 race-condition fix (review 2026-05-22): pessimistic lock on the
        // user_profiles row serialises concurrent POSTs so the cap check + insert
        // observe each other. Without this, two parallel adds at count=4 both
        // pass the gate and the user ends up with 6 rows.
        // P2-7: do NOT echo the JWT-derived username in the 404 body — the
        // /me path means the principal is the request author; a missing row
        // is an internal data state issue.
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new UserNotFoundException("Current user profile not found", true));

        String emailNormalized = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (additionalEmailRepository.countByUser(user) >= maxAdditionalEmailsPerUser) {
            throw new AdditionalEmailLimitReachedException(maxAdditionalEmailsPerUser);
        }
        if (userRepository.existsByEmailIgnoreCase(emailNormalized)
                || additionalEmailRepository.existsByEmailIgnoreCase(emailNormalized)) {
            throw new AdditionalEmailDuplicateException(emailNormalized);
        }

        UserAdditionalEmail entity = UserAdditionalEmail.builder()
                .email(emailNormalized)
                .label(request.getLabel() != null ? request.getLabel().trim() : null)
                .createdAt(java.time.Instant.now())
                .build();
        user.addAdditionalEmail(entity);
        // `user` is the managed entity returned by findByUsernameForUpdate, so the
        // collection mutation is cascade-persisted directly on flush — assigning the
        // generated UUID to THIS `entity` instance (the verification token below binds
        // to entity.getId()). We deliberately avoid userRepository.save(user) here:
        // save() on an already-managed entity is a merge, which persists a managed
        // *copy* of the new child and leaves this `entity` transient with a null id.
        userRepository.flush();

        // Audit: persistent audit table is reserved infra (activity_history has
        // no JPA entity yet — see Story 10.32 Dev Notes; AC19 deferred per the
        // 2026-05-22 code review). Until that lands we emit a structured INFO
        // line ingested by CloudWatch. Emails are masked because the log is
        // operator-facing only (no user-visible timeline yet — OQ#6 downgraded
        // in the same review). The created_at timestamp on the entity remains
        // the row-level audit trail.
        log.info("ADDITIONAL_EMAIL_ADDED user={} email={}",
                LoggingUtils.maskEmail(user.getEmail()),
                LoggingUtils.maskEmail(emailNormalized));

        // Additional-email verification (v2): dispatch a signed verification link.
        // Send is failure-tolerant — a mail failure (SES outage, template missing,
        // reserved-domain recipient) must NEVER fail or roll back the add. We only
        // WARN-log (with a masked email) and still return 201.
        sendVerificationEmailSafely(entity, user);

        return UserResponseMapper.mapAdditionalEmailToDto(entity);
    }

    /**
     * Additional-email verification (v2) — best-effort dispatch of the verification
     * email. Swallows all exceptions (logged as WARN with masked email) so the add
     * flow always succeeds.
     */
    private void sendVerificationEmailSafely(UserAdditionalEmail entity, User user) {
        try {
            String token = verificationTokenService.generateToken(entity.getId(), entity.getEmail());
            String locale = resolveUserLocale(user);
            verificationEmailService.sendVerificationEmail(entity.getEmail(), token, locale);
        } catch (Exception e) {
            log.warn("Failed to send additional-email verification to: {} — {}",
                    LoggingUtils.maskEmail(entity.getEmail()), LoggingUtils.maskEmailsIn(e.getMessage()));
        }
    }

    /**
     * Resolve the user's language preference (de/en) for verification email
     * locale selection. Anything other than 'de' resolves to 'en' (the email
     * templates only exist in de + en per the project localization rule).
     */
    private String resolveUserLocale(User user) {
        if (user.getPreferences() != null && user.getPreferences().getLanguage() != null) {
            String language = user.getPreferences().getLanguage();
            // Any German variant (de, de-DE, de-CH, …) gets the German template; everything
            // else (including gsw-BE per the email-template DE+EN-only rule) falls back to en.
            return language.toLowerCase(Locale.ROOT).startsWith("de") ? "de" : "en";
        }
        return "en";
    }

    /**
     * Additional-email verification (v2) — resend the verification email for one of
     * the caller's own additional emails.
     *
     * @throws AdditionalEmailNotFoundException        if the email is not on the caller's profile (404)
     * @throws AdditionalEmailAlreadyVerifiedException if the email is already verified (409)
     */
    public void resendVerification(String email) {
        String username = securityContext.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Current user profile not found", true));

        String emailNormalized = email.trim().toLowerCase(Locale.ROOT);
        UserAdditionalEmail row = additionalEmailRepository
                .findByUserAndEmailIgnoreCase(user, emailNormalized)
                .orElseThrow(() -> new AdditionalEmailNotFoundException(emailNormalized));

        if (row.getVerifiedAt() != null) {
            throw new AdditionalEmailAlreadyVerifiedException(emailNormalized);
        }

        String token = verificationTokenService.generateToken(row.getId(), row.getEmail());
        String locale = resolveUserLocale(user);
        verificationEmailService.sendVerificationEmail(row.getEmail(), token, locale);

        log.info("ADDITIONAL_EMAIL_VERIFICATION_RESENT user={} email={}",
                LoggingUtils.maskEmail(user.getEmail()),
                LoggingUtils.maskEmail(emailNormalized));
    }

    /**
     * Additional-email verification (v2) — GET-check path. Validates the token and
     * returns the (masked) email + current verification status WITHOUT mutating
     * any state. Mail-scanner prefetches land here.
     *
     * @throws VerificationTokenExpiredException if the token has expired (400)
     * @throws VerificationTokenInvalidException if the token is malformed/forged (400)
     */
    @Transactional(readOnly = true)
    public AdditionalEmailVerificationCheckResponse checkVerificationToken(String token) {
        Claims claims = parseVerificationToken(token);
        java.util.UUID rowId = verificationTokenService.getAdditionalEmailId(claims);
        String claimEmail = verificationTokenService.getEmail(claims);

        // The check path does not 404 on a missing row — it reports the token's
        // email + a "not verified" status so the landing page can still render a
        // sensible prompt. The actual confirm (POST) is the one that 404s.
        boolean verified = additionalEmailRepository.findById(rowId)
                .filter(r -> r.getEmail().equalsIgnoreCase(claimEmail))
                .map(r -> r.getVerifiedAt() != null)
                .orElse(false);

        return new AdditionalEmailVerificationCheckResponse()
                .email(LoggingUtils.maskEmail(claimEmail))
                .verified(verified);
    }

    /**
     * Additional-email verification (v2) — POST-confirm path. Validates the token,
     * loads the row by UUID, confirms the email claim matches, and sets
     * {@code verified_at} if not already set. Idempotent: a token for an
     * already-verified row returns {@code alreadyVerified = true}.
     *
     * @throws VerificationTokenExpiredException if the token has expired (400)
     * @throws VerificationTokenInvalidException if the token is malformed/forged (400)
     * @throws AdditionalEmailNotFoundException  if the row was deleted/re-added since issuance (404)
     */
    @Transactional
    public AdditionalEmailVerificationConfirmResponse confirmVerificationToken(String token) {
        Claims claims = parseVerificationToken(token);
        java.util.UUID rowId = verificationTokenService.getAdditionalEmailId(claims);
        String claimEmail = verificationTokenService.getEmail(claims);

        UserAdditionalEmail row = additionalEmailRepository.findById(rowId)
                .filter(r -> r.getEmail().equalsIgnoreCase(claimEmail))
                .orElseThrow(() -> new AdditionalEmailNotFoundException(claimEmail));

        boolean alreadyVerified = row.getVerifiedAt() != null;
        if (!alreadyVerified) {
            row.setVerifiedAt(java.time.Instant.now());
            additionalEmailRepository.save(row);
            log.info("ADDITIONAL_EMAIL_VERIFIED email={}", LoggingUtils.maskEmail(row.getEmail()));
        }

        return new AdditionalEmailVerificationConfirmResponse()
                .email(LoggingUtils.maskEmail(row.getEmail()))
                .verified(true)
                .alreadyVerified(alreadyVerified);
    }

    /**
     * Parse + validate a verification token, mapping JJWT exceptions to the
     * service-level 400 exceptions. Never throws a raw runtime exception that
     * would surface as a 500.
     */
    private Claims parseVerificationToken(String token) {
        if (token == null || token.isBlank()) {
            throw new VerificationTokenInvalidException();
        }
        try {
            return verificationTokenService.validateToken(token);
        } catch (ExpiredJwtException e) {
            throw new VerificationTokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new VerificationTokenInvalidException();
        }
    }

    /**
     * #1244 — switch the copy of event mails for one of the caller's additional addresses.
     * Switching it on needs a confirmed address; switching it off always works.
     */
    public AdditionalEmail updateAdditionalEmail(String email, UpdateAdditionalEmailRequest request) {
        String username = securityContext.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Current user profile not found", true));

        String emailNormalized = email.trim().toLowerCase(Locale.ROOT);
        UserAdditionalEmail row = additionalEmailRepository
                .findByUserAndEmailIgnoreCase(user, emailNormalized)
                .orElseThrow(() -> new AdditionalEmailNotFoundException(emailNormalized));

        boolean receive = Boolean.TRUE.equals(request.getReceiveEventMail());
        if (receive && row.getVerifiedAt() == null) {
            throw new AdditionalEmailNotVerifiedException(emailNormalized);
        }
        row.setReceiveEventMail(receive);
        additionalEmailRepository.save(row);

        log.info("ADDITIONAL_EMAIL_EVENT_MAIL_COPY user={} email={} receive={}",
                LoggingUtils.maskEmail(user.getEmail()), LoggingUtils.maskEmail(emailNormalized), receive);
        return UserResponseMapper.mapAdditionalEmailToDto(row);
    }

    /**
     * Story 10.32 — remove an additional email from the caller's profile.
     */
    public void deleteAdditionalEmail(String email) {
        String username = securityContext.getCurrentUsername();
        // P2-7: see note above.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Current user profile not found", true));

        String emailNormalized = email.trim().toLowerCase(Locale.ROOT);
        UserAdditionalEmail row = additionalEmailRepository
                .findByUserAndEmailIgnoreCase(user, emailNormalized)
                .orElseThrow(() -> new AdditionalEmailNotFoundException(emailNormalized));

        // P2-9 fix: delete the row directly via the repository rather than
        // routing through the aggregate's orphanRemoval collection. The User
        // entity's additionalEmails list is LAZY and may not be initialised
        // here, in which case user.removeAdditionalEmail(row).remove(...) is
        // a no-op and orphanRemoval never fires. Direct delete is unambiguous
        // and avoids relying on session-state of an unloaded collection.
        user.removeAdditionalEmail(row);
        additionalEmailRepository.delete(row);

        log.info("ADDITIONAL_EMAIL_REMOVED user={} email={}",
                LoggingUtils.maskEmail(user.getEmail()),
                LoggingUtils.maskEmail(emailNormalized));
    }

}
