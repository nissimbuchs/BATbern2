package ch.batbern.events.client;

import ch.batbern.events.dto.CompanyBasicDto;
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

    /**
     * Get all speaker usernames.
     * Used for legacy export — speaker metadata enrichment.
     *
     * @return List of speaker usernames
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<String> getSpeakerUsernames();

    /**
     * Get all companies (basic info) from the company-user-management-service.
     * Used for the companies[] list in the legacy BAT export envelope.
     * Story 10.20: AC1
     *
     * @return List of companies with name, displayName, website
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    List<CompanyBasicDto> getAllCompanies();

    // Profile update methods (Story 6.2b)

    /**
     * Update user profile fields.
     * Story 6.2b: Speaker Profile Update Portal (AC10)
     *
     * Used for syncing speaker profile updates to Company Service.
     * Updates User fields: firstName, lastName, bio, profilePictureUrl.
     *
     * @param username User's username
     * @param updateDto fields to update (null fields are ignored)
     * @return Updated user profile
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    UserResponse updateUser(String username, ch.batbern.events.dto.UserUpdateDto updateDto);

    /**
     * Update user profile picture URL.
     * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
     *
     * Used after successful S3 photo upload confirmation.
     * Updates only User.profilePictureUrl field.
     *
     * @param username User's username
     * @param profilePictureUrl CloudFront URL of the uploaded photo
     * @throws UserNotFoundException if user not found (404)
     * @throws UserServiceException if API communication fails (5xx, timeout, network error)
     */
    void updateUserProfilePicture(String username, String profilePictureUrl);
}
