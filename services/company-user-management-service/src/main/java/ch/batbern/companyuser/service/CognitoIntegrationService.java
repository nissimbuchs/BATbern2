package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.CognitoAuthResult;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;

import java.util.Optional;

/**
 * Service for integrating with AWS Cognito
 * Story 1.14-2 AC2: Cognito sync on user create/update
 * Story 9.2: Speaker account creation and role extension
 * Story 9.3: Dual authentication (password auth + password reset)
 */
public interface CognitoIntegrationService {

    /**
     * Sync user attributes to Cognito
     * @param user User to sync
     */
    void syncUserAttributes(User user);

    /**
     * Create user in Cognito
     * @param request User creation request
     * @return Cognito user ID
     */
    String createCognitoUser(GetOrCreateUserRequest request);

    /**
     * Story 9.2 Task 2.1: Find a Cognito user by email address.
     *
     * @param email Email address to search for
     * @return Optional containing the AdminGetUserResponse if found, empty otherwise
     */
    Optional<AdminGetUserResponse> findUserByEmail(String email);

    /**
     * Story 9.2 Task 2.2: Create a new Cognito user for a speaker invitation.
     * Sets custom:role=SPEAKER and sends temporary password.
     *
     * @param email             Speaker email address
     * @param name              Speaker display name (firstName + lastName)
     * @param temporaryPassword Temporary password meeting Cognito policy
     * @return Cognito user sub (UUID)
     */
    String createCognitoSpeaker(String email, String name, String temporaryPassword);

    /**
     * Story 9.2 Task 2.3: Add a role to an existing Cognito user.
     * Reads existing custom:role, appends the new role if not already present,
     * then calls AdminUpdateUserAttributes.
     *
     * @param email   Email of the Cognito user
     * @param newRole Role to add
     */
    void addRoleToCognitoUser(String email, Role newRole);

    /**
     * Story 9.2 Task 4.4: Compensating transaction — delete a Cognito user by username/sub.
     * Called when local DB update fails after a NEW Cognito account was created, to prevent
     * orphaned Cognito accounts with no corresponding local DB user.
     *
     * @param cognitoUsername Cognito username (sub UUID) to delete
     */
    void deleteCognitoAccount(String cognitoUsername);

    /**
     * Story 9.3 Task 1.1: Authenticate a user with email and password via Cognito.
     * Uses ADMIN_USER_PASSWORD_AUTH flow (server-side, no SRP required).
     *
     * @param email    User email address
     * @param password User password (temporary or permanent)
     * @return CognitoAuthResult containing access, id, and refresh tokens
     * @throws software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException
     *     if credentials are wrong
     * @throws software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException  if email not in pool
     */
    CognitoAuthResult authenticateUser(String email, String password);

    /**
     * Story 9.3 Task 1.2: Initiate Cognito password reset for a user.
     * Cognito sends a confirmation code directly to the user's registered email.
     *
     * @param email User email address
     */
    void initiatePasswordReset(String email);

    /**
     * Story 9.3 Task 1.3: Confirm a Cognito password reset with the confirmation code.
     *
     * @param email            User email address
     * @param confirmationCode Code received via Cognito-sent email
     * @param newPassword      New password (must meet Cognito pool policy)
     */
    void confirmPasswordReset(String email, String confirmationCode, String newPassword);
}
