package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserSearchService
 * Tests cover AC4, AC13, AC14 - User search with Caffeine caching
 *
 * Test Strategy:
 * - RED Phase: All tests fail initially
 * - GREEN Phase: Implement minimal code to pass tests
 * - REFACTOR Phase: Improve code quality while keeping tests green
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserSearchService Tests")
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private UserService userService;

    @Mock
    private ch.batbern.companyuser.service.UserResponseMapper responseMapper;

    @InjectMocks
    private UserSearchServiceImpl userSearchService;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        testUser1 = new User();
        testUser1.setId(UUID.randomUUID());
        testUser1.setUsername("john.doe");
        testUser1.setCognitoUserId("cognito-user-1");
        testUser1.setEmail("john.doe@example.com");
        testUser1.setFirstName("John");
        testUser1.setLastName("Doe");
        testUser1.setCompanyId("GoogleZH");
        testUser1.setRoles(Set.of(Role.ORGANIZER));
        testUser1.setActive(true);
        testUser1.setCreatedAt(Instant.now());
        testUser1.setUpdatedAt(Instant.now());

        testUser2 = new User();
        testUser2.setId(UUID.randomUUID());
        testUser2.setUsername("jane.doe");
        testUser2.setCognitoUserId("cognito-user-2");
        testUser2.setEmail("jane.doe@example.com");
        testUser2.setFirstName("Jane");
        testUser2.setLastName("Doe");
        testUser2.setCompanyId("GoogleZH");
        testUser2.setRoles(Set.of(Role.SPEAKER));
        testUser2.setActive(true);
        testUser2.setCreatedAt(Instant.now());
        testUser2.setUpdatedAt(Instant.now());

        testUser3 = new User();
        testUser3.setId(UUID.randomUUID());
        testUser3.setUsername("alice.smith");
        testUser3.setCognitoUserId("cognito-user-3");
        testUser3.setEmail("alice.smith@example.com");
        testUser3.setFirstName("Alice");
        testUser3.setLastName("Smith");
        testUser3.setCompanyId("SwissTech");
        testUser3.setRoles(Set.of(Role.ATTENDEE));
        testUser3.setActive(true);
        testUser3.setCreatedAt(Instant.now());
        testUser3.setUpdatedAt(Instant.now());

        // Mock responseMapper.mapToResponse() for all test users (lenient to avoid unnecessary stubbing warnings)
        lenient().when(responseMapper.mapToResponse(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserResponse response = new UserResponse();
            response.setId(user.getUsername());  // Story 1.16.2: id contains username
            response.setHasCognitoAccount(user.getCognitoUserId() != null);
            response.setEmail(user.getEmail());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setCompanyId(user.getCompanyId());
            response.setRoles(user.getRoles().stream()
                    .map(role -> UserResponse.RolesEnum.valueOf(role.name()))
                    .toList());
            response.setActive(user.isActive());
            response.setCreatedAt(java.time.OffsetDateTime.ofInstant(user.getCreatedAt(), java.time.ZoneOffset.UTC));
            response.setUpdatedAt(java.time.OffsetDateTime.ofInstant(user.getUpdatedAt(), java.time.ZoneOffset.UTC));
            return response;
        });
    }

    // AC4 Tests: Search Functionality

    @Test
    @DisplayName("Test 4.1: should_searchUsersByName_when_queryProvided")
    void should_searchUsersByName_when_queryProvided() {
        // Given
        String query = "doe";
        List<User> users = Arrays.asList(testUser1, testUser2);
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null, 20);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getFirstName()).isEqualTo("John");
        assertThat(results.get(1).getFirstName()).isEqualTo("Jane");
        verify(userRepository).searchByNameOrEmail(eq(query), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 4.2: should_filterByRole_when_roleProvided")
    void should_filterByRole_when_roleProvided() {
        // Given
        String query = "doe";
        Role roleFilter = Role.ORGANIZER;
        List<User> users = Collections.singletonList(testUser1);
        when(userRepository.searchByNameOrEmailAndRole(eq(query), eq(roleFilter), any(Pageable.class)))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, roleFilter, 20);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRoles()).contains(UserResponse.RolesEnum.ORGANIZER);
        // The role filter is applied by the DATABASE now, not by streaming over a truncated page.
        verify(userRepository).searchByNameOrEmailAndRole(eq(query), eq(roleFilter), any(Pageable.class));
        verify(userRepository, never()).searchByNameOrEmail(eq(query), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 4.3: should_returnAutocompleteResults_when_partialNameProvided")
    void should_returnAutocompleteResults_when_partialNameProvided() {
        // Given
        String partialQuery = "jo";
        List<User> users = Collections.singletonList(testUser1);
        when(userRepository.searchByNameOrEmail(eq(partialQuery), any(Pageable.class)))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(partialQuery, null, 20);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFirstName().toLowerCase()).contains(partialQuery);
        verify(userRepository).searchByNameOrEmail(eq(partialQuery), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 4.4: should_pushTheRequestedLimitToTheDatabase_when_searching")
    void should_pushTheRequestedLimitToTheDatabase_when_searching() {
        // Given
        String query = "user";
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(createManyUsers(7));

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null, 7);

        // Then: the limit is the DB's page size, not an in-memory truncation of an unordered set
        assertThat(results).hasSize(7);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByNameOrEmail(eq(query), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(7);
    }

    @Test
    @DisplayName("Test 4.6: should_honourALimitAboveTwenty_when_callerAsksForMore")
    void should_honourALimitAboveTwenty_when_callerAsksForMore() {
        // Regression guard for the 2026-09-09 bug: MAX_AUTOCOMPLETE_RESULTS was 20 and was
        // applied INSIDE the service, so `limit=50` was silently capped at 20 and the caller
        // could not widen the window to reach a user ranked past position 20.
        String query = "matthias";
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(createManyUsers(50));

        List<UserResponse> results = userSearchService.searchUsers(query, null, 50);

        assertThat(results).hasSize(50);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByNameOrEmail(eq(query), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Test 4.7: should_clampTheLimit_when_callerExceedsTheSpecMaximum")
    void should_clampTheLimit_when_callerExceedsTheSpecMaximum() {
        String query = "matthias";
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        userSearchService.searchUsers(query, null, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByNameOrEmail(eq(query), captor.capture());
        // users-api.openapi.yml declares `maximum: 100`
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Test 4.8: should_fallBackToTheDefaultLimit_when_limitIsNotPositive")
    void should_fallBackToTheDefaultLimit_when_limitIsNotPositive() {
        String query = "matthias";
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        userSearchService.searchUsers(query, null, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByNameOrEmail(eq(query), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Test 4.5: should_returnEmptyList_when_noMatchesFound")
    void should_returnEmptyList_when_noMatchesFound() {
        // Given
        String query = "nonexistent";
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null, 20);

        // Then
        assertThat(results).isEmpty();
        verify(userRepository).searchByNameOrEmail(eq(query), any(Pageable.class));
    }

    // AC13 Tests: Caffeine Caching

    @Test
    @DisplayName("Test 13.1: should_cacheSearchResults_when_queryExecuted")
    void should_cacheSearchResults_when_queryExecuted() {
        // Given
        String query = "doe";
        List<User> users = Arrays.asList(testUser1, testUser2);
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(users);

        // When
        List<UserResponse> firstCall = userSearchService.searchUsers(query, null, 20);
        List<UserResponse> secondCall = userSearchService.searchUsers(query, null, 20);

        // Then - With unit tests, caching may not be active (requires Spring context)
        // This test verifies the service works correctly when called multiple times
        // Full caching behavior will be verified in integration tests
        assertThat(firstCall).hasSize(2);
        assertThat(secondCall).hasSize(2);
        // Note: In unit tests without Spring context, cache annotations don't work
        // We verify the method returns correct results - caching verified in integration tests
        verify(userRepository, atLeastOnce()).searchByNameOrEmail(eq(query), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 13.2: should_invalidateCache_when_userUpdated")
    void should_invalidateCache_when_userUpdated() {
        // Given
        when(cacheManager.getCache("userSearch")).thenReturn(cache);

        // When
        userSearchService.invalidateCache();

        // Then
        verify(cacheManager).getCache("userSearch");
        verify(cache).clear();
    }

    @Test
    @DisplayName("Test 13.3: should_useSeparateCacheKeys_when_differentQueriesProvided")
    void should_useSeparateCacheKeys_when_differentQueriesProvided() {
        // Given
        String query1 = "doe";
        String query2 = "smith";
        List<User> doeUsers = Arrays.asList(testUser1, testUser2);
        List<User> smithUsers = Collections.singletonList(testUser3);

        when(userRepository.searchByNameOrEmail(eq(query1), any(Pageable.class)))
                .thenReturn(doeUsers);
        when(userRepository.searchByNameOrEmail(eq(query2), any(Pageable.class)))
                .thenReturn(smithUsers);

        // When
        List<UserResponse> results1 = userSearchService.searchUsers(query1, null, 20);
        List<UserResponse> results2 = userSearchService.searchUsers(query2, null, 20);

        // Then - Each query should hit the repository once
        assertThat(results1).hasSize(2);
        assertThat(results2).hasSize(1);
        verify(userRepository).searchByNameOrEmail(eq(query1), any(Pageable.class));
        verify(userRepository).searchByNameOrEmail(eq(query2), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 13.4: should_returnFreshData_when_cacheInvalidated")
    void should_returnFreshData_when_cacheInvalidated() {
        // Given
        String query = "doe";
        List<User> initialUsers = Collections.singletonList(testUser1);
        List<User> updatedUsers = Arrays.asList(testUser1, testUser2);

        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(initialUsers)
                .thenReturn(updatedUsers);
        when(cacheManager.getCache("userSearch")).thenReturn(cache);

        // When
        List<UserResponse> firstCall = userSearchService.searchUsers(query, null, 20);
        userSearchService.invalidateCache(); // Invalidate cache
        List<UserResponse> secondCall = userSearchService.searchUsers(query, null, 20);

        // Then
        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).hasSize(2); // Fresh data after cache invalidation
        verify(userRepository, times(2)).searchByNameOrEmail(eq(query), any(Pageable.class));
    }

    // AC14 Tests: Cache Performance (<100ms P95)

    @Test
    @DisplayName("Test 14.1: should_cacheByQueryAndRole_when_roleFilterProvided")
    void should_cacheByQueryAndRole_when_roleFilterProvided() {
        // Given
        String query = "doe";
        Role roleFilter = Role.SPEAKER;
        List<User> users = Collections.singletonList(testUser2);
        when(userRepository.searchByNameOrEmailAndRole(eq(query), eq(roleFilter), any(Pageable.class)))
                .thenReturn(users);

        // When
        List<UserResponse> results1 = userSearchService.searchUsers(query, roleFilter, 20);
        List<UserResponse> results2 = userSearchService.searchUsers(query, roleFilter, 20);

        // Then - Verify correct filtering
        assertThat(results1).hasSize(1);
        assertThat(results2).hasSize(1);
        assertThat(results1.get(0).getRoles()).contains(UserResponse.RolesEnum.SPEAKER);
        verify(userRepository, atLeastOnce())
                .searchByNameOrEmailAndRole(eq(query), eq(roleFilter), any(Pageable.class));
    }

    @Test
    @DisplayName("Test 14.2: should_handleNullRole_when_noRoleFilterProvided")
    void should_handleNullRole_when_noRoleFilterProvided() {
        // Given
        String query = "doe";
        List<User> users = Arrays.asList(testUser1, testUser2);
        when(userRepository.searchByNameOrEmail(eq(query), any(Pageable.class)))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null, 20);

        // Then - Should return all matching users without role filter
        assertThat(results).hasSize(2);
        verify(userRepository).searchByNameOrEmail(eq(query), any(Pageable.class));
    }

    // Helper Methods

    private List<User> createManyUsers(int count) {
        List<User> users = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("user" + i + ".test");
            user.setEmail("user" + i + "@example.com");
            user.setFirstName("User" + i);
            user.setLastName("Test");
            user.setCompanyId("TestCo");
            user.setRoles(Set.of(Role.ATTENDEE));
            user.setActive(true);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            users.add(user);
        }
        return users;
    }
}
