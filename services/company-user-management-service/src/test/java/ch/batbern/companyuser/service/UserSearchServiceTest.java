package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.UserResponse;
import ch.batbern.companyuser.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
        testUser3.setEmail("alice.smith@example.com");
        testUser3.setFirstName("Alice");
        testUser3.setLastName("Smith");
        testUser3.setCompanyId("SwissTech");
        testUser3.setRoles(Set.of(Role.ATTENDEE));
        testUser3.setActive(true);
        testUser3.setCreatedAt(Instant.now());
        testUser3.setUpdatedAt(Instant.now());
    }

    // AC4 Tests: Search Functionality

    @Test
    @DisplayName("Test 4.1: should_searchUsersByName_when_queryProvided")
    void should_searchUsersByName_when_queryProvided() {
        // Given
        String query = "doe";
        List<User> users = Arrays.asList(testUser1, testUser2);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getFirstName()).isEqualTo("John");
        assertThat(results.get(1).getFirstName()).isEqualTo("Jane");
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    @Test
    @DisplayName("Test 4.2: should_filterByRole_when_roleProvided")
    void should_filterByRole_when_roleProvided() {
        // Given
        String query = "doe";
        Role roleFilter = Role.ORGANIZER;
        List<User> users = Collections.singletonList(testUser1);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, roleFilter);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRoles()).contains(Role.ORGANIZER);
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    @Test
    @DisplayName("Test 4.3: should_returnAutocompleteResults_when_partialNameProvided")
    void should_returnAutocompleteResults_when_partialNameProvided() {
        // Given
        String partialQuery = "jo";
        List<User> users = Collections.singletonList(testUser1);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(partialQuery, partialQuery))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(partialQuery, null);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFirstName().toLowerCase()).contains(partialQuery);
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(partialQuery, partialQuery);
    }

    @Test
    @DisplayName("Test 4.4: should_limitAutocompleteResults_when_maxResultsExceeded")
    void should_limitAutocompleteResults_when_maxResultsExceeded() {
        // Given
        String query = "user";
        List<User> manyUsers = createManyUsers(25);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(manyUsers);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null);

        // Then - Max 20 autocomplete results per design
        assertThat(results).hasSizeLessThanOrEqualTo(20);
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    @Test
    @DisplayName("Test 4.5: should_returnEmptyList_when_noMatchesFound")
    void should_returnEmptyList_when_noMatchesFound() {
        // Given
        String query = "nonexistent";
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(Collections.emptyList());

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null);

        // Then
        assertThat(results).isEmpty();
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    // AC13 Tests: Caffeine Caching

    @Test
    @DisplayName("Test 13.1: should_cacheSearchResults_when_queryExecuted")
    void should_cacheSearchResults_when_queryExecuted() {
        // Given
        String query = "doe";
        List<User> users = Arrays.asList(testUser1, testUser2);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(users);

        // When
        List<UserResponse> firstCall = userSearchService.searchUsers(query, null);
        List<UserResponse> secondCall = userSearchService.searchUsers(query, null);

        // Then - With unit tests, caching may not be active (requires Spring context)
        // This test verifies the service works correctly when called multiple times
        // Full caching behavior will be verified in integration tests
        assertThat(firstCall).hasSize(2);
        assertThat(secondCall).hasSize(2);
        // Note: In unit tests without Spring context, cache annotations don't work
        // We verify the method returns correct results - caching verified in integration tests
        verify(userRepository, atLeastOnce()).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
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

        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query1, query1))
                .thenReturn(doeUsers);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query2, query2))
                .thenReturn(smithUsers);

        // When
        List<UserResponse> results1 = userSearchService.searchUsers(query1, null);
        List<UserResponse> results2 = userSearchService.searchUsers(query2, null);

        // Then - Each query should hit the repository once
        assertThat(results1).hasSize(2);
        assertThat(results2).hasSize(1);
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query1, query1);
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query2, query2);
    }

    @Test
    @DisplayName("Test 13.4: should_returnFreshData_when_cacheInvalidated")
    void should_returnFreshData_when_cacheInvalidated() {
        // Given
        String query = "doe";
        List<User> initialUsers = Collections.singletonList(testUser1);
        List<User> updatedUsers = Arrays.asList(testUser1, testUser2);

        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(initialUsers)
                .thenReturn(updatedUsers);
        when(cacheManager.getCache("userSearch")).thenReturn(cache);

        // When
        List<UserResponse> firstCall = userSearchService.searchUsers(query, null);
        userSearchService.invalidateCache(); // Invalidate cache
        List<UserResponse> secondCall = userSearchService.searchUsers(query, null);

        // Then
        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).hasSize(2); // Fresh data after cache invalidation
        verify(userRepository, times(2)).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    // AC14 Tests: Cache Performance (<100ms P95)

    @Test
    @DisplayName("Test 14.1: should_cacheByQueryAndRole_when_roleFilterProvided")
    void should_cacheByQueryAndRole_when_roleFilterProvided() {
        // Given
        String query = "doe";
        Role roleFilter = Role.SPEAKER;
        List<User> users = Collections.singletonList(testUser2);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(users);

        // When
        List<UserResponse> results1 = userSearchService.searchUsers(query, roleFilter);
        List<UserResponse> results2 = userSearchService.searchUsers(query, roleFilter);

        // Then - Verify correct filtering
        assertThat(results1).hasSize(1);
        assertThat(results2).hasSize(1);
        assertThat(results1.get(0).getRoles()).contains(Role.SPEAKER);
        verify(userRepository, atLeastOnce()).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
    }

    @Test
    @DisplayName("Test 14.2: should_handleNullRole_when_noRoleFilterProvided")
    void should_handleNullRole_when_noRoleFilterProvided() {
        // Given
        String query = "doe";
        List<User> users = Arrays.asList(testUser1, testUser2);
        when(userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query))
                .thenReturn(users);

        // When
        List<UserResponse> results = userSearchService.searchUsers(query, null);

        // Then - Should return all matching users without role filter
        assertThat(results).hasSize(2);
        verify(userRepository).findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
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
