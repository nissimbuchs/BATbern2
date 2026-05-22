package ch.batbern.events.client.impl;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.CompanyBasicDto;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.PaginatedUserResponse;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
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
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

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
        // Early guard: an empty/null username yields the URL "/api/v1/users/" which
        // returns a 500 from User Management (route mismatch). This caused the
        // 2026-05-18 attendee-registration outage when pre-token-generation Lambda
        // fell back to an empty `custom:username` claim and the JWT carried that
        // through to event-management. Fail fast with a clear UserNotFoundException
        // so callers and metrics see the real reason.
        if (username == null || username.isEmpty()) {
            log.warn("Refusing getUserByUsername call with empty username "
                    + "(likely missing custom:username JWT claim from pre-token-generation)");
            throw new UserNotFoundException("");
        }
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
     * Provision a User with a role (idempotent).
     * Story 11.C.2 (AR13). See {@link UserApiClient#provisionUserWithRole(ProvisionUserRequest)}.
     *
     * <p>Not cached: this is a write operation. Successful calls evict any cached
     * lookup for the target username because the role set has changed.
     */
    @Override
    public ProvisionUserResponse provisionUserWithRole(ProvisionUserRequest request) {
        log.debug("Provisioning user (Story 11.C.2): email={}, role={}",
                request.getEmail(), request.getRole());

        String url = userServiceBaseUrl + "/api/v1/users/provision";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            headers.set("Content-Type", "application/json");
            HttpEntity<ProvisionUserRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<ProvisionUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpRequest,
                    ProvisionUserResponse.class
            );

            ProvisionUserResponse result = response.getBody();
            log.info("Provisioned user: email={}, username={}, created={}",
                    request.getEmail(),
                    result != null ? result.getUsername() : "null",
                    result != null ? result.getCreated() : "null");
            // P1 (review patch): use CacheManager directly. Self-invoking `this.evictUserCache(...)`
            // bypasses Spring's AOP proxy → @CacheEvict never fires. Programmatic eviction is the
            // only reliable way to evict from within the same bean.
            if (result != null && result.getUsername() != null) {
                evictUserCacheEntry(result.getUsername());
            }
            return result;

        } catch (HttpClientErrorException e) {
            log.error("Client error provisioning user {}: {} - {}",
                    request.getEmail(), e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error provisioning user: " + request.getEmail(),
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for provision {}: {} - {}",
                    request.getEmail(), e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error provisioning user: " + request.getEmail(),
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for provision {}: {}",
                    request.getEmail(), e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + request.getEmail(),
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error provisioning user {}: {}", request.getEmail(), e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error provisioning user: " + request.getEmail(),
                    e
            );
        }
    }

    /**
     * Patch user profile fields (bio, profilePictureUrl).
     * Story 11.C.2 (AR14). See {@link UserApiClient#patchUserProfile(String, PatchUserProfileRequest)}.
     *
     * <p>Successful calls evict the {@code userApiCache} entry for the target username
     * because the underlying User profile has changed.
     */
    @Override
    @CacheEvict(value = "userApiCache", key = "#username")
    public UserResponse patchUserProfile(String username, PatchUserProfileRequest request) {
        log.debug("Patching user profile (Story 11.C.2) for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username + "/profile";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            headers.set("Content-Type", "application/json");
            HttpEntity<PatchUserProfileRequest> httpRequest = new HttpEntity<>(request, headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    httpRequest,
                    UserResponse.class
            );

            UserResponse user = response.getBody();
            log.info("Successfully patched user profile for username: {}", username);
            return user;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found for profile patch: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error patching user profile {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error patching user profile: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error patching user profile {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error patching profile for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error patching user profile {}: {}", username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error patching user profile {}: {}", username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error patching user profile: " + username,
                    e
            );
        }
    }

    /**
     * Issue (or skip) Cognito temp credentials at READY → INVITED.
     * Story 11.E.2 (AR15, FR9). See {@link UserApiClient#issueInvitationCredentials(String)}.
     *
     * <p>Not cached: this is a write operation on the Cognito side. The temp password in
     * the response is never persisted client-side — the caller embeds it in the invitation
     * email and discards from memory.
     */
    @Override
    public InvitationCredentialsResponse issueInvitationCredentials(String username) {
        log.debug("Issuing invitation credentials (Story 11.E.2) for username: {}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username + "/issue-invitation-credentials";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            headers.set("Content-Type", "application/json");
            HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

            ResponseEntity<InvitationCredentialsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpRequest,
                    InvitationCredentialsResponse.class
            );

            InvitationCredentialsResponse result = response.getBody();
            // Story 11.E.2 review patch (P9 / B12): enforce the FRESH_TEMP_PASSWORD invariant
            // that the OpenAPI schema cannot express (nullable + required: [action] only).
            // If CUMS ever regresses and returns action=FRESH_TEMP_PASSWORD with a null
            // temporaryPassword, the invitation email would render with an empty password
            // block and the speaker would be locked out silently. Fail fast at the boundary.
            if (result == null) {
                throw new UserServiceException(
                        "issueInvitationCredentials returned an empty body for: " + username);
            }
            if (result.getAction() == InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD
                    && (result.getTemporaryPassword() == null || result.getTemporaryPassword().isBlank())) {
                throw new UserServiceException(
                        "CUMS returned action=FRESH_TEMP_PASSWORD with no temporaryPassword for: "
                                + username);
            }
            log.info("Issued invitation credentials for username={} (action={})",
                    username,
                    result.getAction());
            return result;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found for invitation credentials: {}", username);
            throw new UserNotFoundException(username, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error issuing invitation credentials for {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error issuing invitation credentials for user: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error issuing invitation credentials for {}: {} - {}",
                    username, e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error issuing invitation credentials: " + username,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error issuing invitation credentials for {}: {}",
                    username, e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for user: " + username,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error issuing invitation credentials for {}: {}",
                    username, e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error issuing invitation credentials: " + username,
                    e
            );
        }
    }

    /**
     * Programmatically evict the cached User entry for a username after a write operation
     * (Story 11.C.2). Uses {@link CacheManager} directly so eviction works correctly from
     * within the same bean — the previous helper used `@CacheEvict` on a method that was
     * self-invoked, which bypasses Spring's AOP proxy and silently did nothing.
     */
    private void evictUserCacheEntry(String username) {
        if (username == null) {
            return;
        }
        Cache cache = cacheManager.getCache("userApiCache");
        if (cache != null) {
            cache.evict(username);
            log.trace("Evicted userApiCache entry for username: {}", username);
        }
    }

    /**
     * Get all partner usernames.
     * Used for auto-enrolling all PARTNER-role users when a new event is created.
     */
    @Override
    public java.util.List<String> getPartnerUsernames() {
        log.debug("Fetching partner usernames");

        String url = userServiceBaseUrl + "/api/v1/users?role=PARTNER&limit=1000";

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
                log.debug("No partners found");
                return java.util.List.of();
            }

            java.util.List<String> usernames = body.getData().stream()
                    .map(UserResponse::getId)
                    .collect(java.util.stream.Collectors.toList());

            log.debug("Successfully fetched {} partner usernames", usernames.size());
            return usernames;

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching partner list: {} - {}", e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "Client error fetching partner list",
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from User Management Service for partner list: {} - {}",
                    e.getStatusCode(), e.getMessage());
            throw new UserServiceException(
                    "User Management Service error fetching partner list",
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to User Management Service for partner list: {}",
                    e.getMessage());
            throw new UserServiceException(
                    "Failed to connect to User Management Service for partner list",
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching partner list: {}", e.getMessage(), e);
            throw new UserServiceException(
                    "Unexpected error fetching partner list",
                    e
            );
        }
    }

    /**
     * Get all companies (basic info) from company-user-management-service.
     * Story 10.20: AC1 — companies[] list in legacy BAT export envelope.
     */
    @Override
    public java.util.List<CompanyBasicDto> getAllCompanies() {
        log.debug("Fetching all companies for legacy export");

        String url = userServiceBaseUrl + "/api/v1/companies?limit=1000";

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.debug("No companies found");
                return java.util.List.of();
            }

            // Parse paginated response: { "data": [...], "pagination": {...} }
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode dataNode = root.path("data");

            if (dataNode.isMissingNode() || !dataNode.isArray()) {
                log.debug("No companies data in response");
                return java.util.List.of();
            }

            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(java.util.List.class, CompanyBasicDto.class);
            java.util.List<CompanyBasicDto> companies = objectMapper.convertValue(dataNode, listType);

            log.debug("Successfully fetched {} companies", companies.size());
            return companies;

        } catch (HttpClientErrorException e) {
            log.warn("Client error fetching companies (non-fatal for export): {} - {}",
                    e.getStatusCode(), e.getMessage());
            return java.util.List.of();

        } catch (HttpServerErrorException e) {
            log.warn("Server error fetching companies (non-fatal for export): {} - {}",
                    e.getStatusCode(), e.getMessage());
            return java.util.List.of();

        } catch (ResourceAccessException e) {
            log.warn("Network error fetching companies (non-fatal for export): {}", e.getMessage());
            return java.util.List.of();

        } catch (Exception e) {
            log.warn("Unexpected error fetching companies (non-fatal for export): {}", e.getMessage());
            return java.util.List.of();
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
