package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.UserResponse;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of UserSearchService with Caffeine caching
 * Story 1.14-2 AC4, AC13, AC14: User search with autocomplete and caching
 *
 * Caching Strategy:
 * - Cache name: "userSearch"
 * - Cache key: query + role + limit (all three parameters)
 * - TTL: 10 minutes (configured in application.yml)
 * - Max entries: 1000 (configured in application.yml)
 * - Performance target: <100ms P95 with cache
 *
 * <p>The limit is part of the cache key on purpose: it changes the result set, so leaving it out
 * would let a narrow request's result be served to a wider one (users-api.openapi.yml already
 * documented "Cache key includes query, role filter, and limit" before the code did).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchServiceImpl implements UserSearchService {

    private final UserRepository userRepository;
    private final CacheManager cacheManager;
    private final UserResponseMapper responseMapper;

    /** Hard server-side ceiling, matching {@code maximum: 100} in users-api.openapi.yml. */
    private static final int MAX_AUTOCOMPLETE_RESULTS = 100;

    /** Applied when a caller passes a non-positive limit; matches the spec's {@code default: 20}. */
    private static final int DEFAULT_AUTOCOMPLETE_RESULTS = 20;

    /**
     * Search users by name, email or username with an optional role filter and caching.
     * AC4: User search with autocomplete
     * AC13: Caffeine caching with 10-min TTL
     * AC14: Performance <100ms P95
     *
     * @param query Search query, matched against username, email, first name, last name and full name
     * @param role Optional role filter, applied in SQL before the limit
     * @param limit Maximum number of results, clamped to {@code [1, 100]}
     * @return List of matching users, most relevant first
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userSearch",
            key = "#query + '_' + (#role != null ? #role.name() : 'ALL') + '_' + #limit")
    public List<UserResponse> searchUsers(String query, Role role, int limit) {
        int effectiveLimit = clampLimit(limit);
        log.debug("Searching users with query: {}, role: {}, limit: {}", query, role, effectiveLimit);

        // Ranking, role filtering and limiting all happen in the database. Doing any of it here
        // would mean filtering an already-truncated page — the 2026-09-09 bug.
        Pageable page = PageRequest.of(0, effectiveLimit);
        List<User> users = role != null
                ? userRepository.searchByNameOrEmailAndRole(query, role, page)
                : userRepository.searchByNameOrEmail(query, page);

        log.debug("Found {} users (limit {})", users.size(), effectiveLimit);

        // Map to response DTOs using UserResponseMapper
        return users.stream()
                .map(responseMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_AUTOCOMPLETE_RESULTS;
        }
        return Math.min(limit, MAX_AUTOCOMPLETE_RESULTS);
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

}
