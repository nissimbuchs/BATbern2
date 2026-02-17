package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.CognitoAuthResult;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of CognitoIntegrationService
 * Story 1.14-2 Task 12: Cognito Integration
 * AC2: Cognito sync on user create/update
 *
 * Story 9.2: Speaker account creation and role extension
 * - findUserByEmail: Lookup speaker by email in Cognito
 * - createCognitoSpeaker: Create new Cognito user with SPEAKER role
 * - addRoleToCognitoUser: Add role to existing Cognito user
 *
 * Story 9.3: Dual Authentication Support
 * - authenticateUser: ADMIN_USER_PASSWORD_AUTH flow for email+password login
 * - initiatePasswordReset: Trigger Cognito ForgotPassword email
 * - confirmPasswordReset: Confirm code + new password
 *
 * Retry strategy: 3 attempts with exponential backoff (1s / 2s / 4s)
 */
@Slf4j
@Service
public class CognitoIntegrationServiceImpl implements CognitoIntegrationService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 1000L;
    /** Cognito attribute key for roles - must match pool schema (singular, per ADR-001) */
    static final String COGNITO_ROLES_ATTRIBUTE = "custom:role";

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;
    private final String appClientId;

    // Constructor for Spring (with @Value injection)
    public CognitoIntegrationServiceImpl(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${aws.cognito.user-pool-id}") String userPoolId,
            @Value("${aws.cognito.app-client-id}") String appClientId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
        this.appClientId = appClientId;
    }

    @Override
    public void syncUserAttributes(User user) {
        // NO-OP: DB is source of truth for user attributes
        // Cognito is only used for authentication (JWT tokens)
        log.debug("Cognito sync disabled - DB is source of truth for user: {}", user.getUsername());
    }

    @Override
    public String createCognitoUser(GetOrCreateUserRequest request) {
        // NO-OP: Invitation-based flow
        // User will sign up via registration page, Cognito hook will populate cognitoUserId
        log.debug("Cognito user creation disabled - invitation flow for user: {}", request.getEmail());
        return null;  // cognitoUserId will be populated on first login
    }

    /**
     * Story 9.2 Task 2.1: Find Cognito user by email using ListUsers with email filter.
     * Retries up to 3 times with exponential backoff on transient errors.
     */
    @Override
    public Optional<AdminGetUserResponse> findUserByEmail(String email) {
        log.debug("Looking up Cognito user by email: {}", maskEmail(email));

        return executeWithRetry("findUserByEmail", () -> {
            ListUsersRequest request = ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .filter("email = \"" + email + "\"")
                    .limit(1)
                    .build();

            ListUsersResponse response = cognitoClient.listUsers(request);

            if (response.users().isEmpty()) {
                log.debug("No Cognito user found for email: {}", maskEmail(email));
                return Optional.empty();
            }

            String username = response.users().get(0).username();
            log.debug("Found Cognito user {} for email: {}", username, maskEmail(email));

            AdminGetUserResponse userResponse = cognitoClient.adminGetUser(
                    AdminGetUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(username)
                            .build());

            return Optional.of(userResponse);
        });
    }

    /**
     * Story 9.2 Task 2.2: Create new Cognito speaker user with SPEAKER role.
     * Uses AdminCreateUser with SUPPRESS message action (we send our own email with magic link).
     * Sets custom:role=SPEAKER attribute.
     * Retries up to 3 times with exponential backoff.
     */
    @Override
    public String createCognitoSpeaker(String email, String name, String temporaryPassword) {
        log.info("Creating Cognito speaker account for email: {}", maskEmail(email));

        return executeWithRetry("createCognitoSpeaker", () -> {
            AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .temporaryPassword(temporaryPassword)
                    .messageAction(MessageActionType.SUPPRESS) // We send our own email
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("name").value(name).build(),
                            AttributeType.builder().name(COGNITO_ROLES_ATTRIBUTE).value("SPEAKER").build()
                    )
                    .build();

            var response = cognitoClient.adminCreateUser(request);
            String cognitoUsername = response.user().username();
            log.info("Created Cognito speaker account, username: {}", cognitoUsername);
            return cognitoUsername;
        });
    }

    /**
     * Story 9.2 Task 2.3: Add role to existing Cognito user.
     * Reads current custom:role value, appends new role (comma-separated), updates attribute.
     * Idempotent: no-op if role already present.
     * Retries up to 3 times with exponential backoff.
     */
    @Override
    public void addRoleToCognitoUser(String email, Role newRole) {
        log.info("Adding role {} to Cognito user with email: {}", newRole, maskEmail(email));

        executeWithRetry("addRoleToCognitoUser", () -> {
            Optional<AdminGetUserResponse> userOpt = findUserByEmail(email);
            if (userOpt.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot add role: Cognito user not found for email " + maskEmail(email));
            }

            AdminGetUserResponse user = userOpt.get();
            String username = user.username();

            String existingRolesStr = user.userAttributes().stream()
                    .filter(attr -> COGNITO_ROLES_ATTRIBUTE.equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse("");

            List<String> roles = new ArrayList<>(
                    existingRolesStr.isEmpty()
                            ? new ArrayList<>()
                            : Arrays.asList(existingRolesStr.split(",")));

            String newRoleName = newRole.name();
            if (roles.contains(newRoleName)) {
                log.debug("Role {} already present in Cognito for user: {}", newRole, maskEmail(email));
                return null; // Idempotent - no-op
            }

            roles.add(newRoleName);
            String updatedRolesStr = String.join(",", roles);

            cognitoClient.adminUpdateUserAttributes(
                    AdminUpdateUserAttributesRequest.builder()
                            .userPoolId(userPoolId)
                            .username(username)
                            .userAttributes(
                                    AttributeType.builder()
                                            .name(COGNITO_ROLES_ATTRIBUTE)
                                            .value(updatedRolesStr)
                                            .build()
                            )
                            .build()
            );

            log.info("Added role {} to Cognito user: {}, roles now: {}",
                    newRole, maskEmail(email), updatedRolesStr);
            return null;
        });
    }

    /**
     * Story 9.2 Task 4.4: Compensating transaction — delete a Cognito user by username/sub.
     * Best-effort: logs but does NOT throw if the delete fails (already in an error path).
     */
    @Override
    public void deleteCognitoAccount(String cognitoUsername) {
        log.info("Compensating transaction: deleting Cognito account {}", cognitoUsername);
        try {
            cognitoClient.adminDeleteUser(
                    AdminDeleteUserRequest.builder()
                            .userPoolId(userPoolId)
                            .username(cognitoUsername)
                            .build());
            log.info("Compensating transaction: deleted Cognito account {}", cognitoUsername);
        } catch (Exception e) {
            log.error("CRITICAL: Compensating delete failed for Cognito account {}. "
                    + "Account is orphaned. Manual cleanup required.",
                    cognitoUsername, e);
        }
    }

    /**
     * Story 9.3 Task 1.1: Authenticate speaker with email + password via ADMIN_USER_PASSWORD_AUTH.
     * Uses admin-side auth flow — no SRP, server-to-Cognito.
     * Retries up to 3 times with exponential backoff.
     */
    @Override
    public CognitoAuthResult authenticateUser(String email, String password) {
        log.debug("Authenticating Cognito user by email: {}", maskEmail(email));

        return executeWithRetry("authenticateUser", () -> {
            AdminInitiateAuthRequest request = AdminInitiateAuthRequest.builder()
                    .userPoolId(userPoolId)
                    .clientId(appClientId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(Map.of(
                            "USERNAME", email,
                            "PASSWORD", password
                    ))
                    .build();

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(request);
            var result = response.authenticationResult();
            log.debug("Cognito authentication successful for email: {}", maskEmail(email));
            return CognitoAuthResult.builder()
                    .accessToken(result.accessToken())
                    .idToken(result.idToken())
                    .refreshToken(result.refreshToken())
                    .build();
        });
    }

    /**
     * Story 9.3 Task 1.2: Initiate password reset — triggers Cognito ForgotPassword email.
     * Cognito sends the confirmation code to the user's verified email.
     * Retries up to 3 times with exponential backoff.
     */
    @Override
    public void initiatePasswordReset(String email) {
        log.info("Initiating password reset for Cognito user: {}", maskEmail(email));

        executeWithRetry("initiatePasswordReset", () -> {
            ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                    .clientId(appClientId)
                    .username(email)
                    .build();

            cognitoClient.forgotPassword(request);
            log.info("Password reset email dispatched by Cognito for: {}", maskEmail(email));
            return null;
        });
    }

    /**
     * Story 9.3 Task 1.3: Confirm password reset — submit confirmation code + new password.
     * Retries up to 3 times with exponential backoff.
     */
    @Override
    public void confirmPasswordReset(String email, String confirmationCode, String newPassword) {
        log.info("Confirming password reset for Cognito user: {}", maskEmail(email));

        executeWithRetry("confirmPasswordReset", () -> {
            ConfirmForgotPasswordRequest request = ConfirmForgotPasswordRequest.builder()
                    .clientId(appClientId)
                    .username(email)
                    .confirmationCode(confirmationCode)
                    .password(newPassword)
                    .build();

            cognitoClient.confirmForgotPassword(request);
            log.info("Password reset confirmed for Cognito user: {}", maskEmail(email));
            return null;
        });
    }

    /**
     * Executes the given operation with exponential backoff retry.
     * Retry delays: 1s, 2s, 4s (3 attempts total).
     */
    private <T> T executeWithRetry(String operationName, CognitoOperation<T> operation) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.execute();
            } catch (UserNotFoundException e) {
                throw e; // Not retryable
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long delayMs = BASE_RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    log.warn("Cognito {} attempt {}/{} failed, retrying in {}ms: {}",
                            operationName, attempt, MAX_RETRY_ATTEMPTS, delayMs, e.getMessage());
                    sleepUninterruptibly(delayMs);
                } else {
                    log.error("Cognito {} failed after {} attempts: {}",
                            operationName, MAX_RETRY_ATTEMPTS, e.getMessage());
                }
            }
        }

        throw new CognitoOperationException(
                "Cognito " + operationName + " failed after " + MAX_RETRY_ATTEMPTS + " attempts",
                lastException);
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        return email.substring(0, Math.min(3, atIdx)) + "***" + email.substring(atIdx);
    }

    @FunctionalInterface
    interface CognitoOperation<T> {
        T execute() throws Exception;
    }

    /** Unchecked exception for Cognito operation failures after all retries exhausted */
    public static class CognitoOperationException extends RuntimeException {
        public CognitoOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
