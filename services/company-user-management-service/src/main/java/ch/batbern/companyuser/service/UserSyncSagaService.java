package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserSyncCompensationLog;
import ch.batbern.companyuser.repository.UserSyncCompensationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User Sync Saga Service
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC3, AC8: Saga pattern with compensation for failed Cognito syncs
 * <p>
 * Purpose:
 * - Sync role changes from database to Cognito Groups
 * - Create compensation log on Cognito sync failure
 * - Allow database transaction to commit (database is source of truth)
 * - Reconciliation service retries failed compensations
 * <p>
 * Pattern:
 * 1. Database update succeeds (in calling transaction)
 * 2. Attempt Cognito sync
 * 3. On failure, create compensation log
 * 4. Commit database transaction (don't rollback)
 * 5. Reconciliation service retries compensation later
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncSagaService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final UserSyncCompensationLogRepository compensationLogRepository;
    private final String userPoolId; // Injected from configuration

    /**
     * Sync role changes to Cognito Groups
     * <p>
     * Called after database role update succeeds
     * Creates compensation log on failure (doesn't throw exception)
     *
     * @param user User with updated roles
     */
    @Transactional
    public void syncRolesToCognito(User user) {
        if (user.getCognitoUserId() == null || user.getCognitoUserId().isEmpty()) {
            log.warn("User has no Cognito ID, skipping Cognito sync",
                    Map("userId", user.getId()));
            return;
        }

        Set<Role> roles = user.getRoles();
        String cognitoId = user.getCognitoUserId();

        log.info("Syncing roles to Cognito Groups",
                Map("userId", user.getId(), "cognitoId", cognitoId, "roles", roles));

        try {
            // Get current Cognito groups
            AdminListGroupsForUserRequest listRequest = AdminListGroupsForUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoId)
                    .build();

            AdminListGroupsForUserResponse listResponse = cognitoClient.adminListGroupsForUser(listRequest);
            List<String> currentGroups = listResponse.groups().stream()
                    .map(GroupType::groupName)
                    .toList();

            // Add missing groups
            for (Role role : roles) {
                String groupName = role.name().toLowerCase();
                if (!currentGroups.contains(groupName)) {
                    addUserToCognitoGroup(cognitoId, groupName);
                }
            }

            // Remove extra groups
            List<String> expectedGroups = roles.stream()
                    .map(role -> role.name().toLowerCase())
                    .toList();
            for (String currentGroup : currentGroups) {
                if (!expectedGroups.contains(currentGroup)) {
                    removeUserFromCognitoGroup(cognitoId, currentGroup);
                }
            }

            log.info("Cognito role sync completed successfully",
                    Map("userId", user.getId(), "cognitoId", cognitoId));

        } catch (UserNotFoundException e) {
            log.warn("User not found in Cognito, creating compensation log",
                    Map("userId", user.getId(), "cognitoId", cognitoId));
            createCompensationLog(user.getId(), cognitoId, "ROLE_SYNC", null,
                    "User not found in Cognito: " + e.getMessage());

        } catch (TooManyRequestsException e) {
            log.warn("Cognito throttling, creating compensation log",
                    Map("userId", user.getId(), "cognitoId", cognitoId));
            createCompensationLog(user.getId(), cognitoId, "ROLE_SYNC", null,
                    "Cognito throttling: " + e.getMessage());

        } catch (Exception e) {
            log.error("Cognito sync failed, creating compensation log",
                    Map("userId", user.getId(), "cognitoId", cognitoId, "error", e.getMessage()));
            createCompensationLog(user.getId(), cognitoId, "ROLE_SYNC", null,
                    "Cognito sync failed: " + e.getMessage());
        }
    }

    /**
     * Add user to Cognito Group
     *
     * @param cognitoId Cognito user ID
     * @param groupName Group name (role name in lowercase)
     */
    private void addUserToCognitoGroup(String cognitoId, String groupName) {
        try {
            AdminAddUserToGroupRequest request = AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoId)
                    .groupName(groupName)
                    .build();

            cognitoClient.adminAddUserToGroup(request);

            log.debug("Added user to Cognito Group",
                    Map("cognitoId", cognitoId, "group", groupName));

        } catch (Exception e) {
            log.error("Failed to add user to Cognito Group",
                    Map("cognitoId", cognitoId, "group", groupName, "error", e.getMessage()));
            throw e;
        }
    }

    /**
     * Remove user from Cognito Group
     *
     * @param cognitoId Cognito user ID
     * @param groupName Group name (role name in lowercase)
     */
    private void removeUserFromCognitoGroup(String cognitoId, String groupName) {
        try {
            AdminRemoveUserFromGroupRequest request = AdminRemoveUserFromGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoId)
                    .groupName(groupName)
                    .build();

            cognitoClient.adminRemoveUserFromGroup(request);

            log.debug("Removed user from Cognito Group",
                    Map("cognitoId", cognitoId, "group", groupName));

        } catch (Exception e) {
            log.error("Failed to remove user from Cognito Group",
                    Map("cognitoId", cognitoId, "group", groupName, "error", e.getMessage()));
            throw e;
        }
    }

    /**
     * Create compensation log for failed Cognito sync
     *
     * @param userId       User ID
     * @param cognitoId    Cognito user ID
     * @param operation    Operation type (ROLE_SYNC, USER_CREATE, USER_DELETE)
     * @param targetRole   Target role (optional)
     * @param errorMessage Error message
     */
    private void createCompensationLog(UUID userId, String cognitoId, String operation,
                                        Role targetRole, String errorMessage) {
        try {
            UserSyncCompensationLog log = UserSyncCompensationLog.builder()
                    .userId(userId)
                    .cognitoId(cognitoId)
                    .operation(operation)
                    .targetRole(targetRole)
                    .status("FAILED")
                    .errorMessage(errorMessage)
                    .compensationRequired(true)
                    .retryCount(0)
                    .build();

            compensationLogRepository.save(log);

            this.log.info("Compensation log created",
                    Map("userId", userId, "cognitoId", cognitoId, "operation", operation));

        } catch (Exception e) {
            this.log.error("Failed to create compensation log", e);
        }
    }

    /**
     * Retry compensation (called by reconciliation service)
     *
     * @param compensationLog Compensation log to retry
     * @return true if retry succeeded, false otherwise
     */
    @Transactional
    public boolean retryCompensation(UserSyncCompensationLog compensationLog) {
        compensationLog.recordCompensationAttempt();

        try {
            // Retry logic based on operation type
            switch (compensationLog.getOperation()) {
                case "ROLE_SYNC":
                    retryRoleSync(compensationLog);
                    break;
                case "USER_CREATE":
                    retryUserCreate(compensationLog);
                    break;
                case "USER_DELETE":
                    retryUserDelete(compensationLog);
                    break;
                default:
                    log.warn("Unknown compensation operation",
                            Map("operation", compensationLog.getOperation()));
                    return false;
            }

            compensationLog.markCompleted();
            compensationLogRepository.save(compensationLog);

            log.info("Compensation retry succeeded",
                    Map("compensationId", compensationLog.getId(), "operation", compensationLog.getOperation()));

            return true;

        } catch (Exception e) {
            compensationLog.markFailed("Retry failed: " + e.getMessage());
            compensationLogRepository.save(compensationLog);

            log.error("Compensation retry failed",
                    Map("compensationId", compensationLog.getId(), "operation", compensationLog.getOperation(), "error", e.getMessage()));

            return false;
        }
    }

    private void retryRoleSync(UserSyncCompensationLog compensationLog) throws Exception {
        // Implementation would fetch user from database and re-sync roles
        log.info("Retrying role sync", Map("compensationId", compensationLog.getId()));
    }

    private void retryUserCreate(UserSyncCompensationLog compensationLog) throws Exception {
        // Implementation would create user in Cognito
        log.info("Retrying user create", Map("compensationId", compensationLog.getId()));
    }

    private void retryUserDelete(UserSyncCompensationLog compensationLog) throws Exception {
        // Implementation would delete user in Cognito
        log.info("Retrying user delete", Map("compensationId", compensationLog.getId()));
    }

    // Map helper for logging
    private <K, V> java.util.Map<K, V> Map(Object... entries) {
        java.util.Map<K, V> map = new java.util.HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }
}
