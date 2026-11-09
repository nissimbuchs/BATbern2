package ch.batbern.companyuser.service;

import ch.batbern.companyuser.exception.CognitoOperationException;
import ch.batbern.companyuser.exception.EmailHasOtherLoginException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Implementation of {@link CognitoIntegrationService}.
 *
 * <p>Story 11.E.2: {@link #adminCreateUserSilently}, {@link #getUserStatus}, and
 * {@link #adminSetTemporaryPassword} are real Cognito Admin SDK calls used by the
 * speaker-provisioning + invitation flow.
 */
@Slf4j
@Service
public class CognitoIntegrationServiceImpl implements CognitoIntegrationService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    public CognitoIntegrationServiceImpl(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${aws.cognito.user-pool-id}") String userPoolId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
    }

    @Override
    public String adminCreateUserSilently(String email, String throwawayTempPassword, String appUsername) {
        AdminCreateUserRequest req = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .temporaryPassword(throwawayTempPassword)
                .messageAction(MessageActionType.SUPPRESS)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("preferred_username").value(appUsername).build(),
                        // Story 12.1 AC6: custom:role='UNUSED' sentinel — documentation-in-the-data
                        // for console inspectors. The actual role lives in PostgreSQL per ADR-004
                        // (user_profiles) + ADR-001 (role_assignments); this attribute is NOT read
                        // for authorization (the PreTokenGeneration Lambda projects the claim from
                        // the DB). given_name/family_name still live in PostgreSQL only.
                        AttributeType.builder().name("custom:role").value("UNUSED").build()
                )
                .build();

        try {
            // Epic 11 bug fix 2026-05-19 — extract and return the new user's `sub` so
            // callers can keep `user_profiles.cognito_user_id` in sync (the DB row may
            // carry a stale sub from a deleted prior Cognito user; without this the
            // PreTokenGeneration Lambda's primary-key lookup misses and the JWT
            // ends up with no roles).
            var response = cognitoClient.adminCreateUser(req);
            log.info("Cognito user created for {} (FORCE_CHANGE_PASSWORD)", LoggingUtils.maskEmail(email));
            if (response == null || response.user() == null || response.user().attributes() == null) {
                return null;
            }
            return response.user().attributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse(null);
        } catch (UsernameExistsException e) {
            log.info("Cognito user already exists for {} - idempotent no-op", LoggingUtils.maskEmail(email));
            return null;
        } catch (InvalidParameterException | InvalidPasswordException e) {
            // Story 11.E.2 review patch (P6 / E14): caller-side bad input (malformed email,
            // password policy violation). Map to 400 via shared-kernel ValidationException
            // rather than the generic CognitoOperationException → 502 — operator sees the
            // real cause instead of "Identity provider unavailable; please retry shortly".
            log.warn("AdminCreateUser rejected with caller-side input for {}: {}",
                    LoggingUtils.maskEmail(email), e.awsErrorDetails().errorCode());
            throw new ch.batbern.shared.exception.ValidationException(
                    "Cognito rejected the request: " + e.awsErrorDetails().errorCode()
                            + " — check the email format and password policy.");
        } catch (CognitoIdentityProviderException e) {
            log.error("AdminCreateUser failed for {}: {}", LoggingUtils.maskEmail(email), e.getMessage());
            throw new CognitoOperationException("adminCreateUser", LoggingUtils.maskEmail(email), e);
        }
    }

    @Override
    public UserStatusType getUserStatus(String email) {
        AdminGetUserRequest req = AdminGetUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .build();

        try {
            AdminGetUserResponse response = cognitoClient.adminGetUser(req);
            return response.userStatus();
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException e) {
            log.warn("Cognito user not found for {} during AdminGetUser", LoggingUtils.maskEmail(email));
            throw new UserNotFoundException(email);
        } catch (CognitoIdentityProviderException e) {
            log.error("AdminGetUser failed for {}: {}", LoggingUtils.maskEmail(email), e.getMessage());
            throw new CognitoOperationException("adminGetUser", LoggingUtils.maskEmail(email), e);
        }
    }

    @Override
    public void adminSetTemporaryPassword(String email, String freshTempPassword) {
        AdminSetUserPasswordRequest req = AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .password(freshTempPassword)
                .permanent(false)
                .build();

        try {
            cognitoClient.adminSetUserPassword(req);
            log.info("Cognito temporary password issued for {} (FORCE_CHANGE_PASSWORD)",
                    LoggingUtils.maskEmail(email));
        } catch (CognitoIdentityProviderException e) {
            log.error("AdminSetUserPassword failed for {}: {}",
                    LoggingUtils.maskEmail(email), e.getMessage());
            throw new CognitoOperationException(
                    "adminSetUserPassword", LoggingUtils.maskEmail(email), e);
        }
    }

    @Override
    public void changeLoginEmail(String currentEmail, String newEmail) {
        AdminUpdateUserAttributesRequest req = AdminUpdateUserAttributesRequest.builder()
                .userPoolId(userPoolId)
                .username(currentEmail)
                .userAttributes(
                        AttributeType.builder().name("email").value(newEmail).build(),
                        AttributeType.builder().name("email_verified").value("true").build())
                .build();
        try {
            cognitoClient.adminUpdateUserAttributes(req);
            log.info("Cognito login moved from {} to {}", LoggingUtils.maskEmail(currentEmail),
                    LoggingUtils.maskEmail(newEmail));
        } catch (AliasExistsException e) {
            throw new EmailHasOtherLoginException(LoggingUtils.maskEmail(newEmail));
        } catch (CognitoIdentityProviderException e) {
            log.error("AdminUpdateUserAttributes (email) failed for {}: {}",
                    LoggingUtils.maskEmail(currentEmail), e.getMessage());
            throw new CognitoOperationException("adminUpdateUserAttributes", LoggingUtils.maskEmail(currentEmail), e);
        }
    }

    @Override
    public void disableLogin(String email) {
        try {
            cognitoClient.adminDisableUser(AdminDisableUserRequest.builder()
                    .userPoolId(userPoolId).username(email).build());
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoOperationException("adminDisableUser", LoggingUtils.maskEmail(email), e);
        }
    }

    @Override
    public void enableLogin(String email) {
        try {
            cognitoClient.adminEnableUser(AdminEnableUserRequest.builder()
                    .userPoolId(userPoolId).username(email).build());
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoOperationException("adminEnableUser", LoggingUtils.maskEmail(email), e);
        }
    }

    @Override
    public void deleteLogin(String email) {
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId).username(email).build());
            log.info("Cognito login deleted for {}", LoggingUtils.maskEmail(email));
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoOperationException("adminDeleteUser", LoggingUtils.maskEmail(email), e);
        }
    }
}
