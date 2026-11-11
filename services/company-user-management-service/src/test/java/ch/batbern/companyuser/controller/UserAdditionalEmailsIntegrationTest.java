package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 *
 * <p>Story 4.10d: one address is addressed by its {@code id} ({@code {emailId}}), loaded scoped to
 * the caller; the address never goes in the path. Every test commits, {@code @AfterEach} removes
 * the two profiles (their addresses go with them, {@code ON DELETE CASCADE}).
 */
@SpringBootTest
@AutoConfigureMockMvc
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User caller;
    private User other;
    private UUID sharedId;

    @BeforeEach
    void setUp() {
        removeProfiles();
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

        // One additional email of the OTHER user — used by the cross-user duplicate test and as
        // the foreign id.
        sharedId = persistAddress(other, "shared@example.com", true);
    }

    @AfterEach
    void tearDown() {
        removeProfiles();
    }

    private void removeProfiles() {
        jdbcTemplate.update("DELETE FROM user_profiles WHERE username IN ('john.doe', 'mary.smith')");
    }

    private UUID persistAddress(User owner, String email, boolean verified) {
        return additionalEmailRepository.saveAndFlush(UserAdditionalEmail.builder()
                .user(owner)
                .email(email)
                .createdAt(Instant.now())
                .verifiedAt(verified ? Instant.now() : null)
                .build()).getId();
    }

    private UUID idOf(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user_additional_emails WHERE email = ?", UUID.class, email);
    }

    private boolean stored(String email) {
        return additionalEmailRepository.existsByEmailIgnoreCase(email);
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
                              "email": "info@batbern.ch",
                              "label": "Hostpoint shared"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.email").value("info@batbern.ch"))
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
                            { "email": "Info@Batbern.CH" }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("info@batbern.ch"));
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
                .andExpect(jsonPath("$.additionalEmails[0].id").value(idOf("work@example.com").toString()))
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

        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{emailId}", idOf("box@example.com")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalEmails", hasSize(0)));
        assertThat(stored("box@example.com")).isFalse();
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return404_when_deleteUnknownId")
    void should_return404_when_deleteUnknownId() throws Exception {
        persistAddress(caller, "keep@example.com", false);

        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{emailId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADDITIONAL_EMAIL_NOT_FOUND"));
        assertThat(stored("keep@example.com")).isTrue();
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return404_when_deleteForeignAdditional")
    void should_return404_when_deleteForeignAdditional() throws Exception {
        // shared@example.com belongs to mary.smith, not john.doe — must 404 for
        // john.doe (and never accidentally delete from mary's profile).
        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{emailId}", sharedId))
                .andExpect(status().isNotFound());

        // Mary still owns it.
        assertThat(jdbcTemplate.queryForObject("SELECT u.username FROM user_additional_emails a "
                + "JOIN user_profiles u ON u.id = a.user_id WHERE a.id = ?", String.class, sharedId))
                .isEqualTo("mary.smith");
    }

    @Test
    @DisplayName("should_return401_when_deleteAnonymously")
    void should_return401_when_deleteAnonymously() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{emailId}", sharedId))
                .andExpect(status().isUnauthorized());
        assertThat(stored("shared@example.com")).isTrue();
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("4.10d: the old address path is gone (400, the segment is no id), nothing removed")
    void should_return400_when_deleteByAddress() throws Exception {
        persistAddress(caller, "box@example.com", false);

        mockMvc.perform(delete("/api/v1/users/me/additional-emails/{email}", "box@example.com"))
                .andExpect(status().isBadRequest());
        assertThat(stored("box@example.com")).isTrue();
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
        persistAddress(caller, "cascade-1@example.com", false);
        persistAddress(caller, "cascade-2@example.com", false);

        assertThat(
                additionalEmailRepository.findAll())
                .extracting(UserAdditionalEmail::getEmail)
                .contains("cascade-1@example.com", "cascade-2@example.com");

        jdbcTemplate.update("DELETE FROM user_profiles WHERE id = ?", caller.getId());

        // The additional-emails rows for the deleted user must be gone.
        assertThat(
                additionalEmailRepository.findAll())
                .extracting(UserAdditionalEmail::getEmail)
                .doesNotContain("cascade-1@example.com", "cascade-2@example.com");
    }

    // ------------------- #1244: copy of event mails -------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("#1244: a new address gets no copy of event mails")
    void should_notReceiveEventMail_when_addressAdded() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"private@example.com\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiveEventMail").value(false));
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("#1244: a confirmed own address can switch the copy of event mails on and off")
    void should_toggleReceiveEventMail_when_ownAddressConfirmed() throws Exception {
        UUID id = persistAddress(caller, "private@example.com", true);

        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{emailId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("private@example.com"))
                .andExpect(jsonPath("$.receiveEventMail").value(true));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additionalEmails[0].receiveEventMail").value(true));

        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{emailId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": false }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiveEventMail").value(false));
        assertThat(additionalEmailRepository.findById(id)).get()
                .extracting(UserAdditionalEmail::isReceiveEventMail).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("#1244: an unconfirmed address cannot receive copies (409 NOT_VERIFIED)")
    void should_return409_when_switchingOnCopyForUnconfirmedAddress() throws Exception {
        UUID id = persistAddress(caller, "pending@example.com", false);

        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{emailId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": true }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_VERIFIED"));
        assertThat(additionalEmailRepository.findById(id)).get()
                .extracting(UserAdditionalEmail::isReceiveEventMail).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("#1244: another user's address is not found (404), its setting unchanged")
    void should_return404_when_switchingCopyForSomeoneElsesAddress() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{emailId}", sharedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": true }"))
                .andExpect(status().isNotFound());
        assertThat(additionalEmailRepository.findById(sharedId)).get()
                .extracting(UserAdditionalEmail::isReceiveEventMail).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("4.10d: an unknown id is not found (404)")
    void should_return404_when_switchingCopyForUnknownId() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{emailId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": false }"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("#1244: switching the copy needs a login (401)")
    void should_return401_when_switchingCopyAnonymously() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{emailId}", sharedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": true }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("4.10d: the old address path is gone (400, the segment is no id), setting unchanged")
    void should_return400_when_patchingByAddress() throws Exception {
        UUID id = persistAddress(caller, "private@example.com", true);

        mockMvc.perform(patch("/api/v1/users/me/additional-emails/{email}", "private@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"receiveEventMail\": true }"))
                .andExpect(status().isBadRequest());
        assertThat(additionalEmailRepository.findById(id)).get()
                .extracting(UserAdditionalEmail::isReceiveEventMail).isEqualTo(false);
    }
}
