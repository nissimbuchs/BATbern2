package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.dto.UserProfileDTO;
import ch.batbern.partners.exception.UserNotFoundException;
import ch.batbern.partners.exception.UserServiceException;
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
 * Implementation of UserServiceClient using Spring RestTemplate.
 *
 * Communicates with the Company User Management Service REST API to retrieve user profile data.
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
public class UserServiceClientImpl implements UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.base-url}")
    private String userServiceBaseUrl;

    /**
     * Get user profile by username.
     *
     * Cached for 15 minutes to minimize API calls.
     *
     * @param username User's username (unique identifier per ADR-003)
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
            log.error("Client error fetching user {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Service for user {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Service error for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Service for user {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Service for user: " + username,
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
                log.trace("JWT token propagated to User Service");
            } else {
                log.warn("No JWT token found in SecurityContext, principal type: {}",
                        principal != null ? principal.getClass().getSimpleName() : "null");
            }

        } catch (Exception e) {
            log.warn("Failed to extract JWT token from SecurityContext: {}", e.getMessage());
            // Continue without token - let the User Service handle authorization
        }

        return headers;
    }
}
