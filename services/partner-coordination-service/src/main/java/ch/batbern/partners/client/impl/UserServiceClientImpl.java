package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.PaginatedUserResponse;
import ch.batbern.partners.client.user.dto.UserResponse;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Implementation of UserServiceClient using Spring RestTemplate.
 *
 * Communicates with the Company User Management Service REST API to retrieve user profile data.
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
     * Cached 15 minutes.
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

            log.debug("Successfully fetched user profile for username: {}", username);
            return response.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching user {}: {} - {}", username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException("Client error fetching user: " + username, e.getStatusCode().value(), e);

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Service for user {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Service error for user: " + username, e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Service for user {}: {}", username, e.getMessage());
            throw new UserServiceException("Failed to connect to User Service for user: " + username, e);

        } catch (Exception e) {
            log.error("Unexpected error fetching user {}: {}", username, e.getMessage(), e);
            throw new UserServiceException("Unexpected error fetching user: " + username, e);
        }
    }

    /**
     * Alias for getUserByUsername().
     */
    @Override
    public UserResponse getUserProfile(String username) {
        return getUserByUsername(username);
    }

    /**
     * Get the currently authenticated user's own profile via GET /users/me.
     * Accessible to any authenticated user regardless of role.
     */
    @Override
    public UserResponse getCurrentUserProfile() {
        log.debug("Fetching current user profile via /users/me");

        String url = userServiceBaseUrl + "/api/v1/users/me";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    UserResponse.class
            );

            log.debug("Successfully fetched current user profile");
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching current user profile: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException("Client error fetching current user profile", e.getStatusCode().value(), e);

        } catch (HttpServerErrorException e) {
            log.error("Server error fetching current user profile: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Service error fetching current user profile", e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Service for /users/me: {}", e.getMessage());
            throw new UserServiceException("Failed to connect to User Service for /users/me", e);

        } catch (Exception e) {
            log.error("Unexpected error fetching current user profile: {}", e.getMessage(), e);
            throw new UserServiceException("Unexpected error fetching current user profile", e);
        }
    }

    /**
     * List all users for a company with the given role.
     * Calls GET /users?company={companyName}&role={role}&limit=100.
     */
    @Override
    @Cacheable(value = "usersByCompanyRoleCache", key = "#companyName + '-' + #role")
    public List<UserResponse> getUsersByCompanyAndRole(String companyName, String role) {
        log.debug("Fetching users for company={}, role={}", companyName, role);

        String url = UriComponentsBuilder
                .fromHttpUrl(userServiceBaseUrl + "/api/v1/users")
                .queryParam("company", companyName)
                .queryParam("role", role)
                .queryParam("limit", 100)
                .toUriString();

        return fetchUserList(url, "company=" + companyName + ", role=" + role);
    }

    /**
     * List all users with the given role across all companies.
     * Calls GET /users?role={role}&limit=100.
     */
    @Override
    @Cacheable(value = "usersByRoleCache", key = "#role")
    public List<UserResponse> getUsersByRole(String role) {
        log.debug("Fetching all users with role={}", role);

        String url = UriComponentsBuilder
                .fromHttpUrl(userServiceBaseUrl + "/api/v1/users")
                .queryParam("role", role)
                .queryParam("limit", 100)
                .toUriString();

        return fetchUserList(url, "role=" + role);
    }

    private List<UserResponse> fetchUserList(String url, String context) {
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
                return Collections.emptyList();
            }

            log.debug("Fetched {} users for {}", body.getData().size(), context);
            return body.getData();

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching users ({}): {} - {}", context, e.getStatusCode(), e.getMessage());
            throw new UserServiceException("Client error fetching users: " + context, e.getStatusCode().value(), e);

        } catch (HttpServerErrorException e) {
            log.error("Server error fetching users ({}): {} - {}", context, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Service error fetching users: " + context, e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Network error fetching users ({}): {}", context, e.getMessage());
            throw new UserServiceException("Failed to connect to User Service: " + context, e);

        } catch (Exception e) {
            log.error("Unexpected error fetching users ({}): {}", context, e.getMessage(), e);
            throw new UserServiceException("Unexpected error fetching users: " + context, e);
        }
    }

    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();

        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                headers.set("Authorization", "Bearer " + jwt.getTokenValue());
                log.trace("JWT token propagated to User Service");
            } else {
                log.warn("No JWT token found in SecurityContext, principal type: {}",
                        principal != null ? principal.getClass().getSimpleName() : "null");
            }

        } catch (Exception e) {
            log.warn("Failed to extract JWT token from SecurityContext: {}", e.getMessage());
        }

        return headers;
    }
}
