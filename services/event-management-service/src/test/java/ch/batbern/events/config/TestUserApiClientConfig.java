package ch.batbern.events.config;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.UserProfileDTO;
import ch.batbern.events.exception.UserNotFoundException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration providing a mocked UserApiClient for integration tests.
 *
 * This configuration replaces the real UserApiClient with a mock to avoid
 * making actual HTTP calls to the User Management Service during tests.
 */
@TestConfiguration
public class TestUserApiClientConfig {

    /**
     * Create a mocked UserApiClient bean for tests.
     *
     * By default, returns a test user profile for any username.
     * Tests can override this behavior using @MockBean or Mockito.when().
     */
    @Bean
    @Primary
    public UserApiClient mockUserApiClient() {
        UserApiClient mock = mock(UserApiClient.class);

        // Default behavior: return a test user for any username
        when(mock.getUserByUsername(any(String.class)))
                .thenAnswer(invocation -> {
                    String username = invocation.getArgument(0);
                    return createTestUser(username);
                });

        // Default behavior: return true for any username validation
        when(mock.validateUserExists(any(String.class)))
                .thenReturn(true);

        return mock;
    }

    /**
     * Helper method to create a test user profile.
     *
     * @param username Username for the test user
     * @return UserProfileDTO with test data
     */
    public static UserProfileDTO createTestUser(String username) {
        return UserProfileDTO.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(username + "@test.com")
                .firstName("Test")
                .lastName("User")
                .companyId("TestCompany")
                .profilePictureUrl("https://cdn.test.com/profile.jpg")
                .bio("Test user bio")
                .active(true)
                .build();
    }

    /**
     * Helper method to configure mock to throw UserNotFoundException.
     *
     * Example usage in tests:
     * <pre>
     * when(userApiClient.getUserByUsername("invalid"))
     *     .thenThrow(new UserNotFoundException("invalid"));
     * </pre>
     */
    public static UserNotFoundException userNotFoundFor(String username) {
        return new UserNotFoundException(username);
    }
}
