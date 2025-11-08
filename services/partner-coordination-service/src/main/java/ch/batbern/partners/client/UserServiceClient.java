package ch.batbern.partners.client;

import ch.batbern.partners.dto.UserProfileDTO;

/**
 * Client interface for communicating with the User Service API.
 *
 * This client provides user data retrieval for partner contact enrichment.
 * All methods use caching (15min TTL) to minimize API calls.
 */
public interface UserServiceClient {

    /**
     * Get user profile by username.
     *
     * @param username User's username (unique identifier per ADR-003)
     * @return User profile data
     * @throws ch.batbern.partners.exception.UserNotFoundException if user not found (404)
     * @throws ch.batbern.partners.exception.UserServiceException if API communication fails (5xx, timeout, network error)
     */
    UserProfileDTO getUserByUsername(String username);
}
