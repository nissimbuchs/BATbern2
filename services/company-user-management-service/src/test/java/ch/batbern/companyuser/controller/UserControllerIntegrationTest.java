package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserController
 * Tests REST API endpoints with security and validation
 *
 * Story 1.14-2 Task 11: REST Controllers (RED phase)
 * AC: 1, 2, 3, 5
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        userRepository.deleteAll();

        // Create test user
        // Note: @WithMockUser(username = "john.doe") returns "john.doe" from SecurityContextHelper.getCurrentUserId()
        // So we set cognitoUserId to "john.doe" to match
        testUser = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("john.doe")  // Match @WithMockUser username
                .companyId("GoogleZH")
                .bio("Test bio")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();

        testUser = userRepository.save(testUser);
    }

    // AC1: GET /api/v1/users/me returns authenticated user

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_returnCurrentUser_when_authenticated")
    void should_returnCurrentUser_when_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.companyId").value("GoogleZH"))
                .andExpect(jsonPath("$.bio").value("Test bio"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"));
    }

    @Test
    @DisplayName("should_return401_when_notAuthenticated")
    void should_return401_when_notAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_expandCompany_when_includeCompanyProvided")
    void should_expandCompany_when_includeCompanyProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .param("include", "company")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.company").exists())
                .andExpect(jsonPath("$.company.id").value("GoogleZH"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_expandPreferences_when_includePreferencesProvided")
    void should_expandPreferences_when_includePreferencesProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .param("include", "preferences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.preferences").exists())
                .andExpect(jsonPath("$.preferences.theme").exists())
                .andExpect(jsonPath("$.preferences.language").exists());
    }

    // AC2: PUT /api/v1/users/me updates current user with validation

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_updateUserProfile_when_validDataProvided")
    void should_updateUserProfile_when_validDataProvided() throws Exception {
        String updateRequest = """
                {
                    "firstName": "Jane",
                    "lastName": "Smith",
                    "bio": "Updated bio"
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.bio").value("Updated bio"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_validateEmail_when_emailUpdated")
    void should_validateEmail_when_emailUpdated() throws Exception {
        String invalidEmailRequest = """
                {
                    "email": "invalid-email"
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEmailRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return400_when_invalidDataProvided")
    void should_return400_when_invalidDataProvided() throws Exception {
        String invalidRequest = """
                {
                    "firstName": ""
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    // AC3: GET /api/v1/users lists users (admin/organizer only)

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_listUsers_when_adminRequests")
    void should_listUsers_when_adminRequests() throws Exception {
        // Create additional test users
        User user2 = User.builder()
                .username("jane.smith")
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .cognitoUserId("cognito-456")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
        userRepository.save(user2);

        mockMvc.perform(get("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.pagination").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_filterByRole_when_roleFilterProvided")
    void should_filterByRole_when_roleFilterProvided() throws Exception {
        // Create organizer
        User organizer = User.builder()
                .username("organizer.user")
                .email("organizer@example.com")
                .firstName("Organizer")
                .lastName("User")
                .cognitoUserId("cognito-789")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
        userRepository.save(organizer);

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].roles", hasItem("ORGANIZER")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_filterByCompany_when_companyFilterProvided")
    void should_filterByCompany_when_companyFilterProvided() throws Exception {
        // Create user with different company
        User user2 = User.builder()
                .username("bob.jones")
                .email("bob.jones@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .cognitoUserId("cognito-999")
                .companyId("MicrosoftZH")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
        userRepository.save(user2);

        mockMvc.perform(get("/api/v1/users")
                        .param("company", "GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[*].companyId", everyItem(is("GoogleZH"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_respectLimitParameter_when_paginationRequested")
    void should_respectLimitParameter_when_paginationRequested() throws Exception {
        // Create 30 users
        for (int i = 1; i <= 30; i++) {
            User user = User.builder()
                    .username("test.user." + i)  // Fixed: username must match format ^[a-z]+\.[a-z]+(\.[0-9]+)?$
                    .email("user" + i + "@example.com")
                    .firstName("User")
                    .lastName("" + i)
                    .cognitoUserId("cognito-" + i)
                    .companyId("GoogleZH")
                    .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                    .build();
            userRepository.save(user);
        }

        // Request page 1 (first page) with limit=10
        mockMvc.perform(get("/api/v1/users")
                        .param("page", "1")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(10)))  // CRITICAL: Must respect limit
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.totalItems").value(31))  // 30 + testUser from setUp
                .andExpect(jsonPath("$.pagination.totalPages").value(4))
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.pagination.hasPrev").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_returnCorrectPage_when_pageParameterProvided")
    void should_returnCorrectPage_when_pageParameterProvided() throws Exception {
        // Create 25 users
        for (int i = 1; i <= 25; i++) {
            User user = User.builder()
                    .username("test.user." + i)  // Fixed: username must match format ^[a-z]+\.[a-z]+(\.[0-9]+)?$
                    .email("user" + i + "@example.com")
                    .firstName("User")
                    .lastName("" + i)
                    .cognitoUserId("cognito-" + i)
                    .companyId("GoogleZH")
                    .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                    .build();
            userRepository.save(user);
        }

        // Request page 2 (second page) with limit=10 (should get 10 users)
        mockMvc.perform(get("/api/v1/users")
                        .param("page", "2")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(10)))  // CRITICAL: Page 2 should have 10 items
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.totalItems").value(26))  // 25 + testUser
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.pagination.hasPrev").value(true));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_returnDefaultPageSize_when_limitNotProvided")
    void should_returnDefaultPageSize_when_limitNotProvided() throws Exception {
        // Create 25 users
        for (int i = 1; i <= 25; i++) {
            User user = User.builder()
                    .username("test.user." + i)  // Fixed: username must match format ^[a-z]+\.[a-z]+(\.[0-9]+)?$
                    .email("user" + i + "@example.com")
                    .firstName("User")
                    .lastName("" + i)
                    .cognitoUserId("cognito-" + i)
                    .companyId("GoogleZH")
                    .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                    .build();
            userRepository.save(user);
        }

        // Request page 1 without limit (should default to 20)
        mockMvc.perform(get("/api/v1/users")
                        .param("page", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(20)))  // Default limit=20
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    @DisplayName("should_return403_when_nonAdminRequestsUserList")
    void should_return403_when_nonAdminRequestsUserList() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // AC5: GET /api/v1/users/{username} returns user by username

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_getUserByUsername_when_userExists")
    void should_getUserByUsername_when_userExists() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_expandCompanyAndRoles_when_includeProvided")
    void should_expandCompanyAndRoles_when_includeProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}", "john.doe")
                        .param("include", "company,roles")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.company").exists())
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return404_when_userNotFound")
    void should_return404_when_userNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("nonexistent.user")));
    }

    @Test
    @DisplayName("should_return401_when_notAuthenticatedForGetUser")
    void should_return401_when_notAuthenticatedForGetUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // AC10: Profile Picture Upload Tests

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_generatePresignedUrl_when_validFileProvided")
    void should_generatePresignedUrl_when_validFileProvided() throws Exception {
        String requestBody = """
            {
                "fileName": "profile.png",
                "fileSize": 2097152,
                "mimeType": "image/png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/me/picture/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.fileId").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").value(containsString("/profile-pictures/")))
                .andExpect(jsonPath("$.s3Key").value(containsString("john.doe")))
                .andExpect(jsonPath("$.fileExtension").value("png"))
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andExpect(jsonPath("$.requiredHeaders.Content-Type").value("image/png"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return400_when_fileSizeExceeds5MB")
    void should_return400_when_fileSizeExceeds5MB() throws Exception {
        String requestBody = """
            {
                "fileName": "profile.png",
                "fileSize": 6291456,
                "mimeType": "image/png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/me/picture/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("5MB")));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return400_when_invalidFileType")
    void should_return400_when_invalidFileType() throws Exception {
        String requestBody = """
            {
                "fileName": "document.pdf",
                "fileSize": 1048576,
                "mimeType": "application/pdf"
            }
            """;

        mockMvc.perform(post("/api/v1/users/me/picture/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("PNG, JPG, JPEG, SVG")));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_confirmUpload_when_validFileIdProvided")
    void should_confirmUpload_when_validFileIdProvided() throws Exception {
        String fileId = "test-file-id-12345";
        String requestBody = String.format("""
            {
                "fileId": "%s",
                "fileExtension": "png"
            }
            """, fileId);

        mockMvc.perform(post("/api/v1/users/me/picture/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePictureUrl").isNotEmpty())
                .andExpect(jsonPath("$.profilePictureUrl").value(containsString("cdn.batbern.ch")))
                .andExpect(jsonPath("$.profilePictureUrl").value(containsString("/profile-pictures/")))
                .andExpect(jsonPath("$.profilePictureUrl").value(containsString("john.doe")))
                .andExpect(jsonPath("$.profilePictureUrl").value(containsString(fileId)));

        // Verify user entity was updated
        User updatedUser = userRepository.findByUsername("john.doe").orElseThrow();
        assert updatedUser.getProfilePictureUrl() != null;
        assert updatedUser.getProfilePictureUrl().contains("cdn.batbern.ch");
        assert updatedUser.getProfilePictureS3Key() != null;
        assert updatedUser.getProfilePictureS3Key().contains(fileId);
    }

    @Test
    @DisplayName("should_return401_when_notAuthenticatedForPresignedUrl")
    void should_return401_when_notAuthenticatedForPresignedUrl() throws Exception {
        String requestBody = """
            {
                "fileName": "profile.png",
                "fileSize": 2097152,
                "mimeType": "image/png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/me/picture/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_return401_when_notAuthenticatedForConfirm")
    void should_return401_when_notAuthenticatedForConfirm() throws Exception {
        String requestBody = """
            {
                "fileId": "test-file-id",
                "fileExtension": "png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/me/picture/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }
}
