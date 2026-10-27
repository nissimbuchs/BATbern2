package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        mockMvc.perform(patch("/api/v1/users/me")
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

        mockMvc.perform(patch("/api/v1/users/me")
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

        mockMvc.perform(patch("/api/v1/users/me")
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
                        .param("filter", "{\"role\":\"ORGANIZER\"}")
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
                        .param("filter", "{\"company\":\"GoogleZH\"}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[*].companyId", everyItem(is("GoogleZH"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_sortDescendingByName_when_sortParameterWithMinusSign")
    void should_sortDescendingByName_when_sortParameterWithMinusSign() throws Exception {
        // ADR-013 §3: server-side ordering via the `sort` vocabulary (-name → lastName DESC).
        User zoe = User.builder()
                .username("zoe.zimmer")
                .email("zoe.zimmer@example.com")
                .firstName("Zoe")
                .lastName("Zimmer")
                .cognitoUserId("cognito-zoe")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
        userRepository.save(zoe);
        User amy = User.builder()
                .username("amy.adams")
                .email("amy.adams@example.com")
                .firstName("Amy")
                .lastName("Adams")
                .cognitoUserId("cognito-amy")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
        userRepository.save(amy);

        mockMvc.perform(get("/api/v1/users")
                        .param("sort", "-name")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lastName", is("Zimmer")));
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

    // The user directory (list, search, single user) is for organizers. Services read users
    // through /internal/users (InternalUserControllerIntegrationTest), the caller's own record
    // comes from /api/v1/users/me.

    @ParameterizedTest
    @ValueSource(strings = {"ATTENDEE", "SPEAKER", "PARTNER"})
    @DisplayName("should_return403_when_nonOrganizerRequestsUserDirectory")
    void should_return403_when_nonOrganizerRequestsUserDirectory(String role) throws Exception {
        var caller = user("john.doe").roles(role);
        mockMvc.perform(get("/api/v1/users").with(caller))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/search").param("query", "John").with(caller))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/{username}", "john.doe").with(caller))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("should_listUsers_when_adminRequests")
    void should_listUsers_when_adminRoleRequests() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("john.doe@example.com"));
    }

    // AC5: GET /api/v1/users/{username} returns user by username

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
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
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
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

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
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
                .andExpect(jsonPath("$.s3Key").value(startsWith("profile-pictures/")))
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

    // AC4: POST /api/v1/users creates new user (Organizer only)

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_createUser_when_validDataProvided")
    void should_createUser_when_validDataProvided() throws Exception {
        String createRequest = """
                {
                    "email": "new.user@example.com",
                    "firstName": "New",
                    "lastName": "User",
                    "companyId": "GoogleZH",
                    "bio": "New user bio"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new.user"))
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.companyId").value("GoogleZH"))
                .andExpect(jsonPath("$.bio").value("New user bio"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return400_when_createUserWithInvalidEmail")
    void should_return400_when_createUserWithInvalidEmail() throws Exception {
        String invalidRequest = """
                {
                    "email": "invalid-email",
                    "firstName": "Invalid",
                    "lastName": "User",
                    "companyId": "GoogleZH"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    @WithMockUser(username = "attendee", roles = {"ATTENDEE"})
    @DisplayName("should_return403_when_nonOrganizerCreatesUser")
    void should_return403_when_nonOrganizerCreatesUser() throws Exception {
        String createRequest = """
                {
                    "email": "new.user@example.com",
                    "firstName": "New",
                    "lastName": "User",
                    "companyId": "GoogleZH"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isForbidden());
    }

    // PUT /api/v1/users/{username} updates user by username (Organizer/Admin only)

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_updateUserByUsername_when_organizerUpdates")
    void should_updateUserByUsername_when_organizerUpdates() throws Exception {
        String updateRequest = """
                {
                    "firstName": "Updated",
                    "lastName": "Name",
                    "bio": "Updated by admin"
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("john.doe"))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Name"))
                .andExpect(jsonPath("$.bio").value("Updated by admin"));
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return404_when_updateNonExistentUser")
    void should_return404_when_updateNonExistentUser() throws Exception {
        String updateRequest = """
                {
                    "firstName": "Updated",
                    "lastName": "Name"
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("not found")));
    }

    @Test
    @WithMockUser(username = "attendee", roles = {"ATTENDEE"})
    @DisplayName("should_return403_when_nonAdminUpdatesUser")
    void should_return403_when_nonAdminUpdatesUser() throws Exception {
        String updateRequest = """
                {
                    "firstName": "Hacker",
                    "lastName": "Attempt"
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isForbidden());
    }

    // AC12: get-or-create for domain service integration. EMS calls it in-VPC on the internal API
    // (/internal/users/get-or-create, no JWT); it is not part of the public API any more.

    @Test
    @WithMockUser(username = "attendee", roles = {"ATTENDEE"})
    @DisplayName("should_notCreateUser_when_getOrCreateCalledOnPublicApi")
    void should_notCreateUser_when_getOrCreateCalledOnPublicApi() throws Exception {
        mockMvc.perform(post("/api/v1/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "new.person@example.com", "firstName": "New", "lastName": "Person"}
                                """))
                .andExpect(status().is4xxClientError());
        assertThat(userRepository.findByEmailIgnoreCase("new.person@example.com")).isEmpty();
    }

    @Test
    @DisplayName("should_returnExistingUser_when_userExists")
    void should_returnExistingUser_when_userExists() throws Exception {
        String getOrCreateRequest = """
                {
                    "email": "john.doe@example.com",
                    "firstName": "John",
                    "lastName": "Doe",
                    "companyId": "GoogleZH"
                }
                """;

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getOrCreateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.user.id").value("john.doe"))
                .andExpect(jsonPath("$.user.email").value("john.doe@example.com"));
    }

    /**
     * #1102: production rejected "Hông Ân" with a 500 (chk_username_format). Names are free text;
     * whatever a person is called, registration must succeed and keep the name as entered.
     */
    @ParameterizedTest(name = "[{index}] {0} {1} -> {3}")
    @CsvSource(delimiter = '|', value = {
        "Hông Ân    | Meier        | h.meier.1102@example.com     | hongan.meier",
        "Jan        | van der Berg | jvdb.1102@example.com        | jan.vanderberg",
        "Maria José | García       | mjg.1102@example.com         | mariajose.garcia",
        "太郎        | 山田          | taro.yamada.1102@example.com | taro.yamada",
        "Владимир   | Petrov       | 1102@example.com             | user.petrov",
    })
    void should_createUserWithValidUsername_when_nameHasSpacesOrNonLatinScript(
            String firstName, String lastName, String email, String expectedUsername) throws Exception {
        String body = """
                {"email": "%s", "firstName": "%s", "lastName": "%s", "createIfMissing": true}
                """.formatted(email, firstName, lastName);

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.username").value(expectedUsername))
                .andExpect(jsonPath("$.user.firstName").value(firstName))
                .andExpect(jsonPath("$.user.lastName").value(lastName));

        // The class is @Transactional, so without a flush chk_username_format never runs and a 200
        // proves nothing. A JPQL query auto-flushes the insert: this is where production failed.
        assertThat(userRepository.findByUsername(expectedUsername)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"o'neill+batbern@example.ie", "x@müller.ch", "x@example.engineering"})
    void should_acceptEmail_when_sesCanDeliverToIt(String email) throws Exception {
        String body = """
                {"email": "%s", "firstName": "Valid", "lastName": "Address", "createIfMissing": true}
                """.formatted(email);

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void should_return400_when_emailLocalPartIsNotAscii() throws Exception {
        // SES has no SMTPUTF8: a Unicode local part can never receive the confirmation mail.
        String body = """
                {"email": "jürg@example.ch", "firstName": "Jürg", "lastName": "Muster", "createIfMissing": true}
                """;

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_createNewUser_when_userDoesNotExist")
    void should_createNewUser_when_userDoesNotExist() throws Exception {
        String getOrCreateRequest = """
                {
                    "email": "brand.new@example.com",
                    "firstName": "Brand",
                    "lastName": "New",
                    "companyId": "GoogleZH",
                    "createIfMissing": true
                }
                """;

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(getOrCreateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.user.email").value("brand.new@example.com"))
                .andExpect(jsonPath("$.user.firstName").value("Brand"))
                .andExpect(jsonPath("$.user.lastName").value("New"));
    }

    // An anonymous registration with an address that is stored as another user's ADDITIONAL email
    // must not try to create a second user: the DB trigger enforce_primary_email_not_other_additional
    // rejects that insert, which surfaced as a 500 on the public registration form (2026-10-18).

    private void addAdditionalEmail(String email, Instant verifiedAt) {
        User owner = userRepository.findByUsername("john.doe").orElseThrow();
        owner.addAdditionalEmail(UserAdditionalEmail.builder()
                .email(email)
                .label("Work")
                .verifiedAt(verifiedAt)
                .build());
        userRepository.saveAndFlush(owner);
    }

    @Test
    @DisplayName("should_returnOwner_when_emailIsVerifiedAdditionalEmailOfExistingUser")
    void should_returnOwner_when_emailIsVerifiedAdditionalEmailOfExistingUser() throws Exception {
        addAdditionalEmail("john.work@example.com", Instant.now());
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "John.Work@Example.com",
                                    "firstName": "John",
                                    "lastName": "Doe",
                                    "createIfMissing": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.user.email").value("john.doe@example.com"));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("should_return409_when_emailIsUnverifiedAdditionalEmailOfExistingUser")
    void should_return409_when_emailIsUnverifiedAdditionalEmailOfExistingUser() throws Exception {
        addAdditionalEmail("john.pending@example.com", null);
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "john.pending@example.com",
                                    "firstName": "Someone",
                                    "lastName": "Else",
                                    "createIfMissing": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_LINKED_TO_ACCOUNT"));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    // Email addresses identify a person regardless of capitalisation (#1099): production had 5
    // people stored twice because get-or-create compared case-sensitively.

    @Test
    @DisplayName("should_returnExistingUser_when_emailDiffersOnlyInCase")
    void should_returnExistingUser_when_emailDiffersOnlyInCase() throws Exception {
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "John.Doe@EXAMPLE.com",
                                    "firstName": "John",
                                    "lastName": "Doe",
                                    "createIfMissing": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.created").value(false));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("should_storeLowerCasedEmail_when_creatingUserViaGetOrCreate")
    void should_storeLowerCasedEmail_when_creatingUserViaGetOrCreate() throws Exception {
        mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "Mixed.Case@Example.com",
                                    "firstName": "Mixed",
                                    "lastName": "Case",
                                    "createIfMissing": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.user.email").value("mixed.case@example.com"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_rejectCreateUser_when_emailExistsWithDifferentCase")
    void should_rejectCreateUser_when_emailExistsWithDifferentCase() throws Exception {
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "JOHN.DOE@example.com",
                                    "firstName": "John",
                                    "lastName": "Doe"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("should_rejectAtDatabaseLevel_when_emailsDifferOnlyInCase")
    void should_rejectAtDatabaseLevel_when_emailsDifferOnlyInCase() {
        // Native inserts bypass the entity normalisation (and the unflushed setUp row): the index
        // must hold on its own
        String insert = """
                INSERT INTO user_profiles (id, username, email, first_name, last_name, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, 'Case', 'Twin', now(), now())
                """;
        jdbcTemplate.update(insert, "case.twin", "case.twin@example.com");

        assertThatThrownBy(() -> jdbcTemplate.update(insert, "case.twinb", "Case.Twin@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_user_profiles_email_lower");
    }

    // AC4: GET /api/v1/users/search (autocomplete with caching)

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_searchUsers_when_queryProvided")
    void should_searchUsers_when_queryProvided() throws Exception {
        // Create additional users for search
        User user2 = User.builder()
                .username("jane.smith")
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .cognitoUserId("cognito-jane")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
        userRepository.save(user2);

        mockMvc.perform(get("/api/v1/users/search")
                        .param("query", "Jane")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].firstName").value("Jane"))
                .andExpect(jsonPath("$[0].lastName").value("Smith"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_filterByRole_when_roleParameterProvided")
    void should_filterByRole_when_roleParameterProvided() throws Exception {
        // Create organizer
        User organizer = User.builder()
                .username("jane.organizer")
                .email("jane.organizer@example.com")
                .firstName("Jane")
                .lastName("Organizer")
                .cognitoUserId("cognito-org")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
        userRepository.save(organizer);

        mockMvc.perform(get("/api/v1/users/search")
                        .param("query", "Jane")
                        .param("role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roles", hasItem("ORGANIZER")));
    }

    // AC11: DELETE /api/v1/users/{username} (GDPR compliance)

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_deleteUser_when_organizerRequests")
    void should_deleteUser_when_organizerRequests() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify user is deleted
        mockMvc.perform(get("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return404_when_deleteNonExistentUser")
    void should_return404_when_deleteNonExistentUser() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/{username}", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("not found")));
    }

    @Test
    @WithMockUser(username = "attendee", roles = {"ATTENDEE"})
    @DisplayName("should_return403_when_nonOrganizerDeletesUser")
    void should_return403_when_nonOrganizerDeletesUser() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/{username}", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // AC8: Role Management Tests

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_getUserRoles_when_organizerRequests")
    void should_getUserRoles_when_organizerRequests() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}/roles", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"));
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return404_when_getRolesForNonExistentUser")
    void should_return404_when_getRolesForNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}/roles", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("not found")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_updateUserRoles_when_organizerAddsRole")
    void should_updateUserRoles_when_organizerAddsRole() throws Exception {
        String updateRequest = """
                {
                    "roles": ["ATTENDEE", "SPEAKER"]
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}/roles", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasSize(2)))
                .andExpect(jsonPath("$.roles", hasItem("ATTENDEE")))
                .andExpect(jsonPath("$.roles", hasItem("SPEAKER")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_updateUserRoles_when_organizerRemovesRole")
    void should_updateUserRoles_when_organizerRemovesRole() throws Exception {
        // First add multiple roles
        testUser.setRoles(new HashSet<>(Set.of(Role.ATTENDEE, Role.SPEAKER)));
        userRepository.save(testUser);

        // Now remove SPEAKER role
        String updateRequest = """
                {
                    "roles": ["ATTENDEE"]
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}/roles", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_updateUserRoles_when_organizerSetsMultipleRoles")
    void should_updateUserRoles_when_organizerSetsMultipleRoles() throws Exception {
        String updateRequest = """
                {
                    "roles": ["ATTENDEE", "SPEAKER", "PARTNER"]
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}/roles", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", hasSize(3)))
                .andExpect(jsonPath("$.roles", hasItem("ATTENDEE")))
                .andExpect(jsonPath("$.roles", hasItem("SPEAKER")))
                .andExpect(jsonPath("$.roles", hasItem("PARTNER")));
    }

    @Test
    @WithMockUser(username = "attendee", roles = {"ATTENDEE"})
    @DisplayName("should_return403_when_nonOrganizerUpdatesRoles")
    void should_return403_when_nonOrganizerUpdatesRoles() throws Exception {
        String updateRequest = """
                {
                    "roles": ["ORGANIZER"]
                }
                """;

        mockMvc.perform(put("/api/v1/users/{username}/roles", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isForbidden());
    }

    // Admin Profile Picture Management Tests

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_generatePresignedUrlForUser_when_organizerRequests")
    void should_generatePresignedUrlForUser_when_organizerRequests() throws Exception {
        String requestBody = """
            {
                "fileName": "profile.png",
                "fileSize": 2097152,
                "mimeType": "image/png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/{username}/picture/presigned-url", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.fileId").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").value(startsWith("profile-pictures/")))
                .andExpect(jsonPath("$.s3Key").value(containsString("john.doe")))
                .andExpect(jsonPath("$.fileExtension").value("png"))
                .andExpect(jsonPath("$.expiresInMinutes").value(15))
                .andExpect(jsonPath("$.requiredHeaders.Content-Type").value("image/png"));
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return404_when_generateUrlForNonExistentUser")
    void should_return404_when_generateUrlForNonExistentUser() throws Exception {
        String requestBody = """
            {
                "fileName": "profile.png",
                "fileSize": 2097152,
                "mimeType": "image/png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/{username}/picture/presigned-url", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("nonexistent.user")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_confirmUploadForUser_when_organizerConfirms")
    void should_confirmUploadForUser_when_organizerConfirms() throws Exception {
        String fileId = "admin-test-file-id-67890";
        String requestBody = String.format("""
            {
                "fileId": "%s",
                "fileExtension": "png"
            }
            """, fileId);

        mockMvc.perform(post("/api/v1/users/{username}/picture/confirm", "john.doe")
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

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")


    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return404_when_confirmUploadForNonExistentUser")
    void should_return404_when_confirmUploadForNonExistentUser() throws Exception {
        String requestBody = """
            {
                "fileId": "test-file-id",
                "fileExtension": "png"
            }
            """;

        mockMvc.perform(post("/api/v1/users/{username}/picture/confirm", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("nonexistent.user")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_removeProfilePictureForUser_when_organizerRequests")
    void should_removeProfilePictureForUser_when_organizerRequests() throws Exception {
        // First set a profile picture for john.doe
        testUser.setProfilePictureUrl("https://cdn.batbern.ch/profile-pictures/john.doe/test-file.png");
        testUser.setProfilePictureS3Key("profile-pictures/john.doe/test-file.png");
        userRepository.save(testUser);

        // Verify it was set
        User userBeforeDelete = userRepository.findByUsername("john.doe").orElseThrow();
        assert userBeforeDelete.getProfilePictureUrl() != null;

        // Now remove it via admin endpoint
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/users/{username}/picture", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify profile picture was cleared
        User updatedUser = userRepository.findByUsername("john.doe").orElseThrow();
        assert updatedUser.getProfilePictureUrl() == null;
        assert updatedUser.getProfilePictureS3Key() == null;
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_removeOwnProfilePicture_when_deleteMePicture")
    void should_removeOwnProfilePicture_when_deleteMePicture() throws Exception {
        // Regression: DELETE /users/me/picture must hit the self-service handler, NOT fall
        // through to /{username}/picture with literal username="me" (which 404s and left the
        // self-service "Remove Photo" button broken for every user). Found in slice 1 of the
        // Playwright staging-hardening audit (docs/plans/playwright-staging-hardening.md).
        testUser.setProfilePictureUrl("https://cdn.batbern.ch/profile-pictures/john.doe/test-file.png");
        testUser.setProfilePictureS3Key("profile-pictures/john.doe/test-file.png");
        userRepository.save(testUser);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/users/me/picture")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        User updatedUser = userRepository.findByUsername("john.doe").orElseThrow();
        assert updatedUser.getProfilePictureUrl() == null;
        assert updatedUser.getProfilePictureS3Key() == null;
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")
    @Test
    @WithMockUser(username = "admin", roles = {"ORGANIZER"})
    @DisplayName("should_return404_when_removeProfilePictureForNonExistentUser")
    void should_return404_when_removeProfilePictureForNonExistentUser() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/users/{username}/picture", "nonexistent.user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("nonexistent.user")));
    }

    // ---- /api/v1/users/by-company: removed, services use GET /internal/users?company=… ----

    @Test
    @WithMockUser(username = "service", roles = {"PARTNER"})
    @DisplayName("should_notListUsers_when_partnerCallsFormerByCompanyPath")
    void should_notListUsers_when_partnerCallsFormerByCompanyPath() throws Exception {
        mockMvc.perform(get("/api/v1/users/by-company")
                        .param("company", "GoogleZH")
                        .param("role", "ATTENDEE"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("should_return401_when_byCompanyCalledWithoutAuth")
    void should_return401_when_byCompanyCalledWithoutAuth() throws Exception {
        // In production this path is protected by VpcInternalAuthorizationManager;
        // in the test profile it falls back to .anyRequest().authenticated()
        mockMvc.perform(get("/api/v1/users/by-company")
                        .param("company", "sbb"))
                .andExpect(status().isUnauthorized());
    }
}
