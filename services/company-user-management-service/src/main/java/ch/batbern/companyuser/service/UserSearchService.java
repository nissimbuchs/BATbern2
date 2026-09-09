package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.dto.generated.UserResponse;

import java.util.List;

/**
 * Service for user search with caching
 * Story 1.14-2 AC4, AC13, AC14: Search with Caffeine caching
 */
public interface UserSearchService {

    /**
     * Search users by name, email or username with an optional role filter.
     * AC4: User search with autocomplete
     * AC13: Caffeine caching with 10-min TTL
     * AC14: Performance <100ms P95
     *
     * <p>Both the role filter and the limit are applied by the database, relevance-ranked
     * (bug fix 2026-09-09 — the previous implementation truncated an unordered result set at a
     * hard 20 in memory and then filtered by role, which hid users the caller had asked for).
     *
     * @param query Search query, matched against username, email, first name, last name and full name
     * @param role Optional role filter, applied in SQL before the limit
     * @param limit Maximum number of results, clamped to {@code [1, 100]} per users-api.openapi.yml
     * @return List of matching users, most relevant first
     */
    List<UserResponse> searchUsers(String query, Role role, int limit);

    /**
     * Invalidate search cache
     * Called when user data is updated
     */
    void invalidateCache();
}
