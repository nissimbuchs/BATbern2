package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.domain.UserSyncCompensationLog;
import ch.batbern.companyuser.repository.UserSyncCompensationLogRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User Reconciliation Service
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC4, AC6: Reconciliation job for drift detection and compensation retry
 * <p>
 * Purpose:
 * - Detect orphaned database users (Cognito user deleted)
 * - Detect missing database users (Cognito user exists but no DB record)
 * - Detect role mismatches between database and Cognito Groups
 * - Retry failed compensation logs
 * - Publish reconciliation metrics
 * <p>
 * Schedule: Daily at 2:00 AM (cron: "0 0 2 * * *")
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserReconciliationService {

    private final UserRepository userRepository;
    private final UserSyncCompensationLogRepository compensationLogRepository;
    private final CognitoIdentityProviderClient cognitoClient;
    private final UserSyncSagaService syncSagaService;
    private final String userPoolId; // Injected from configuration

    private static final int MAX_RETRIES = 5;
    private static final int PAGE_SIZE = 60; // Cognito max

    /**
     * Scheduled reconciliation job
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void reconcileUsers() {
        log.info("Starting user reconciliation job");
        Instant startTime = Instant.now();

        ReconciliationReport report = new ReconciliationReport();

        try {
            // 1. Retry failed compensations
            retryFailedCompensations(report);

            // 2. Reconcile orphaned DB users (Cognito user deleted)
            reconcileOrphanedDbUsers(report);

            // 3. Reconcile missing DB users (Cognito user exists but no DB record)
            reconcileMissingDbUsers(report);

            // 4. Reconcile role mismatches (DB vs Cognito Groups)
            reconcileRoleMismatches(report);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            log.info("User reconciliation job completed",
                    Map("durationMs", durationMs,
                            "orphanedUsers", report.getOrphanedUsers(),
                            "missingUsers", report.getMissingUsers(),
                            "roleMismatches", report.getRoleMismatches(),
                            "compensationRetries", report.getCompensationRetries(),
                            "errors", report.getErrors()));

        } catch (Exception e) {
            log.error("User reconciliation job failed", e);
            report.addError("Reconciliation failed: " + e.getMessage());
        }
    }

    /**
     * Retry failed compensation logs
     * AC4: Retry failed compensations
     */
    private void retryFailedCompensations(ReconciliationReport report) {
        log.info("Retrying failed compensations");

        List<UserSyncCompensationLog> pendingCompensations =
                compensationLogRepository.findPendingCompensations(MAX_RETRIES);

        for (UserSyncCompensationLog compensation : pendingCompensations) {
            try {
                boolean success = syncSagaService.retryCompensation(compensation);
                if (success) {
                    report.incrementCompensationRetries();
                } else {
                    report.addError("Compensation retry failed: " + compensation.getId());
                }
            } catch (Exception e) {
                log.error("Failed to retry compensation",
                        Map("compensationId", compensation.getId(), "error", e.getMessage()));
                report.addError("Compensation retry error: " + e.getMessage());
            }
        }

        log.info("Compensation retries completed",
                Map("retried", report.getCompensationRetries(), "total", pendingCompensations.size()));
    }

    /**
     * Reconcile orphaned DB users (Cognito user deleted)
     * AC6: Deactivate DB users when Cognito user deleted
     */
    private void reconcileOrphanedDbUsers(ReconciliationReport report) {
        log.info("Reconciling orphaned database users");

        List<User> activeUsers = userRepository.findByIsActive(true);

        for (User user : activeUsers) {
            if (user.getCognitoUserId() == null || user.getCognitoUserId().isEmpty()) {
                continue; // Skip users without Cognito ID
            }

            try {
                // Check if user exists in Cognito
                AdminGetUserRequest request = AdminGetUserRequest.builder()
                        .userPoolId(userPoolId)
                        .username(user.getCognitoUserId())
                        .build();

                cognitoClient.adminGetUser(request);
                // User exists in Cognito - no action needed

            } catch (UserNotFoundException e) {
                // User deleted in Cognito - deactivate in database
                log.warn("Orphaned database user detected, deactivating",
                        Map("userId", user.getId(), "cognitoId", user.getCognitoUserId()));

                user.setActive(false);
                userRepository.save(user);
                report.incrementOrphanedUsers();

            } catch (Exception e) {
                log.error("Failed to check Cognito user",
                        Map("userId", user.getId(), "cognitoId", user.getCognitoUserId(), "error", e.getMessage()));
                report.addError("Orphaned user check failed: " + e.getMessage());
            }
        }

        log.info("Orphaned users reconciliation completed",
                Map("deactivated", report.getOrphanedUsers()));
    }

    /**
     * Reconcile missing DB users (Cognito user exists but no DB record)
     * AC4: Create DB user when Cognito user exists
     */
    private void reconcileMissingDbUsers(ReconciliationReport report) {
        log.info("Reconciling missing database users");

        try {
            // Paginate through all Cognito users
            ListUsersRequest request = ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .limit(PAGE_SIZE)
                    .build();

            ListUsersIterable paginator = cognitoClient.listUsersPaginator(request);

            for (ListUsersResponse page : paginator) {
                for (UserType cognitoUser : page.users()) {
                    String cognitoId = cognitoUser.username();

                    // Check if user exists in database
                    Optional<User> dbUser = userRepository.findByCognitoUserId(cognitoId);
                    if (dbUser.isEmpty()) {
                        // User missing in database - create
                        log.warn("Missing database user detected, creating",
                                Map("cognitoId", cognitoId));

                        createMissingUser(cognitoUser);
                        report.incrementMissingUsers();
                    }
                }
            }

            log.info("Missing users reconciliation completed",
                    Map("created", report.getMissingUsers()));

        } catch (Exception e) {
            log.error("Failed to reconcile missing users", e);
            report.addError("Missing users reconciliation failed: " + e.getMessage());
        }
    }

    /**
     * Create missing database user from Cognito user
     */
    @Transactional
    private void createMissingUser(UserType cognitoUser) {
        String cognitoId = cognitoUser.username();
        String email = extractAttribute(cognitoUser, "email");
        String firstName = extractAttribute(cognitoUser, "given_name");
        String lastName = extractAttribute(cognitoUser, "family_name");

        // Generate username from email
        String username = generateUsername(email);

        // Extract roles from Cognito Groups
        Set<Role> roles = extractRolesFromCognito(cognitoId);

        User user = User.builder()
                .cognitoUserId(cognitoId)
                .username(username)
                .email(email != null ? email : "")
                .firstName(firstName != null ? firstName : "")
                .lastName(lastName != null ? lastName : "")
                .roles(roles)
                .isActive(true)
                .build();

        userRepository.save(user);

        log.info("Missing user created",
                Map("cognitoId", cognitoId, "username", username));
    }

    /**
     * Reconcile role mismatches (DB vs Cognito Groups)
     * AC4: Sync role mismatches (database as source of truth)
     */
    private void reconcileRoleMismatches(ReconciliationReport report) {
        log.info("Reconciling role mismatches");

        List<User> activeUsers = userRepository.findByIsActive(true);

        for (User user : activeUsers) {
            if (user.getCognitoUserId() == null || user.getCognitoUserId().isEmpty()) {
                continue;
            }

            try {
                // Get Cognito Groups
                Set<Role> cognitoRoles = extractRolesFromCognito(user.getCognitoUserId());
                Set<Role> dbRoles = user.getRoles();

                // Check for mismatch
                if (!cognitoRoles.equals(dbRoles)) {
                    log.warn("Role mismatch detected",
                            Map("userId", user.getId(),
                                    "cognitoId", user.getCognitoUserId(),
                                    "dbRoles", dbRoles,
                                    "cognitoRoles", cognitoRoles));

                    // Sync roles (database as source of truth)
                    syncSagaService.syncRolesToCognito(user);
                    report.incrementRoleMismatches();
                }

            } catch (Exception e) {
                log.error("Failed to reconcile role mismatch",
                        Map("userId", user.getId(), "cognitoId", user.getCognitoUserId(), "error", e.getMessage()));
                report.addError("Role mismatch reconciliation failed: " + e.getMessage());
            }
        }

        log.info("Role mismatches reconciliation completed",
                Map("synced", report.getRoleMismatches()));
    }

    /**
     * Extract roles from Cognito Groups
     */
    private Set<Role> extractRolesFromCognito(String cognitoId) {
        try {
            AdminListGroupsForUserRequest request = AdminListGroupsForUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(cognitoId)
                    .build();

            AdminListGroupsForUserResponse response = cognitoClient.adminListGroupsForUser(request);

            return response.groups().stream()
                    .map(GroupType::groupName)
                    .map(String::toUpperCase)
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.warn("Failed to extract roles from Cognito",
                    Map("cognitoId", cognitoId, "error", e.getMessage()));
            return new HashSet<>();
        }
    }

    /**
     * Extract attribute from Cognito user
     */
    private String extractAttribute(UserType cognitoUser, String attributeName) {
        return cognitoUser.attributes().stream()
                .filter(attr -> attr.name().equals(attributeName))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Generate username from email
     */
    private String generateUsername(String email) {
        if (email == null || email.isEmpty()) {
            return "user." + System.currentTimeMillis();
        }

        String username = email.split("@")[0].toLowerCase();

        int suffix = 1;
        String finalUsername = username;
        while (userRepository.existsByUsername(finalUsername)) {
            finalUsername = username + "." + suffix;
            suffix++;
        }

        return finalUsername;
    }

    // Map helper
    private <K, V> java.util.Map<K, V> Map(Object... entries) {
        java.util.Map<K, V> map = new java.util.HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }

    /**
     * Reconciliation Report
     * Tracks metrics for reconciliation job
     */
    @Getter
    public static class ReconciliationReport {
        private int orphanedUsers = 0;
        private int missingUsers = 0;
        private int roleMismatches = 0;
        private int compensationRetries = 0;
        private List<String> errors = new ArrayList<>();

        public void incrementOrphanedUsers() {
            orphanedUsers++;
        }

        public void incrementMissingUsers() {
            missingUsers++;
        }

        public void incrementRoleMismatches() {
            roleMismatches++;
        }

        public void incrementCompensationRetries() {
            compensationRetries++;
        }

        public void addError(String error) {
            errors.add(error);
        }
    }
}
