package ch.batbern.events.client;

import ch.batbern.events.dto.CompanyBasicDto;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;

import java.time.Instant;
import java.util.List;

/**
 * Client interface for communicating with the User Management Service API.
 *
 * This client replaces direct database access to the user_profiles table,
 * providing API-based user data retrieval for speaker enrichment and validation.
 *
 * Story 2.2a: Extended to support anonymous user creation (ADR-005)
 * Story BAT-7: Extended to support notification-related user queries
 *
 * All methods use aggressive caching (15min TTL) to minimize API calls.
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
     * Check if a user exists by username.
     *
     * @param username User's username
     * @return true if user exists, false otherwise
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    boolean validateUserExists(String username);

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
     * @return User notification preferences
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    ch.batbern.events.notification.UserPreferences getPreferences(String username);

    /**
     * Get user's email address by username.
     * Used for sending email notifications.
     *
     * @param username User's username
     * @return User's email address
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    String getEmailByUsername(String username);

    /**
     * Get the user's preferred UI/web language (Story 7.3).
     *
     * <p>Hits {@code GET /api/v1/users/{username}?include=preferences} and returns the lowercase
     * language code from {@code preferences.language} (e.g. {@code "de"}, {@code "en"}, {@code "fr"}).
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
     * <p>Hits {@code GET /api/v1/users/{username}?include=preferences} (the same proven path as
     * {@link #getPreferredLanguage}) and returns the lowercase value of
     * {@code preferences.qnaNotificationFrequency} — {@code "live"}, {@code "daily"}, or
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
     * Get user's last login timestamp.
     * Used for in-app notification queries.
     *
     * @param username User's username
     * @return Last login timestamp
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    Instant getLastLogin(String username);

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
     * Get all companies (basic info) from the company-user-management-service.
     * Used for the companies[] list in the legacy BAT export envelope.
     * Story 10.20: AC1
     *
     * @return List of companies with name, displayName, website
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<CompanyBasicDto> getAllCompanies();

    /**
     * Resolve a single company slug to its human-readable display name.
     *
     * <p>Hits {@code GET /api/v1/companies/{slug}} on company-user-management-service and
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
     * <p>Cognito wiring is deliberately stubbed in Story 11.C.2; {@code temporaryPassword}
     * on the response is always {@code null}. Story 11.E.2 will wire
     * {@code AdminCreateUser}/{@code AdminSetUserPassword} and populate that field.
     *
     * @param request username (optional), email (required), firstName, lastName, role (required)
     * @return canonical username + {@code created} flag + {@code temporaryPassword=null}
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
     * <p>Authorization is enforced on the CUMS side: ORGANIZER/ADMIN may patch any user;
     * SPEAKERS may patch only their own profile.
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
