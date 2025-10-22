package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
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
 * AC4, AC6: Reconciliation job for drift detection (Cognito → Database ONLY)
 * <p>
 * Purpose (ADR-001: Unidirectional Sync):
 * - Detect orphaned database users (Cognito user deleted → deactivate DB user)
 * - Detect missing database users (Cognito user exists but no DB record → create DB user)
 * - Publish reconciliation metrics
 * <p>
 * IMPORTANT: NO reverse sync (Database → Cognito). Database is source of truth for roles.
 * Roles are synced to JWT at login via PreTokenGeneration Lambda.
 * <p>
 * Schedule: Daily at 2:00 AM (cron: "0 0 2 * * *")
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserReconciliationService {

    private final UserRepository userRepository;
    private final CognitoIdentityProviderClient cognitoClient;
    private final UserSyncMetricsService metricsService;
    private final String userPoolId; // Injected from configuration

    private static final int PAGE_SIZE = 60; // Cognito max

    /**
     * Scheduled reconciliation job
     * Runs daily at 2:00 AM
     * <p>
     * ADR-001: Unidirectional sync (Cognito → Database only)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void reconcileUsers() {
        log.info("Starting user reconciliation job (Cognito → DB only, per ADR-001)");
        Instant startTime = Instant.now();

        ReconciliationReport report = new ReconciliationReport();

        try {
            // 1. Reconcile orphaned DB users (Cognito user deleted)
            reconcileOrphanedDbUsers(report);

            // 2. Reconcile missing DB users (Cognito user exists but no DB record)
            reconcileMissingDbUsers(report);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            // Publish metrics
            metricsService.recordReconciliationJob(
                    report.getOrphanedUsers(),
                    report.getMissingUsers(),
                    0, // No role mismatches (DB is source of truth)
                    0, // No compensation retries (unidirectional sync)
                    durationMs
            );

            log.info("User reconciliation job completed",
                    Map("durationMs", durationMs,
                            "orphanedUsers", report.getOrphanedUsers(),
                            "missingUsers", report.getMissingUsers(),
                            "errors", report.getErrors()));

        } catch (Exception e) {
            log.error("User reconciliation job failed", e);
            report.addError("Reconciliation failed: " + e.getMessage());
        }
    }

    /**
     * Reconcile orphaned DB users (Cognito user deleted)
     * AC6: Deactivate DB users when Cognito user deleted
     * <p>
     * ADR-001: Cognito → Database (detect deletions)
     */
    private void reconcileOrphanedDbUsers(ReconciliationReport report) {
        log.info("Reconciling orphaned database users (Cognito → DB)");

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
                user.setDeactivationReason("Cognito user deleted");
                userRepository.save(user);
                report.incrementOrphanedUsers();

                metricsService.recordDriftDetected(1);

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
     * <p>
     * ADR-001: Cognito → Database (detect missing users)
     */
    private void reconcileMissingDbUsers(ReconciliationReport report) {
        log.info("Reconciling missing database users (Cognito → DB)");

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

                        metricsService.recordUserCreated("RECONCILIATION");
                        metricsService.recordDriftDetected(1);
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
     * <p>
     * ADR-001: Cognito → Database sync
     * Roles will be synced to JWT at next login via PreTokenGeneration Lambda
     */
    @Transactional
    private void createMissingUser(UserType cognitoUser) {
        String cognitoId = cognitoUser.username();
        String email = extractAttribute(cognitoUser, "email");
        String firstName = extractAttribute(cognitoUser, "given_name");
        String lastName = extractAttribute(cognitoUser, "family_name");

        // Generate username from email
        String username = generateUsername(email);

        // Assign default ATTENDEE role (per ADR-001: database is source of truth)
        Set<Role> roles = Set.of(Role.ATTENDEE);

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

        log.info("Missing user created (roles will sync to JWT at next login)",
                Map("cognitoId", cognitoId, "username", username, "roles", roles));
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
        private List<String> errors = new ArrayList<>();

        public void incrementOrphanedUsers() {
            orphanedUsers++;
        }

        public void incrementMissingUsers() {
            missingUsers++;
        }

        public void addError(String error) {
            errors.add(error);
        }
    }
}
