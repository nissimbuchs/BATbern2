package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 10.32 — integration tests for the additional-email endpoints on
 * {@code /api/v1/users/me/additional-emails}.
 *
 * <p>Test data is set up with two users: {@code john.doe} (the authenticated
 * caller) and {@code mary.smith} (an unrelated profile used to exercise the
 * "duplicate vs another user's additional email" case). Cap is overridden to 3
 * via {@link TestPropertySource} so the limit-reached test runs in 4 POSTs
 * instead of 6.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@TestPropertySource(properties = "batbern.user.additional-emails.max=3")
@DisplayName("Story 10.32 — UserController additional-emails endpoints")
class UserAdditionalEmailsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAdditionalEmailRepository additionalEmailRepository;

    private User caller;
    private User other;

    @BeforeEach
    void setUp() {
        // Test class is @Transactional — each method rolls back. The explicit
        // deleteAll() calls were redundant (and wasteful) for that reason.
        // Removed in review 2026-05-22 finding P2-3.
        caller = userRepository.save(User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("john.doe")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build());

        other = userRepository.save(User.builder()
                .username("mary.smith")
                .email("mary.smith@example.com")
                .firstName("Mary")
                .lastName("Smith")
                .cognitoUserId("mary.smith")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build());

        // Attach one additional email to the OTHER user — used by the
        // cross-user duplicate test.
        UserAdditionalEmail otherAdditional = UserAdditionalEmail.builder()
                .email("shared@example.com")
                .build();
        other.addAdditionalEmail(otherAdditional);
        userRepository.save(other);
    }

    // -------------------------- happy path --------------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_create_when_validEmailProvided")
    void should_create_when_validEmailProvided() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "info@berner-architekten-treffen.ch",
                              "label": "Hostpoint shared"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("info@berner-architekten-treffen.ch"))
                .andExpect(jsonPath("$.label").value("Hostpoint shared"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.verifiedAt").doesNotExist());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_normaliseEmailToLowercase_when_mixedCaseProvided")
    void should_normaliseEmailToLowercase_when_mixedCaseProvided() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "Info@Berner-Architekten-Treffen.CH" }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("info@berner-architekten-treffen.ch"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_returnAdditionalEmails_when_getCurrentUser")
    void should_returnAdditionalEmails_when_getCurrentUser() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"work@example.com\" }"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalEmails", hasSize(1)))
                .andExpect(jsonPath("$.additionalEmails[0].email").value("work@example.com"));
    }

    // -------------------------- duplicates --------------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return409_when_emailCollidesWithOwnPrimary")
    void should_return409_when_emailCollidesWithOwnPrimary() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"john.doe@example.com\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDITIONAL_EMAIL_DUPLICATE"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return409_when_emailCollidesWithOtherUsersPrimary")
    void should_return409_when_emailCollidesWithOtherUsersPrimary() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"mary.smith@example.com\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDITIONAL_EMAIL_DUPLICATE"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return409_when_emailCollidesWithOtherUsersAdditional")
    void should_return409_when_emailCollidesWithOtherUsersAdditional() throws Exception {
        // Other user already owns shared@example.com (set up in @BeforeEach)
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"shared@example.com\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDITIONAL_EMAIL_DUPLICATE"));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return409_when_emailCollidesWithSameUsersAdditional")
    void should_return409_when_emailCollidesWithSameUsersAdditional() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"home@example.com\" }"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"HOME@example.com\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDITIONAL_EMAIL_DUPLICATE"));
    }

    // -------------------------- cap (AC5) --------------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return422_when_capReached")
    void should_return422_when_capReached() throws Exception {
        // Cap = 3 (overridden via @TestPropertySource).
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/users/me/additional-emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"email\": \"box" + i + "@example.com\" }"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"box4@example.com\" }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ADDITIONAL_EMAIL_LIMIT_REACHED"));
    }

    // -------------------------- validation --------------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return400_when_emailFormatInvalid")
    void should_return400_when_emailFormatInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"not-an-email\" }"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------- auth --------------------------

    @Test
    @DisplayName("should_return401_when_notAuthenticated_onAdd")
    void should_return401_when_notAuthenticated_onAdd() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"x@example.com\" }"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------- delete --------------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return204_when_deleteExisting")
    void should_return204_when_deleteExisting() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"box@example.com\" }"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{email}", "box@example.com"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalEmails", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return204_when_deleteCaseInsensitive")
    void should_return204_when_deleteCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"info@example.com\" }"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{email}", "INFO@example.com"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return404_when_deleteUnknown")
    void should_return404_when_deleteUnknown() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{email}", "nothing@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return404_when_deleteForeignAdditional")
    void should_return404_when_deleteForeignAdditional() throws Exception {
        // shared@example.com belongs to mary.smith, not john.doe — must 404 for
        // john.doe (and never accidentally delete from mary's profile).
        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{email}", "shared@example.com"))
                .andExpect(status().isNotFound());

        // Mary still owns it.
        var mary = userRepository.findByUsername("mary.smith").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(mary.getAdditionalEmails())
                .extracting(UserAdditionalEmail::getEmail)
                .containsExactly("shared@example.com");
    }

    @Test
    @DisplayName("should_cascadeDeleteAdditionalEmails_when_userDeleted")
    void should_cascadeDeleteAdditionalEmails_when_userDeleted() throws Exception {
        // Story 10.32 (P3-10 from 2026-05-22 review): V16 declares
        // `ON DELETE CASCADE` on the FK from user_additional_emails →
        // user_profiles. This test pins the invariant: if a future migration
        // accidentally drops the FK or changes it to NO ACTION, additional
        // emails become orphaned rows that still satisfy the global LOWER(email)
        // unique index, permanently blocking other users from registering the
        // same address.
        UserAdditionalEmail row1 = UserAdditionalEmail.builder()
                .email("cascade-1@example.com")
                .build();
        UserAdditionalEmail row2 = UserAdditionalEmail.builder()
                .email("cascade-2@example.com")
                .build();
        caller.addAdditionalEmail(row1);
        caller.addAdditionalEmail(row2);
        userRepository.save(caller);
        userRepository.flush();

        org.assertj.core.api.Assertions.assertThat(
                additionalEmailRepository.findAll())
                .extracting(UserAdditionalEmail::getEmail)
                .contains("cascade-1@example.com", "cascade-2@example.com");

        userRepository.delete(caller);
        userRepository.flush();

        // The additional-emails rows for the deleted user must be gone.
        org.assertj.core.api.Assertions.assertThat(
                additionalEmailRepository.findAll())
                .extracting(UserAdditionalEmail::getEmail)
                .doesNotContain("cascade-1@example.com", "cascade-2@example.com");
    }
}
