package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for UserReconciliationService
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC4, AC6: Reconciliation job for drift detection (Cognito → Database ONLY)
 * <p>
 * Test Coverage:
 * - Main integration tests (full reconcileUsers flow)
 * - Sync status tests (checkSyncStatus)
 * - Unit tests for helper methods
 * - Report and inner class tests
 */
@ExtendWith(MockitoExtension.class)
class UserReconciliationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private UserSyncMetricsService metricsService;

    private UserReconciliationService reconciliationService;

    private static final String USER_POOL_ID = "us-east-1_TestPool";

    @BeforeEach
    void setUp() {
        reconciliationService = new UserReconciliationService(
                userRepository,
                cognitoClient,
                metricsService,
                USER_POOL_ID
        );
    }

    // =============================
    // Main Integration Tests
    // =============================

    /**
     * Test 1: should_reconcileSuccessfully_when_allInSync
     * AC: All users are in sync, no changes needed
     */
    @Test
    void should_reconcileSuccessfully_when_allInSync() {
        // Given: All DB users exist in Cognito
        User user1 = createUser("user1", "user1@example.com", "John", "Doe");
        User user2 = createUser("user2", "user2@example.com", "Jane", "Smith");
        List<User> activeUsers = List.of(user1, user2);

        when(userRepository.findByIsActive(true)).thenReturn(activeUsers);

        // Mock Cognito responses for orphan check (users exist)
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenReturn(AdminGetUserResponse.builder().build());

        // Mock Cognito list users response (same users, no missing)
        ListUsersIterable paginator = mockListUsersPaginator(
                createCognitoUser("user1", "user1@example.com", "John", "Doe"),
                createCognitoUser("user2", "user2@example.com", "Jane", "Smith")
        );
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);
        when(userRepository.findByCognitoUserId("user1")).thenReturn(Optional.of(user1));
        when(userRepository.findByCognitoUserId("user2")).thenReturn(Optional.of(user2));

        // When
        UserReconciliationService.ReconciliationReport report = reconciliationService.reconcileUsers();

        // Then: No changes
        assertThat(report.getOrphanedUsers()).isEqualTo(0);
        assertThat(report.getMissingUsers()).isEqualTo(0);
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getDurationMs()).isGreaterThan(0);

        verify(userRepository, never()).save(any(User.class));
        verify(metricsService).recordReconciliationJob(0, 0, 0, 0, report.getDurationMs());
    }

    /**
     * Test 2: should_createMissingUsers_when_usersInCognitoNotInDb
     * AC4: Create DB user when Cognito user exists but no DB record
     */
    @Test
    void should_createMissingUsers_when_usersInCognitoNotInDb() {
        // Given: No active DB users (orphan check passes)
        when(userRepository.findByIsActive(true)).thenReturn(List.of());

        // Cognito has 2 users
        UserType cognitoUser1 = createCognitoUser("cognito1", "newuser1@example.com", "Alice", "Johnson");
        UserType cognitoUser2 = createCognitoUser("cognito2", "newuser2@example.com", "Bob", "Williams");

        ListUsersIterable paginator = mockListUsersPaginator(cognitoUser1, cognitoUser2);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        // DB doesn't have these users
        when(userRepository.findByCognitoUserId("cognito1")).thenReturn(Optional.empty());
        when(userRepository.findByCognitoUserId("cognito2")).thenReturn(Optional.empty());

        // Mock username generation (username is extracted from email prefix)
        when(userRepository.existsByUsername("newuser1")).thenReturn(false);
        when(userRepository.existsByUsername("newuser2")).thenReturn(false);

        // When
        UserReconciliationService.ReconciliationReport report = reconciliationService.reconcileUsers();

        // Then: 2 users created
        assertThat(report.getMissingUsers()).isEqualTo(2);
        assertThat(report.getOrphanedUsers()).isEqualTo(0);
        assertThat(report.getErrors()).isEmpty();

        verify(userRepository, times(2)).save(argThat(user ->
                user.getCognitoUserId() != null
                        && user.getRoles().contains(Role.ATTENDEE)
                        && user.isActive()
        ));
        verify(metricsService, times(2)).recordUserCreated("RECONCILIATION");
        verify(metricsService, times(2)).recordDriftDetected(1);
    }

    /**
     * Test 3: should_deactivateOrphanedUsers_when_usersInDbNotInCognito
     * AC6: Deactivate DB users when Cognito user deleted
     */
    @Test
    void should_deactivateOrphanedUsers_when_usersInDbNotInCognito() {
        // Given: DB has 2 active users
        User user1 = createUser("orphan1", "orphan1@example.com", "Orphan", "One");
        User user2 = createUser("orphan2", "orphan2@example.com", "Orphan", "Two");
        List<User> activeUsers = List.of(user1, user2);

        when(userRepository.findByIsActive(true)).thenReturn(activeUsers);

        // Cognito throws UserNotFoundException (users deleted)
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found").build());

        // Mock empty Cognito list (no users)
        ListUsersIterable paginator = mockListUsersPaginator();
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        // When
        UserReconciliationService.ReconciliationReport report = reconciliationService.reconcileUsers();

        // Then: 2 users deactivated
        assertThat(report.getOrphanedUsers()).isEqualTo(2);
        assertThat(report.getMissingUsers()).isEqualTo(0);
        assertThat(report.getErrors()).isEmpty();

        verify(userRepository, times(2)).save(argThat(user ->
                !user.isActive()
                        && "Cognito user deleted".equals(user.getDeactivationReason())
        ));
        verify(metricsService, times(2)).recordDriftDetected(1);
    }

    /**
     * Test 4: should_performBothOperations_when_outOfSync
     * AC: Mixed scenario - both create missing AND deactivate orphaned
     */
    @Test
    void should_performBothOperations_when_outOfSync() {
        // Given: DB has 1 orphaned user
        User orphanedUser = createUser("orphaned", "orphaned@example.com", "Orphaned", "User");
        when(userRepository.findByIsActive(true)).thenReturn(List.of(orphanedUser));

        // Orphaned user not in Cognito
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found").build());

        // Cognito has 1 new user
        UserType newCognitoUser = createCognitoUser("new-cognito", "new@example.com", "New", "User");
        ListUsersIterable paginator = mockListUsersPaginator(newCognitoUser);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        when(userRepository.findByCognitoUserId("new-cognito")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("new")).thenReturn(false);

        // When
        UserReconciliationService.ReconciliationReport report = reconciliationService.reconcileUsers();

        // Then: 1 deactivated, 1 created
        assertThat(report.getOrphanedUsers()).isEqualTo(1);
        assertThat(report.getMissingUsers()).isEqualTo(1);
        assertThat(report.getErrors()).isEmpty();

        verify(userRepository, times(2)).save(any(User.class));
        verify(metricsService, times(1)).recordUserCreated("RECONCILIATION");
        verify(metricsService, times(2)).recordDriftDetected(1);
    }

    /**
     * Test 5: should_handleErrors_when_reconciliationFails
     * AC: Error handling during reconciliation
     */
    @Test
    void should_handleErrors_when_reconciliationFails() {
        // Given: Repository throws exception
        when(userRepository.findByIsActive(true))
                .thenThrow(new RuntimeException("Database connection error"));

        // When
        UserReconciliationService.ReconciliationReport report = reconciliationService.reconcileUsers();

        // Then: Error recorded
        assertThat(report.getErrors()).hasSize(1);
        assertThat(report.getErrors().get(0)).contains("Reconciliation failed");
        assertThat(report.getErrors().get(0)).contains("Database connection error");
    }

    // =============================
    // Sync Status Tests
    // =============================

    /**
     * Test 6: should_returnInSync_when_cognitoAndDbMatch
     * AC: checkSyncStatus() - all in sync
     */
    @Test
    void should_returnInSync_when_cognitoAndDbMatch() {
        // Given: Cognito has 2 users
        UserType cognitoUser1 = createCognitoUser("user1", "user1@example.com", "John", "Doe");
        UserType cognitoUser2 = createCognitoUser("user2", "user2@example.com", "Jane", "Smith");

        ListUsersIterable paginator = mockListUsersPaginator(cognitoUser1, cognitoUser2);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        // DB has same users
        User dbUser1 = createUser("user1", "user1@example.com", "John", "Doe");
        User dbUser2 = createUser("user2", "user2@example.com", "Jane", "Smith");
        when(userRepository.findByIsActive(true)).thenReturn(List.of(dbUser1, dbUser2));
        when(userRepository.findByCognitoUserId("user1")).thenReturn(Optional.of(dbUser1));
        when(userRepository.findByCognitoUserId("user2")).thenReturn(Optional.of(dbUser2));

        // When
        UserReconciliationService.SyncStatus status = reconciliationService.checkSyncStatus();

        // Then
        assertThat(status.getCognitoUserCount()).isEqualTo(2);
        assertThat(status.getDatabaseUserCount()).isEqualTo(2);
        assertThat(status.getMissingInDatabase()).isEqualTo(0);
        assertThat(status.getOrphanedInDatabase()).isEqualTo(0);
        assertThat(status.isInSync()).isTrue();
        assertThat(status.getMissingCognitoIds()).isEmpty();
    }

    /**
     * Test 7: should_returnOutOfSync_when_usersAreMissing
     * AC: checkSyncStatus() - missing users detected
     */
    @Test
    void should_returnOutOfSync_when_usersAreMissing() {
        // Given: Cognito has 3 users
        UserType cognitoUser1 = createCognitoUser("user1", "user1@example.com", "John", "Doe");
        UserType cognitoUser2 = createCognitoUser("user2", "user2@example.com", "Jane", "Smith");
        UserType cognitoUser3 = createCognitoUser("user3", "user3@example.com", "Bob", "Johnson");

        ListUsersIterable paginator = mockListUsersPaginator(cognitoUser1, cognitoUser2, cognitoUser3);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        // DB has only 1 user, and 1 orphaned user
        User dbUser1 = createUser("user1", "user1@example.com", "John", "Doe");
        User orphanedUser = createUser("orphaned", "orphaned@example.com", "Orphaned", "User");
        when(userRepository.findByIsActive(true)).thenReturn(List.of(dbUser1, orphanedUser));

        when(userRepository.findByCognitoUserId("user1")).thenReturn(Optional.of(dbUser1));
        when(userRepository.findByCognitoUserId("user2")).thenReturn(Optional.empty());
        when(userRepository.findByCognitoUserId("user3")).thenReturn(Optional.empty());

        // When
        UserReconciliationService.SyncStatus status = reconciliationService.checkSyncStatus();

        // Then
        assertThat(status.getCognitoUserCount()).isEqualTo(3);
        assertThat(status.getDatabaseUserCount()).isEqualTo(2);
        assertThat(status.getMissingInDatabase()).isEqualTo(2);
        assertThat(status.getMissingCognitoIds()).containsExactlyInAnyOrder("user2", "user3");
        assertThat(status.getOrphanedInDatabase()).isEqualTo(1);
        assertThat(status.isInSync()).isFalse();
    }

    // =============================
    // Unit Tests (Helper Methods)
    // =============================

    /**
     * Test 8: should_extractUserAttributes_when_parsingCognitoUser
     * AC: extractAttribute() helper method
     */
    @Test
    void should_extractUserAttributes_when_parsingCognitoUser() {
        // Given
        UserType cognitoUser = createCognitoUser("user1", "test@example.com", "FirstName", "LastName");

        // Mock empty Cognito list
        ListUsersIterable paginator = mockListUsersPaginator(cognitoUser);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);
        when(userRepository.findByIsActive(true)).thenReturn(List.of());
        when(userRepository.findByCognitoUserId("user1")).thenReturn(Optional.empty());
        // Username is email prefix "test" (from test@example.com)
        when(userRepository.existsByUsername("test")).thenReturn(false);

        // When
        reconciliationService.reconcileUsers();

        // Then: Verify user was created with extracted attributes
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getFirstName()).isEqualTo("FirstName");
        assertThat(savedUser.getLastName()).isEqualTo("LastName");
        assertThat(savedUser.getCognitoUserId()).isEqualTo("user1");
        assertThat(savedUser.getUsername()).isEqualTo("test");
    }

    /**
     * Test 9: should_createUser_when_cognitoUserProvided
     * AC: createMissingUser() helper method
     */
    @Test
    void should_createUser_when_cognitoUserProvided() {
        // Given
        when(userRepository.findByIsActive(true)).thenReturn(List.of());

        UserType cognitoUser = createCognitoUser("new-user", "new@example.com", "New", "User");
        ListUsersIterable paginator = mockListUsersPaginator(cognitoUser);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);
        when(userRepository.findByCognitoUserId("new-user")).thenReturn(Optional.empty());
        // Username is email prefix "new" (from new@example.com)
        when(userRepository.existsByUsername("new")).thenReturn(false);

        // When
        reconciliationService.reconcileUsers();

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getCognitoUserId()).isEqualTo("new-user");
        assertThat(savedUser.getUsername()).isEqualTo("new");
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getFirstName()).isEqualTo("New");
        assertThat(savedUser.getLastName()).isEqualTo("User");
        assertThat(savedUser.getRoles()).containsExactly(Role.ATTENDEE);
        assertThat(savedUser.isActive()).isTrue();
    }

    /**
     * Test 10: should_deactivateUser_when_orphanDetected
     * AC: deactivateOrphanedUser() logic within reconcileOrphanedDbUsers
     */
    @Test
    void should_deactivateUser_when_orphanDetected() {
        // Given
        User orphanUser = createUser("orphan-user", "orphan@example.com", "Orphan", "User");
        when(userRepository.findByIsActive(true)).thenReturn(List.of(orphanUser));

        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found").build());

        ListUsersIterable paginator = mockListUsersPaginator();
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        // When
        reconciliationService.reconcileUsers();

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User deactivatedUser = userCaptor.getValue();
        assertThat(deactivatedUser.isActive()).isFalse();
        assertThat(deactivatedUser.getDeactivationReason()).isEqualTo("Cognito user deleted");
    }

    /**
     * Test 11: should_generateUniqueUsername_when_conflictExists
     * AC: generateUsername() handles duplicates
     */
    @Test
    void should_generateUniqueUsername_when_conflictExists() {
        // Given
        when(userRepository.findByIsActive(true)).thenReturn(List.of());

        UserType cognitoUser = createCognitoUser("user1", "john.doe@example.com", "John", "Doe");
        ListUsersIterable paginator = mockListUsersPaginator(cognitoUser);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);
        when(userRepository.findByCognitoUserId("user1")).thenReturn(Optional.empty());

        // Simulate username conflict
        when(userRepository.existsByUsername("john.doe")).thenReturn(true);
        when(userRepository.existsByUsername("john.doe.1")).thenReturn(false);

        // When
        reconciliationService.reconcileUsers();

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("john.doe.1");
    }

    /**
     * Test 12: should_skipUsers_when_noCognitoUserId
     * AC: Users without Cognito ID are skipped in orphan check
     */
    @Test
    void should_skipUsers_when_noCognitoUserId() {
        // Given: User without Cognito ID (anonymous user)
        User anonymousUser = User.builder()
                .username("anonymous.user")
                .email("anonymous@example.com")
                .firstName("Anonymous")
                .lastName("User")
                .cognitoUserId(null)
                .isActive(true)
                .roles(Set.of(Role.ATTENDEE))
                .build();

        when(userRepository.findByIsActive(true)).thenReturn(List.of(anonymousUser));
        ListUsersIterable paginator = mockListUsersPaginator();
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(paginator);

        // When
        UserReconciliationService.ReconciliationReport report = reconciliationService.reconcileUsers();

        // Then: Anonymous user not checked in Cognito
        assertThat(report.getOrphanedUsers()).isEqualTo(0);
        verify(cognitoClient, never()).adminGetUser(any(AdminGetUserRequest.class));
    }

    // =============================
    // Report Tests
    // =============================

    /**
     * Test 13: should_buildReconciliationReport_when_operationsCompleted
     * AC: ReconciliationReport builder/getters work correctly
     */
    @Test
    void should_buildReconciliationReport_when_operationsCompleted() {
        // Given
        UserReconciliationService.ReconciliationReport report = new UserReconciliationService.ReconciliationReport();

        // When
        report.incrementOrphanedUsers();
        report.incrementOrphanedUsers();
        report.incrementMissingUsers();
        report.setDurationMs(1500L);
        report.addError("Test error 1");
        report.addError("Test error 2");

        // Then
        assertThat(report.getOrphanedUsers()).isEqualTo(2);
        assertThat(report.getMissingUsers()).isEqualTo(1);
        assertThat(report.getDurationMs()).isEqualTo(1500L);
        assertThat(report.getErrors()).containsExactly("Test error 1", "Test error 2");
    }

    /**
     * Test 14: should_buildSyncStatus_when_checkingSync
     * AC: SyncStatus builder/getters work correctly
     */
    @Test
    void should_buildSyncStatus_when_checkingSync() {
        // Given
        UserReconciliationService.SyncStatus status = new UserReconciliationService.SyncStatus();

        // When
        status.setCognitoUserCount(10);
        status.setDatabaseUserCount(8);
        status.setMissingInDatabase(2);
        status.setOrphanedInDatabase(0);
        status.setMissingCognitoIds(List.of("user1", "user2"));
        status.setMessage("Test message");

        // Then
        assertThat(status.getCognitoUserCount()).isEqualTo(10);
        assertThat(status.getDatabaseUserCount()).isEqualTo(8);
        assertThat(status.getMissingInDatabase()).isEqualTo(2);
        assertThat(status.getOrphanedInDatabase()).isEqualTo(0);
        assertThat(status.getMissingCognitoIds()).containsExactly("user1", "user2");
        assertThat(status.getMessage()).isEqualTo("Test message");
        assertThat(status.isInSync()).isFalse();
    }

    /**
     * Test 15: should_reportInSync_when_noDiscrepancies
     * AC: isInSync() returns true when no missing or orphaned users
     */
    @Test
    void should_reportInSync_when_noDiscrepancies() {
        // Given
        UserReconciliationService.SyncStatus status = new UserReconciliationService.SyncStatus();
        status.setCognitoUserCount(5);
        status.setDatabaseUserCount(5);
        status.setMissingInDatabase(0);
        status.setOrphanedInDatabase(0);

        // When/Then
        assertThat(status.isInSync()).isTrue();
    }

    // =============================
    // Helper Methods
    // =============================

    private User createUser(String cognitoUserId, String email, String firstName, String lastName) {
        return User.builder()
                .cognitoUserId(cognitoUserId)
                .username(email.split("@")[0])
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .isActive(true)
                .roles(Set.of(Role.ATTENDEE))
                .build();
    }

    private UserType createCognitoUser(String username, String email, String givenName, String familyName) {
        return UserType.builder()
                .username(username)
                .attributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("given_name").value(givenName).build(),
                        AttributeType.builder().name("family_name").value(familyName).build()
                )
                .build();
    }

    private ListUsersIterable mockListUsersPaginator(UserType... users) {
        ListUsersIterable paginator = mock(ListUsersIterable.class);
        ListUsersResponse response = ListUsersResponse.builder()
                .users(users)
                .build();
        when(paginator.iterator()).thenReturn(List.of(response).iterator());
        return paginator;
    }
}
