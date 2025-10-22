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
     * Search users by name with optional role filter
     * AC4: User search with autocomplete
     * AC13: Caffeine caching with 10-min TTL
     * AC14: Performance <100ms P95
     *
     * @param query Search query (first name or last name)
     * @param role Optional role filter
     * @return List of matching users (max 20 for autocomplete)
     */
    List<UserResponse> searchUsers(String query, Role role);

    /**
     * Invalidate search cache
     * Called when user data is updated
     */
    void invalidateCache();
}
