package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.exception.CognitoOperationException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
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
 * <p>Story 1.14-2 (AC2): {@link #syncUserAttributes} and {@link #createCognitoUser} are
 * intentional NO-OPs — DB is the source of truth and the invitation-based registration
 * flow lets Cognito fill in the {@code cognitoUserId} at first login.
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
    public void syncUserAttributes(User user) {
        // NO-OP: DB is source of truth for user attributes; Cognito only authenticates.
        log.debug("Cognito sync disabled - DB is source of truth for user: {}", user.getUsername());
    }

    @Override
    public String createCognitoUser(GetOrCreateUserRequest request) {
        // NO-OP: invitation-based flow — user signs up via registration page and
        // PreTokenGeneration Lambda populates cognitoUserId at first login.
        log.debug("Cognito user creation disabled - invitation flow for user: {}", request.getEmail());
        return null;
    }

    @Override
    public void adminCreateUserSilently(String email, String throwawayTempPassword, String appUsername) {
        AdminCreateUserRequest req = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .temporaryPassword(throwawayTempPassword)
                .messageAction(MessageActionType.SUPPRESS)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("preferred_username").value(appUsername).build()
                        // No given_name/family_name/custom:role — those live in PostgreSQL
                        // per ADR-004 (user_profiles) + ADR-001 (user_roles).
                )
                .build();

        try {
            cognitoClient.adminCreateUser(req);
            log.info("Cognito user created for {} (FORCE_CHANGE_PASSWORD)", LoggingUtils.maskEmail(email));
        } catch (UsernameExistsException e) {
            log.info("Cognito user already exists for {} - idempotent no-op", LoggingUtils.maskEmail(email));
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

}
