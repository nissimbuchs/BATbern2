package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
@Slf4j
public class UserReconciliationService {

    private final UserRepository userRepository;
    private final CognitoIdentityProviderClient cognitoClient;
    private final UserSyncMetricsService metricsService;
    private final String userPoolId;

    public UserReconciliationService(
            UserRepository userRepository,
            CognitoIdentityProviderClient cognitoClient,
            UserSyncMetricsService metricsService,
            @Value("${aws.cognito.user-pool-id}") String userPoolId) {
        this.userRepository = userRepository;
        this.cognitoClient = cognitoClient;
        this.metricsService = metricsService;
        this.userPoolId = userPoolId;
    }

    private static final int PAGE_SIZE = 60; // Cognito max

    /**
     * Scheduled reconciliation job
     * Runs daily at 2:00 AM
     * <p>
     * ADR-001: Unidirectional sync (Cognito → Database only)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void reconcileUsersScheduled() {
        reconcileUsers();
    }

    /**
     * Manual reconciliation trigger
     * Can be called from admin API for on-demand sync
     * <p>
     * ADR-001: Unidirectional sync (Cognito → Database only)
     *
     * @return ReconciliationReport with sync statistics
     */
    @Transactional
    public ReconciliationReport reconcileUsers() {
        log.info("Starting user reconciliation (Cognito → DB only, per ADR-001)");
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

            log.info("User reconciliation completed",
                    mapOf("durationMs", durationMs,
                            "orphanedUsers", report.getOrphanedUsers(),
                            "missingUsers", report.getMissingUsers(),
                            "errors", report.getErrors()));

            report.setDurationMs(durationMs);

        } catch (Exception e) {
            log.error("User reconciliation failed", e);
            report.addError("Reconciliation failed: " + e.getMessage());
        }

        return report;
    }

    /**
     * Check sync status between Cognito and Database
     * <p>
     * ADR-001: Cognito → Database sync check
     *
     * @return SyncStatus with comparison statistics
     */
    public SyncStatus checkSyncStatus() {
        log.info("Checking sync status (Cognito vs Database)");

        SyncStatus status = new SyncStatus();

        try {
            // Count Cognito users
            ListUsersRequest request = ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .limit(PAGE_SIZE)
                    .build();

            ListUsersIterable paginator = cognitoClient.listUsersPaginator(request);
            Set<String> cognitoUserIds = new HashSet<>();

            for (ListUsersResponse page : paginator) {
                for (UserType cognitoUser : page.users()) {
                    cognitoUserIds.add(cognitoUser.username());
                }
            }

            status.setCognitoUserCount(cognitoUserIds.size());

            // Count DB users
            List<User> dbUsers = userRepository.findByIsActive(true);
            status.setDatabaseUserCount(dbUsers.size());

            // Find missing in DB
            List<String> missingCognitoIds = new ArrayList<>();
            for (String cognitoId : cognitoUserIds) {
                if (userRepository.findByCognitoUserId(cognitoId).isEmpty()) {
                    missingCognitoIds.add(cognitoId);
                }
            }
            status.setMissingInDatabase(missingCognitoIds.size());
            status.setMissingCognitoIds(missingCognitoIds);

            // Find orphaned in DB
            int orphaned = 0;
            for (User user : dbUsers) {
                if (user.getCognitoUserId() != null
                        && !cognitoUserIds.contains(user.getCognitoUserId())) {
                    orphaned++;
                }
            }
            status.setOrphanedInDatabase(orphaned);

            log.info("Sync status check completed",
                    mapOf("cognitoUsers", cognitoUserIds.size(),
                            "dbUsers", dbUsers.size(),
                            "missing", missingCognitoIds.size(),
                            "orphaned", orphaned));

        } catch (Exception e) {
            log.error("Failed to check sync status", e);
            status.setMessage("Sync status check failed: " + e.getMessage());
        }

        return status;
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
                        mapOf("userId", user.getId(), "cognitoId", user.getCognitoUserId()));

                user.setActive(false);
                user.setDeactivationReason("Cognito user deleted");
                userRepository.save(user);
                report.incrementOrphanedUsers();

                metricsService.recordDriftDetected(1);

            } catch (Exception e) {
                log.error("Failed to check Cognito user",
                        mapOf("userId", user.getId(), "cognitoId", user.getCognitoUserId(), "error", e.getMessage()));
                report.addError("Orphaned user check failed: " + e.getMessage());
            }
        }

        log.info("Orphaned users reconciliation completed",
                mapOf("deactivated", report.getOrphanedUsers()));
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
                                mapOf("cognitoId", cognitoId));

                        createMissingUser(cognitoUser);
                        report.incrementMissingUsers();

                        metricsService.recordUserCreated("RECONCILIATION");
                        metricsService.recordDriftDetected(1);
                    }
                }
            }

            log.info("Missing users reconciliation completed",
                    mapOf("created", report.getMissingUsers()));

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

        // Generate username from first/last name or email (firstname.lastname format required)
        String username = generateUsername(firstName, lastName, email);

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
                mapOf("cognitoId", cognitoId, "username", username, "roles", roles));
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
     * Generate username from first name, last name, or email
     * <p>
     * Format: firstname.lastname (lowercase, required by chk_username_format constraint)
     * Example: John Doe -> john.doe
     * Example: John Doe (duplicate) -> john.doe.2
     * Example: e2e-test@batbern.ch (no names) -> user.e2etest
     *
     * @param firstName User first name from Cognito (given_name)
     * @param lastName  User last name from Cognito (family_name)
     * @param email     User email (fallback if names not available)
     * @return Generated username matching pattern ^[a-z]+\.[a-z]+(\.[0-9]+)?$
     */
    private String generateUsername(String firstName, String lastName, String email) {
        String username;

        // Prefer first.last name if both available
        if (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty()) {
            username = firstName.toLowerCase().replaceAll("[^a-z]", "") + "." +
                       lastName.toLowerCase().replaceAll("[^a-z]", "");
        }
        // Fall back to email local part if it contains a dot
        else if (email != null && !email.isEmpty()) {
            String emailLocal = email.split("@")[0].toLowerCase().replaceAll("[^a-z.]", "");
            if (emailLocal.contains(".")) {
                username = emailLocal;
            } else {
                // Email doesn't contain dot, prepend "user."
                username = "user." + emailLocal;
            }
        }
        // Last resort: generate timestamp-based username
        else {
            username = "user." + System.currentTimeMillis();
        }

        // Ensure username matches required pattern
        if (!username.matches("^[a-z]+\\.[a-z]+(\\.[0-9]+)?$")) {
            log.warn("Generated username '{}' doesn't match pattern, using fallback", username);
            username = "user.unknown";
        }

        // Check if username exists, add numeric suffix if needed
        String finalUsername = username;
        int suffix = 2;
        while (userRepository.existsByUsername(finalUsername)) {
            finalUsername = username + "." + suffix;
            suffix++;
        }

        return finalUsername;
    }

    // Map helper
    private <K, V> java.util.Map<K, V> mapOf(Object... entries) {
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
        private long durationMs = 0;
        private List<String> errors = new ArrayList<>();

        public void incrementOrphanedUsers() {
            orphanedUsers++;
        }

        public void incrementMissingUsers() {
            missingUsers++;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public void addError(String error) {
            errors.add(error);
        }
    }

    /**
     * Sync Status
     * Comparison between Cognito and Database users
     */
    @Getter
    public static class SyncStatus {
        private int cognitoUserCount = 0;
        private int databaseUserCount = 0;
        private int missingInDatabase = 0;
        private int orphanedInDatabase = 0;
        private List<String> missingCognitoIds = new ArrayList<>();
        private String message;

        public void setCognitoUserCount(int count) {
            this.cognitoUserCount = count;
        }

        public void setDatabaseUserCount(int count) {
            this.databaseUserCount = count;
        }

        public void setMissingInDatabase(int count) {
            this.missingInDatabase = count;
        }

        public void setOrphanedInDatabase(int count) {
            this.orphanedInDatabase = count;
        }

        public void setMissingCognitoIds(List<String> ids) {
            this.missingCognitoIds = ids;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isInSync() {
            return missingInDatabase == 0 && orphanedInDatabase == 0;
        }
    }
}
