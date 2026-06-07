package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.exception.CognitoOperationException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

/**
 * Service for integrating with AWS Cognito.
 *
 * <p>Story 1.14-2 (AC2): legacy Cognito sync seam (currently a NO-OP — DB is source of truth).
 *
 * <p>Story 11.E.2: extended with the three Admin SDK calls used by the Cognito-provisioning
 * flow ({@code adminCreateUserSilently} at READY; {@code getUserStatus} +
 * {@code adminSetTemporaryPassword} at INVITED).
 */
public interface CognitoIntegrationService {

    /**
     * Sync user attributes to Cognito.
     *
     * @param user User to sync
     */
    void syncUserAttributes(User user);

    /**
     * Create user in Cognito.
     *
     * @param request User creation request
     * @return Cognito user ID
     */
    String createCognitoUser(GetOrCreateUserRequest request);

    /**
     * Create a Cognito user shell with a throwaway temporary password and
     * {@code FORCE_CHANGE_PASSWORD} status.
     *
     * <p>Story 11.E.2 (AR15, FR3). Called by {@code UserService.provisionUserWithRole}
     * at CONTACTED → READY.
     *
     * <p>Suppresses Cognito's default invitation email ({@code MessageAction=SUPPRESS}) —
     * BATbern sends its own templated invitation at INVITED time. The temp password passed
     * here is internal-only and is NEVER returned to the speaker;
     * {@link #adminSetTemporaryPassword} generates the real one at INVITED time.
     *
     * <p>Idempotency: if the email already exists in the user pool
     * ({@code UsernameExistsException}), the method returns silently — the caller's
     * existing-user branch handles "already provisioned".
     *
     * @param email speaker's email (becomes the Cognito Username AND the email attribute)
     * @param throwawayTempPassword satisfies the pool policy; never surfaced
     * @param appUsername BATbern's username (set as the {@code preferred_username} Cognito attribute)
     * @return the new Cognito user's {@code sub} attribute when a user was created,
     *         {@code null} when an existing user was found (idempotent no-op).
     *         Epic 11 bug fix 2026-05-19 — callers use this to keep
     *         {@code user_profiles.cognito_user_id} in sync with the actual Cognito sub
     *         so the PreTokenGeneration Lambda's primary-key lookup hits on subsequent
     *         logins (otherwise it must rely on the email-fallback path, which the
     *         deployed Lambda may or may not have at any given time).
     * @throws CognitoOperationException on any Cognito error other than UsernameExistsException
     */
    String adminCreateUserSilently(String email, String throwawayTempPassword, String appUsername);

    /**
     * Read the current Cognito user-status for the given email.
     *
     * <p>Story 11.E.2 (AR15, FR9). Called by {@code UserService.issueInvitationCredentials}
     * at READY → INVITED.
     *
     * @param email speaker's email
     * @return the AWS SDK {@link UserStatusType} value reported by {@code AdminGetUser}
     * @throws UserNotFoundException if AdminGetUser returns {@code UserNotFoundException} — the
     *         User row exists in PostgreSQL but no Cognito user — operator must intervene
     * @throws CognitoOperationException on any other Cognito error
     */
    UserStatusType getUserStatus(String email);

    /**
     * Set a new temporary password for an existing Cognito user, keeping them in
     * {@code FORCE_CHANGE_PASSWORD} state.
     *
     * <p>Story 11.E.2 (AR15, FR9). Called by {@code UserService.issueInvitationCredentials}
     * when the user's status is {@code FORCE_CHANGE_PASSWORD} or {@code RESET_REQUIRED}.
     *
     * <p>Uses {@code AdminSetUserPassword} with {@code Permanent=false}.
     *
     * @param email speaker's email
     * @param freshTempPassword the password to set (must satisfy the pool policy); the speaker
     *        will be challenged to change it on first login
     * @throws CognitoOperationException on any Cognito error
     */
    void adminSetTemporaryPassword(String email, String freshTempPassword);
}
