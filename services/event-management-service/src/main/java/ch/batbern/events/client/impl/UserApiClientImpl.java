package ch.batbern.events.client.impl;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.CognitoAuthResult;
import ch.batbern.events.dto.SpeakerAuthRequest;
import ch.batbern.events.dto.SpeakerConfirmResetRequest;
import ch.batbern.events.dto.SpeakerForgotPasswordRequest;
import ch.batbern.events.dto.SpeakerProvisionRequest;
import ch.batbern.events.dto.SpeakerProvisionResponse;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.PaginatedUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of UserApiClient using Spring RestTemplate.
 *
 * Communicates with the User Management Service REST API to retrieve user data.
 * Replaces direct database access to user_profiles table.
 *
 * Features:
 * - JWT token propagation from incoming requests
 * - Aggressive caching (15min TTL) for performance
 * - Fail-fast error handling
 * - Comprehensive logging
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserApiClientImpl implements UserApiClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.base-url}")
    private String userServiceBaseUrl;

    /**
     * Get user profile by username.
     *
     * Cached for 15 minutes to minimize API calls.
     *
     * @param username User's username
     * @return User profile data
     * @throws UserNotFoundException if user not found
     * @throws UserServiceException if API communication fails
     */
    @Override
    @Cacheable(value = "userApiCache", key = "#username")
    public UserResponse getUserByUsername(String username) {
        log.debug("Fetching user profile for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username;

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    UserResponse.class
            );

            UserResponse user = response.getBody();
            log.debug("Successfully fetched user profile for username: {}", username);
            return user;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching user {}: {} - {}", username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for user {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for user {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching user {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error fetching user: " + username,
                    e
            );
        }
    }

    /**
     * Check if a user exists by username.
     *
     * Cached for 15 minutes to minimize API calls.
     *
     * @param username User's username
     * @return true if user exists, false otherwise
     * @throws UserServiceException if API communication fails
     */
    @Override
    @Cacheable(value = "userApiCache", key = "#username", unless = "#result == false")
    public boolean validateUserExists(String username) {
        log.debug("Validating user exists: {}", username);

        try {
            getUserByUsername(username);
            return true;
        } catch (UserNotFoundException e) {
            log.debug("User does not exist: {}", username);
            return false;
        }
    }

    /**
     * Get or create user profile (ADR-005: Anonymous Event Registration).
     *
     * Used for anonymous event registration where users register without creating a Cognito account.
     * Creates user with cognito_id=NULL when cognitoSync=false.
     *
     * If user already exists (by email), returns existing user profile.
     * If user doesn't exist, creates new anonymous user profile.
     *
     * Cached for 15 minutes using email as cache key.
     *
     * @param request User creation/lookup request with email, names, and cognitoSync flag
     * @return User profile data (existing or newly created)
     * @throws UserServiceException if API communication fails
     */
    @Override
    @Cacheable(value = "userApiCache", key = "#request.email")
    public GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request) {
        log.debug("Getting or creating user for email: {}", request.getEmail());

        String url = userServiceBaseUrl + "/api/v1/users/get-or-create";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<GetOrCreateUserRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<GetOrCreateUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpRequest,
                    GetOrCreateUserResponse.class
            );

            GetOrCreateUserResponse result = response.getBody();
            log.info("Successfully got/created user profile for email: {}, username: {}, created: {}",
                    request.getEmail(),
                    result != null ? result.getUsername() : "null",
                    result != null ? result.getCreated() : "null");
            return result;

        } catch (HttpClientErrorException e) {
            log.error("Client error getting/creating user for email {}: {} - {}",
                    request.getEmail(), e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error getting/creating user for email: " + request.getEmail(),
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for email {}: {} - {}",
                    request.getEmail(), e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error for email: " + request.getEmail(),
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for email {}: {}",
                    request.getEmail(), e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for email: " + request.getEmail(),
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error getting/creating user for email {}: {}",
                    request.getEmail(), e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error getting/creating user for email: " + request.getEmail(),
                    e
            );
        }
    }

    @Override
    public ch.batbern.events.notification.UserPreferences getPreferences(String username) {
        log.debug("Fetching preferences for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username + "/preferences";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<ch.batbern.events.notification.UserPreferences> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    ch.batbern.events.notification.UserPreferences.class
            );

            ch.batbern.events.notification.UserPreferences prefs = response.getBody();
            log.debug("Successfully fetched preferences for username: {}", username);
            return prefs;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching preferences for {}: {} - {}", username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching preferences for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for preferences {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for preferences {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching preferences for {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error fetching preferences for user: " + username,
                    e
            );
        }
    }

    @Override
    public String getEmailByUsername(String username) {
        log.debug("Fetching email for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username + "/email";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            String email = response.getBody();
            log.debug("Successfully fetched email for username: {}", username);
            return email;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching email for {}: {} - {}", username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching email for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for email {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for email {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching email for {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error fetching email for user: " + username,
                    e
            );
        }
    }

    @Override
    public java.time.Instant getLastLogin(String username) {
        log.debug("Fetching last login for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username + "/last-login";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<java.time.Instant> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    java.time.Instant.class
            );

            java.time.Instant lastLogin = response.getBody();
            log.debug("Successfully fetched last login for username: {}", username);
            return lastLogin;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching last login for {}: {} - {}", username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching last login for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for last login {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for last login {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching last login for {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error fetching last login for user: " + username,
                    e
            );
        }
    }

    @Override
    public java.util.List<String> getOrganizerUsernames() {
        log.debug("Fetching organizer usernames");

        String url = userServiceBaseUrl + "/api/v1/users?role=ORGANIZER";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<PaginatedUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    PaginatedUserResponse.class
            );

            PaginatedUserResponse body = response.getBody();
            if (body == null || body.getData() == null) {
                log.debug("No organizers found");
                return java.util.List.of();
            }

            java.util.List<String> usernames = body.getData().stream()
                    .map(UserResponse::getId)  // 'id' field contains the username
                    .collect(java.util.stream.Collectors.toList());

            log.debug("Successfully fetched {} organizer usernames", usernames.size());
            return usernames;

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching organizer list: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching organizer list",
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for organizer list: {} - {}",
                    e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error fetching organizer list",
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for organizer list: {}",
                    e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for organizer list",
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching organizer list: {}", e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error fetching organizer list",
                    e
            );
        }
    }

    /**
     * Update user profile fields.
     * Story 6.2b: Speaker Profile Update Portal (AC10)
     *
     * @param username User's username
     * @param updateDto fields to update
     * @return Updated user profile
     */
    @Override
    public UserResponse updateUser(String username, ch.batbern.events.dto.UserUpdateDto updateDto) {
        log.debug("Updating user profile for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username;

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            headers.set("Content-Type", "application/json");
            HttpEntity<ch.batbern.events.dto.UserUpdateDto> request = new HttpEntity<>(updateDto, headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    request,
                    UserResponse.class
            );

            UserResponse user = response.getBody();
            log.info("Successfully updated user profile for username: {}", username);
            return user;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found for update: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error updating user {}: {} - {}", username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error updating user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for user {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for user {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error updating user {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error updating user: " + username,
                    e
            );
        }
    }

    /**
     * Update user profile picture URL.
     * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
     *
     * Uses PATCH to update only the profilePictureUrl field.
     *
     * @param username User's username
     * @param profilePictureUrl CloudFront URL of the uploaded photo
     */
    @Override
    public void updateUserProfilePicture(String username, String profilePictureUrl) {
        log.debug("Updating profile picture for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username + "/profile-picture";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            headers.set("Content-Type", "application/json");

            // Simple DTO with just the URL
            java.util.Map<String, String> body = java.util.Map.of("profilePictureUrl", profilePictureUrl);
            HttpEntity<java.util.Map<String, String>> request = new HttpEntity<>(body, headers);

            restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    request,
                    Void.class
            );

            log.info("Successfully updated profile picture for username: {}", username);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found for profile picture update: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error updating profile picture for {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error updating profile picture for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for profile picture {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error updating profile picture for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for profile picture {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error updating profile picture for {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error updating profile picture for user: " + username,
                    e
            );
        }
    }

    /**
     * Story 9.2: Provision a speaker account in Cognito and local DB.
     * Calls POST /api/v1/users/speaker-accounts on company-user-management-service.
     * Not cached — each call must reach the service to ensure provisioning completes.
     */
    @Override
    public SpeakerProvisionResponse provisionSpeakerAccount(SpeakerProvisionRequest request) {
        String email = request.getEmail();
        String maskedEmail = email != null && email.length() > 3
                ? email.substring(0, 3) + "***"
                : "***";
        log.info("Provisioning speaker account for email: {}", maskedEmail);

        String url = userServiceBaseUrl + "/api/v1/users/speaker-accounts";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<SpeakerProvisionRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<SpeakerProvisionResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpRequest,
                    SpeakerProvisionResponse.class
            );

            SpeakerProvisionResponse result = response.getBody();
            log.info("Speaker account provisioned: username={}, action={}",
                    result != null ? result.getUsername() : "null",
                    result != null ? result.getAction() : "null");
            return result;

        } catch (HttpClientErrorException e) {
            log.error("Client error provisioning speaker account: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error provisioning speaker account",
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error provisioning speaker account: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error provisioning speaker account",
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error provisioning speaker account: {}", e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for speaker provisioning",
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error provisioning speaker account: {}", e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error provisioning speaker account",
                    e
            );
        }
    }

    /**
     * Story 9.3 Task 3.5: Authenticate speaker via Cognito.
     * Calls POST /api/v1/speaker-auth/authenticate on company-user-management-service.
     */
    @Override
    public CognitoAuthResult authenticateSpeaker(SpeakerAuthRequest request) {
        log.info("Authenticating speaker via Cognito");

        String url = userServiceBaseUrl + "/api/v1/speaker-auth/authenticate";

        try {
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<SpeakerAuthRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<CognitoAuthResult> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpRequest,
                    CognitoAuthResult.class
            );

            log.info("Speaker Cognito authentication successful");
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.warn("Client error authenticating speaker: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error authenticating speaker",
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error authenticating speaker: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error authenticating speaker",
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error authenticating speaker: {}", e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for speaker authentication",
                    e
            );
        }
    }

    /**
     * Story 9.3 Task 3.5: Initiate speaker password reset.
     * Calls POST /api/v1/speaker-auth/forgot-password on company-user-management-service.
     */
    @Override
    public void speakerForgotPassword(SpeakerForgotPasswordRequest request) {
        log.info("Initiating speaker password reset");

        String url = userServiceBaseUrl + "/api/v1/speaker-auth/forgot-password";

        try {
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<SpeakerForgotPasswordRequest> httpRequest = new HttpEntity<>(request, headers);

            restTemplate.exchange(url, HttpMethod.POST, httpRequest, Void.class);
            log.info("Speaker password reset initiated");

        } catch (HttpClientErrorException e) {
            log.warn("Client error initiating password reset: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error initiating speaker password reset",
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error initiating password reset: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error initiating speaker password reset",
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error initiating password reset: {}", e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for password reset",
                    e
            );
        }
    }

    /**
     * Story 9.3 Task 3.5: Confirm speaker password reset.
     * Calls POST /api/v1/speaker-auth/confirm-reset on company-user-management-service.
     */
    @Override
    public void speakerConfirmReset(SpeakerConfirmResetRequest request) {
        log.info("Confirming speaker password reset");

        String url = userServiceBaseUrl + "/api/v1/speaker-auth/confirm-reset";

        try {
            HttpHeaders headers = createServiceHeaders();
            HttpEntity<SpeakerConfirmResetRequest> httpRequest = new HttpEntity<>(request, headers);

            restTemplate.exchange(url, HttpMethod.POST, httpRequest, Void.class);
            log.info("Speaker password reset confirmed");

        } catch (HttpClientErrorException e) {
            log.warn("Client error confirming password reset: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error confirming speaker password reset",
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error confirming password reset: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error confirming speaker password reset",
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error confirming password reset: {}", e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for password reset confirmation",
                    e
            );
        }
    }

    /**
     * Create HTTP headers without JWT token for service-to-service calls
     * where no user auth context is present (e.g., speaker auth endpoints).
     */
    private HttpHeaders createServiceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }

    /**
     * Create HTTP headers with JWT token propagated from SecurityContext.
     *
     * Extracts the JWT token from the current security context and adds it
     * to the Authorization header for service-to-service communication.
     *
     * @return HttpHeaders with Authorization Bearer token
     */
    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();

        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                String token = jwt.getTokenValue();
                headers.set("Authorization", "Bearer " + token);
                log.trace("JWT token propagated to User Management Service");
            } else {
                log.warn("No JWT token found in SecurityContext, principal type: {}",
                        principal != null ? principal.getClass().getSimpleName() : "null");
            }

        } catch (Exception e) {
            log.warn("Failed to extract JWT token from SecurityContext: {}", e.getMessage());
            // Continue without token - let the User Management Service handle authorization
        }

        return headers;
    }
}
