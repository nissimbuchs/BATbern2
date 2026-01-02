package ch.batbern.events.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HTTP client for Company-User Management Service
 * Story BAT-7: Notifications API Consolidation
 *
 * Provides:
 * - User preferences retrieval
 * - Last login timestamp
 * - User email lookup
 * - Organizer user list
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${user-service.base-url}")
    private String userServiceBaseUrl;

    /**
     * Fetch user notification preferences
     */
    public UserPreferences getPreferences(String username) {
        String url = userServiceBaseUrl + "/api/v1/users/{username}/preferences";
        return restTemplate.getForObject(url, UserPreferences.class, username);
    }

    /**
     * Get user's last login timestamp (for in-app notifications)
     */
    public Instant getLastLogin(String username) {
        String url = userServiceBaseUrl + "/api/v1/users/{username}/last-login";
        return restTemplate.getForObject(url, Instant.class, username);
    }

    /**
     * Get user email address
     */
    public String getEmailByUsername(String username) {
        String url = userServiceBaseUrl + "/api/v1/users/{username}/email";
        return restTemplate.getForObject(url, String.class, username);
    }

    /**
     * Get all organizer usernames (for escalation)
     */
    public List<String> getOrganizerUsernames() {
        String url = userServiceBaseUrl + "/api/v1/users?role=ORGANIZER";
        UserListResponse response = restTemplate.getForObject(url, UserListResponse.class);

        if (response == null || response.getUsers() == null) {
            return List.of();
        }

        return response.getUsers().stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
    }

    // Inner DTOs for User Service responses

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserListResponse {
        private List<User> users;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class User {
        private String username;
        private String email;
        private String role;
    }
}
