package ch.batbern.events.client;

import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;

import java.time.Instant;
import java.util.List;

/**
 * Client interface for communicating with the User Management Service API.
 *
 * This client replaces direct database access to the user_profiles table,
 * providing API-based user data retrieval for speaker enrichment and validation.
 *
 * Story 2.2a: Extended to support anonymous user creation (ADR-005)
 * Story BAT-7: Extended to support notification-related user queries
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
    UserResponse getUserByUsername(String username);

    /**
     * Check if a user exists by username.
     *
     * @param username User's username
     * @return true if user exists, false otherwise
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    boolean validateUserExists(String username);

    /**
     * Get or create user profile (ADR-005: Anonymous Event Registration).
     * <p>
     * Used for anonymous event registration where users register without creating a Cognito account.
     * Creates user with cognito_id=NULL when cognitoSync=false.
     * <p>
     * If user already exists (by email), returns existing user profile.
     * If user doesn't exist, creates new anonymous user profile.
     * <p>
     * Cached for 15 minutes using email as cache key.
     *
     * @param request User creation/lookup request with email, names, and cognitoSync flag
     * @return GetOrCreateUserResponse with username, created flag, and user profile data
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request);

    // Notification-specific methods (Story BAT-7)

    /**
     * Get user notification preferences.
     * Used for checking if user wants to receive notifications.
     *
     * @param username User's username
     * @return User notification preferences
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    ch.batbern.events.notification.UserPreferences getPreferences(String username);

    /**
     * Get user's email address by username.
     * Used for sending email notifications.
     *
     * @param username User's username
     * @return User's email address
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    String getEmailByUsername(String username);

    /**
     * Get user's last login timestamp.
     * Used for in-app notification queries.
     *
     * @param username User's username
     * @return Last login timestamp
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    Instant getLastLogin(String username);

    /**
     * Get all organizer usernames.
     * Used for sending in-app notifications to all organizers.
     *
     * @return List of organizer usernames
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<String> getOrganizerUsernames();
}
