package ch.batbern.partners.client;

import ch.batbern.partners.client.user.dto.UserResponse;

import java.util.List;

/**
 * Client interface for communicating with the User Service API.
 *
 * Uses generated DTO from users-api.openapi.yml for type safety.
 */
public interface UserServiceClient {

    /**
     * Get user profile by username.
     *
     * @param username User's username (unique identifier per ADR-003)
     * @return User profile data
     * @throws ch.batbern.partners.exception.UserNotFoundException if user not found (404)
     * @throws ch.batbern.partners.exception.UserServiceException if API communication fails
     */
    UserResponse getUserByUsername(String username);

    /**
     * Alias for getUserByUsername() for clarity in contact enrichment scenarios.
     */
    UserResponse getUserProfile(String username);

    /**
     * Get the currently authenticated user's own profile.
     * Calls GET /users/me — accessible to any authenticated user regardless of role.
     *
     * @return current user's profile including companyId
     * @throws ch.batbern.partners.exception.UserServiceException if API communication fails
     */
    UserResponse getCurrentUserProfile();

    /**
     * List all users belonging to a company with the given role.
     * Calls GET /users?company={companyName}&role={role}&limit=100.
     *
     * Used by contacts tab: find all PARTNER users for a given company.
     *
     * @param companyName ADR-003 company identifier
     * @param role        role filter (e.g. "PARTNER")
     * @return list of matching users (empty if none found)
     */
    List<UserResponse> getUsersByCompanyAndRole(String companyName, String role);

    /**
     * List all users with the given role across all companies.
     * Calls GET /users?role={role}&limit=100.
     *
     * Used for calendar invite email collection: get all PARTNER users globally.
     *
     * @param role role filter (e.g. "PARTNER")
     * @return list of matching users (empty if none found)
     */
    List<UserResponse> getUsersByRole(String role);
}
