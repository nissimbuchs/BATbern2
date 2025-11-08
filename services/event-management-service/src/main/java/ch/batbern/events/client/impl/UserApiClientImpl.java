package ch.batbern.events.client.impl;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.GetOrCreateUserRequest;
import ch.batbern.events.dto.UserProfileDTO;
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
    public UserProfileDTO getUserByUsername(String username) {
        log.debug("Fetching user profile for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username;

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserProfileDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    UserProfileDTO.class
            );

            UserProfileDTO user = response.getBody();
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
    public UserProfileDTO getOrCreateUser(GetOrCreateUserRequest request) {
        log.debug("Getting or creating user for email: {}", request.getEmail());

        String url = userServiceBaseUrl + "/api/v1/users/get-or-create";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<GetOrCreateUserRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<UserProfileDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpRequest,
                    UserProfileDTO.class
            );

            UserProfileDTO user = response.getBody();
            log.info("Successfully got/created user profile for email: {}, username: {}",
                    request.getEmail(), user != null ? user.getUsername() : "null");
            return user;

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
