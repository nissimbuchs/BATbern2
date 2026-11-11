package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.PaginatedUserResponse;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.exception.UserNotFoundException;
import ch.batbern.partners.exception.UserServiceException;
import ch.batbern.shared.security.S2sHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of UserServiceClient using Spring RestTemplate.
 *
 * Reads user profiles from the Company User Management Service's internal API
 * ({@code docs/api/internal/cums-internal.openapi.yml}). Every call carries the headers of
 * {@link S2sHeaders#forCurrentUser()}: the user's JWT when there is one, none otherwise (ADR-016
 * decision 3). The internal reads do not depend on the caller, so the cached answers are the same
 * for everyone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClientImpl implements UserServiceClient {

    private final RestTemplate restTemplate;

    /**
     * CUMS internal user API: VPC-only, not routed by the gateway, a forwarded JWT optional. The
     * public {@code /api/v1/users} directory is organizer-only, and these results are cached for
     * every caller, so they must not depend on whose request triggered the lookup: the internal
     * operations authorize by operation, never by the forwarded user.
     */
    private static final String INTERNAL_USERS_PATH = "/internal/users";

    /**
     * Page size asked of {@code GET /internal/users}. The internal list declares and enforces no
     * maximum (cums-internal, {@code InternalUserController}); 100 matches its default and EMS.
     */
    private static final int PAGE_SIZE = 100;

    /** Upper bound on pages read, so a misbehaving hasNext can never loop forever. */
    private static final int MAX_PAGES = 100;

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

        // The username is one strictly encoded path segment (17.0b2; concatenated before).
        URI url = UriComponentsBuilder.fromUriString(userServiceBaseUrl + INTERNAL_USERS_PATH + "/{username}")
                .encode().buildAndExpand(username).toUri();

        try {
            HttpEntity<Void> request = new HttpEntity<>(S2sHeaders.forCurrentUser());

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
     * List all users for a company with the given role.
     * Calls the CUMS internal API {@code GET /internal/users?company=…&role=…}, page by page.
     */
    @Override
    @Cacheable(value = "usersByCompanyRoleCache", key = "#companyName + '-' + #role")
    public List<UserResponse> getUsersByCompanyAndRole(String companyName, String role) {
        log.debug("Fetching users for company={}, role={}", companyName, role);
        return fetchAllPages(companyName, role, "company=" + companyName + ", role=" + role);
    }

    /**
     * List all users with the given role across all companies.
     * Calls the CUMS internal API {@code GET /internal/users?role=…}, page by page.
     */
    @Override
    @Cacheable(value = "usersByRoleCache", key = "#role")
    public List<UserResponse> getUsersByRole(String role) {
        log.debug("Fetching all users with role={}", role);
        return fetchAllPages(null, role, "role=" + role);
    }

    /**
     * Every page of {@code GET /internal/users} for the filters (17.0b2). Before, PCS read only the
     * first page: invites and contact lists stopped silently at 100 users. Follows
     * {@code pagination.hasNext}, at most {@link #MAX_PAGES} pages, like EMS
     * {@code UserApiClientImpl}. Filter values are strictly encoded, so a {@code +} or {@code &}
     * in a company name stays part of the value.
     */
    private List<UserResponse> fetchAllPages(String company, String role, String context) {
        List<UserResponse> users = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("role", role);
            vars.put("page", page);
            vars.put("limit", PAGE_SIZE);
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(userServiceBaseUrl + INTERNAL_USERS_PATH)
                    .queryParam("role", "{role}")
                    .queryParam("page", "{page}")
                    .queryParam("limit", "{limit}");
            if (company != null) {
                builder.queryParam("company", "{company}");
                vars.put("company", company);
            }
            PaginatedUserResponse body = fetchUserPage(builder.encode().buildAndExpand(vars).toUri(), context);
            if (body == null || body.getData() == null) {
                break;
            }
            users.addAll(body.getData());
            if (body.getPagination() == null || !Boolean.TRUE.equals(body.getPagination().getHasNext())) {
                break;
            }
        }
        log.debug("Fetched {} users for {}", users.size(), context);
        return users;
    }

    /** One page from the CUMS internal API. */
    private PaginatedUserResponse fetchUserPage(URI url, String context) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(S2sHeaders.forCurrentUser());

            ResponseEntity<PaginatedUserResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    PaginatedUserResponse.class
            );
            return response.getBody();

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
}
