package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.UserResponse;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of UserSearchService with Caffeine caching
 * Story 1.14-2 AC4, AC13, AC14: User search with autocomplete and caching
 *
 * Caching Strategy:
 * - Cache name: "userSearch"
 * - Cache key: query + role (both parameters)
 * - TTL: 10 minutes (configured in application.yml)
 * - Max entries: 1000 (configured in application.yml)
 * - Performance target: <100ms P95 with cache
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchServiceImpl implements UserSearchService {

    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    private static final int MAX_AUTOCOMPLETE_RESULTS = 20;

    /**
     * Search users by name with optional role filter and caching
     * AC4: User search with autocomplete
     * AC13: Caffeine caching with 10-min TTL
     * AC14: Performance <100ms P95
     *
     * @param query Search query (first name or last name)
     * @param role Optional role filter
     * @return List of matching users (max 20 for autocomplete)
     */
    @Cacheable(value = "userSearch", key = "#query + '_' + (#role != null ? #role.name() : 'ALL')")
    public List<UserResponse> searchUsers(String query, Role role) {
        log.debug("Searching users with query: {} and role: {}", query, role);

        // Search users by name
        List<User> users = userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);

        // Filter by role if provided
        if (role != null) {
            users = users.stream()
                    .filter(user -> user.getRoles().contains(role))
                    .collect(Collectors.toList());
        }

        // Limit to max autocomplete results
        List<User> limitedUsers = users.stream()
                .limit(MAX_AUTOCOMPLETE_RESULTS)
                .collect(Collectors.toList());

        log.debug("Found {} users (limited to {})", users.size(), limitedUsers.size());

        // Map to response DTOs
        return limitedUsers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Invalidate user search cache
     * Called when user data is updated
     */
    @Override
    public void invalidateCache() {
        var cache = cacheManager.getCache("userSearch");
        if (cache != null) {
            cache.clear();
            log.debug("User search cache invalidated");
        }
    }

    /**
     * Map User entity to UserResponse DTO
     * Story 1.16.2: id field contains username (not UUID)
     *
     * @param user User entity
     * @return UserResponse DTO
     */
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getUsername())  // Story 1.16.2: username as public ID
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .bio(user.getBio())
                .companyId(user.getCompanyId())  // Story 1.16.2: company name
                .roles(user.getRoles())
                .profilePictureUrl(user.getProfilePictureUrl())
                .active(user.isActive())  // Map isActive() to active field
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
