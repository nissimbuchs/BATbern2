package ch.batbern.events.client;

import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;

import java.util.List;

/**
 * Client of the company-user-management service (CUMS), its only way in from EMS.
 *
 * <p>Every operation is a CUMS {@code /internal} operation ({@code docs/api/internal/
 * cums-internal.openapi.yml}, ADR-016). Calls forward the signed-in user's JWT when there is one
 * and work without it (shared-kernel {@code S2sHeaders}). Reads are cached 15 min.
 *
 * <p>Story 2.2a: anonymous user creation (ADR-005). Story BAT-7: notification-related queries.
 */
public interface UserApiClient {

    /**
     * Get user profile by username.
     *
     * @param username User's username (public identifier)
     * @return User profile data
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    UserResponse getUserByUsername(String username);

    /**
     * Get or create user profile (ADR-005: Anonymous Event Registration).
     * <p>
     * Used for anonymous event registration where users register without creating a Cognito account.
     * Creates user with cognito_id=NULL when cognitoSync=false.
     * <p>
     * If user already exists (by email), returns existing user profile.
     * If user doesn't exist, creates new anonymous user profile.
     * <p>
     * Cached for 15 minutes using email as cache key.
     *
     * @param request User creation/lookup request with email, names, and cognitoSync flag
     * @return GetOrCreateUserResponse with username, created flag, and user profile data
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request);

    // Notification-specific methods (Story BAT-7)

    /**
     * Get user notification preferences.
     * Used for checking if user wants to receive notifications.
     *
     * @param username User's username
     * @return User notification preferences (CUMS defaults when the user has none)
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    ch.batbern.events.notification.UserPreferences getPreferences(String username);

    /**
     * Get the user's preferred UI/web language (Story 7.3).
     *
     * <p>Hits {@code GET /internal/users/{username}?include=settings} and returns the lowercase
     * language code from {@code settings.language} (e.g. {@code "de"}, {@code "en"}, {@code "fr"}).
     *
     * <p><b>Lenient by design:</b> returns {@code null} — never throws — when the username is
     * blank, the user/preferences are absent, or CUMS is degraded. The slides-online send treats
     * {@code null} (and any non-de/en value) as the German fallback, so a per-recipient lookup
     * failure can never abort the rest of the send.
     *
     * @param username User's username (public identifier)
     * @return lowercase language code, or {@code null} when unavailable
     */
    String getPreferredLanguage(String username);

    /**
     * Get the user's Q&A notification cadence preference (Story 15.7).
     *
     * <p>Hits {@code GET /internal/users/{username}?include=settings} (the same path as
     * {@link #getPreferredLanguage}) and returns the lowercase value of
     * {@code settings.qnaNotificationFrequency} — {@code "live"}, {@code "daily"}, or
     * {@code "off"}.
     *
     * <p><b>Lenient by design:</b> returns {@code null} — never throws — when the username is
     * blank, the user/preferences are absent, or CUMS is degraded. The Q&A digest flush treats
     * {@code null} as the default {@code "live"}, so a single recipient's lookup failure can never
     * abort the rest of the flush.
     *
     * @param username User's username (public identifier)
     * @return lowercase {@code live|daily|off}, or {@code null} when unavailable
     */
    String getQnaNotificationFrequency(String username);


    /**
     * Get all organizer usernames.
     * Used for sending in-app notifications to all organizers.
     *
     * @return List of organizer usernames
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<String> getOrganizerUsernames();

    /**
     * Get all partner usernames.
     * Used for auto-enrolling all PARTNER-role users when a new event is created.
     *
     * @return List of partner usernames
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<String> getPartnerUsernames();

    /**
     * Story 16.10: the users of one company holding {@code role} (CUMS {@code GET /internal/users}),
     * with their additional emails, every page.
     *
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<UserResponse> getUsersByCompany(String companyName, String role);

    /**
     * #1183: the usernames owning {@code email} as primary or VERIFIED additional address (CUMS
     * {@code GET /internal/email-owners}); empty if nobody does or CUMS cannot answer. Uncached: a
     * public by-email form calls it, and a cached "nobody" would hide a profile created a minute ago.
     */
    List<String> getEmailOwners(String email);

    /**
     * Resolve a single company slug to its human-readable display name.
     *
     * <p>Hits {@code GET /internal/companies/{name}} on company-user-management-service and
     * returns {@code displayName} when set, falling back to the company {@code name}. The
     * lookup is cached per slug (15&nbsp;min) in {@code userApiCache} so repeated speakers
     * from the same company incur one upstream call.
     *
     * <p>Returns {@code null} when the slug is null/blank, the company is not found (404),
     * or CUMS is degraded — callers should treat null as "fall back to the slug" rather
     * than an error. (Nullable {@link String} rather than {@link java.util.Optional}
     * because Spring's {@code @Cacheable} unwraps Optional results before evaluating
     * {@code unless}, which would NPE the unless expression on an empty Optional.)
     *
     * @param companySlug the company's meaningful identifier ({@code User.companyId})
     * @return resolved display name, or {@code null} when unavailable
     */
    String getCompanyDisplayName(String companySlug);

    // Story 11.C.2 (AR13/AR14): canonical speaker-provisioning + profile-patch operations.

    /**
     * Provision a User with a role (idempotent).
     *
     * <p>Story 11.C.2 (AR13). Canonical entry point for the
     * {@code SpeakerWorkflowService.transition()} CONTACTED → READY hook to materialise
     * the Speaker as a User + SPEAKER role (replaces the deleted {@code Speaker} entity
     * per ADR-009 / Story 11.C.1).
     *
     * <p>Behaviour:
     * <ul>
     *   <li>If the User exists by email (case-insensitive lookup), grants the role if not
     *       already held and returns the existing username with {@code created=false}.</li>
     *   <li>If the User does not exist, creates the row, grants the role, and returns the
     *       generated username with {@code created=true}.</li>
     *   <li>Idempotent: re-calling for an already-provisioned user is a no-op.</li>
     * </ul>
     *
     * <p>CUMS ({@code POST /internal/users/provision}) creates the Cognito login silently and
     * grants SPEAKER only; any other role answers 400 ({@link UserServiceException}).
     *
     * @param request email (required), firstName, lastName, role (required, SPEAKER)
     * @return canonical username + {@code created} flag
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    ProvisionUserResponse provisionUserWithRole(ProvisionUserRequest request);

    /**
     * Patch user profile fields (bio, profilePictureUrl).
     *
     * <p>Story 11.C.2 (AR14). Called by the consolidated
     * {@code ContentSubmissionService} when an organizer (on behalf) or a speaker (self)
     * submits content that includes a CV blurb or a portrait. Per ADR-009 §"Decision 2"
     * + ADR-007: {@code bio} and {@code profilePictureUrl} live on User (single source of
     * truth) and are overwritten globally.
     *
     * <p>Authorization stays in EMS, where the user entered: {@code ContentSubmissionService}
     * resolves the username server-side, so a speaker patches only their own profile. CUMS
     * ({@code PATCH /internal/users/{username}/profile}) authorizes by operation (ADR-016).
     *
     * @param username target user's username
     * @param request  bio (nullable, max 5000) + profilePictureUrl (nullable, max 2048);
     *                 at least one must be present
     * @return updated user profile
     * @throws UserNotFoundException if username not found (404)
     * @throws UserServiceException  if API communication fails (5xx, timeout, network error)
     */
    UserResponse patchUserProfile(String username, PatchUserProfileRequest request);

    /**
     * Issue (or skip) Cognito temp credentials at READY → INVITED.
     *
     * <p>Story 11.E.2 (AR15, FR9). Called by
     * {@code SpeakerWorkflowService.runInvitedHook} on the READY → INVITED transition.
     * CUMS branches on the Cognito user's current status: FORCE_CHANGE_PASSWORD /
     * RESET_REQUIRED / UNCONFIRMED → fresh temp password; CONFIRMED → null +
     * USE_EXISTING_PASSWORD action; ARCHIVED / COMPROMISED → HTTP 422.
     *
     * <p>Idempotent: repeated calls are safe (each FRESH_TEMP_PASSWORD call overwrites
     * the previous temp password via {@code AdminSetUserPassword}).
     *
     * @param username target user's username (must exist in CUMS)
     * @return action discriminator + fresh temp password (or null when use-existing)
     * @throws UserNotFoundException if {@code username} is not in CUMS (404)
     * @throws UserServiceException  on 422 / 502 / 5xx / network failure
     */
    InvitationCredentialsResponse issueInvitationCredentials(String username);
}
