package ch.batbern.events.client;

import ch.batbern.events.dto.UserProfileDTO;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;

/**
 * Client interface for communicating with the User Management Service API.
 *
 * This client replaces direct database access to the user_profiles table,
 * providing API-based user data retrieval for speaker enrichment and validation.
 *
 * All methods use aggressive caching (15min TTL) to minimize API calls.
 */
public interface UserApiClient {

    /**
     * Get user profile by username.
     *
     * @param username User's username (public identifier)
     * @return User profile data
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    UserProfileDTO getUserByUsername(String username);

    /**
     * Check if a user exists by username.
     *
     * @param username User's username
     * @return true if user exists, false otherwise
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    boolean validateUserExists(String username);
}
