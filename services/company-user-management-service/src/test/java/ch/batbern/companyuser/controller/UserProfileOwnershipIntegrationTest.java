package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.10: a user's profile and picture are one set, {@code /users/{username}}, with one
 * ownership rule (the picture upload: purpose {@code profile-picture}, Story 17.11a). The user
 * themself may edit their own; an organizer (profile: also an admin) any;
 * everyone else gets 403. Consent ({@code termsAccepted}) is self only. The {@code /users/me}
 * twins and {@code PUT /users/{username}} are gone.
 *
 * <p>Runs the production security chain; every test commits, {@code @AfterEach} removes the rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@RecordApplicationEvents
@DisplayName("Story 4.10: /users/{username} profile and picture ownership")
class UserProfileOwnershipIntegrationTest extends AbstractIntegrationTest {

    private static final String SPEAKER = "john.doe";
    private static final String SPEAKER_SUB = "6f1c2d3e-0000-4000-8000-00000000a001";
    private static final String OTHER = "jane.smith";
    private static final String ORGANIZER = "organizer.alice";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM watch_pairings");
        jdbcTemplate.update("DELETE FROM user_profiles");
        save(SPEAKER, "John", "Doe", SPEAKER_SUB, Role.SPEAKER);
        save(OTHER, "Jane", "Smith", "6f1c2d3e-0000-4000-8000-00000000a002", Role.ATTENDEE);
        save(ORGANIZER, "Alice", "Organizer", "6f1c2d3e-0000-4000-8000-00000000a003", Role.ORGANIZER);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM company_user_uploads WHERE purpose = 'profile-picture' AND created_by IN (?, ?)",
                SPEAKER, ORGANIZER);
        jdbcTemplate.update("DELETE FROM watch_pairings");
        jdbcTemplate.update("DELETE FROM user_profiles");
    }

    private void save(String username, String first, String last, String sub, Role role) {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName(first)
                .lastName(last)
                .cognitoUserId(sub)
                .bio("Old bio")
                .roles(new HashSet<>(Set.of(role)))
                .build());
    }

    private User stored(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    // --- PATCH /users/{username} ------------------------------------------------------------

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_updateOwnProfile_when_speakerPatchesOwnUsername() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{username}", SPEAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Neue Bio über Brünnimann\", \"firstName\": \"Hông Ân\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SPEAKER))
                .andExpect(jsonPath("$.bio").value("Neue Bio über Brünnimann"));

        assertThat(stored(SPEAKER).getBio()).isEqualTo("Neue Bio über Brünnimann");
        assertThat(stored(SPEAKER).getFirstName()).isEqualTo("Hông Ân");
        assertThat(stored(SPEAKER).getLastName()).isEqualTo("Doe");
        assertThat(applicationEvents.stream(UserUpdatedEvent.class))
                .extracting(UserUpdatedEvent::getAggregateId, UserUpdatedEvent::getUserId)
                .containsExactly(tuple(SPEAKER, SPEAKER));
        assertThat(applicationEvents.stream(UserUpdatedEvent.class).findFirst().orElseThrow()
                .getUpdatedFields()).containsOnlyKeys("bio", "firstName");
    }

    @Test
    void should_updateOwnProfile_when_tokenHasNoUsernameClaimButOwnSubject() throws Exception {
        // The first token after JIT provisioning carries no custom:username; the subject still
        // names the user (the fallback GET /users/me uses), so the consent gate keeps working.
        mockMvc.perform(patch("/api/v1/users/{username}", SPEAKER)
                        .with(jwt().jwt(j -> j.subject(SPEAKER_SUB))
                                .authorities(new SimpleGrantedAuthority("ROLE_SPEAKER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": true}"))
                .andExpect(status().isOk());

        assertThat(stored(SPEAKER).getTermsAcceptedAt()).isNotNull();
        assertThat(applicationEvents.stream(UserUpdatedEvent.class))
                .extracting(UserUpdatedEvent::getUserId)
                .containsExactly(SPEAKER);
    }

    @Test
    void should_return403_when_tokenSubjectBelongsToAnotherUser() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .with(jwt().jwt(j -> j.subject(SPEAKER_SUB))
                                .authorities(new SimpleGrantedAuthority("ROLE_SPEAKER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Hijacked\"}"))
                .andExpect(status().isForbidden());

        assertThat(stored(OTHER).getBio()).isEqualTo("Old bio");
    }

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_return403_when_speakerPatchesAnotherProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Hijacked\"}"))
                .andExpect(status().isForbidden());

        assertThat(stored(OTHER).getBio()).isEqualTo("Old bio");
        assertThat(applicationEvents.stream(UserUpdatedEvent.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_clearBioToNull_when_blankBioSent() throws Exception {
        // A client restores "no bio" by sending "" (the e2e specs do, on shared accounts): it is
        // stored as NULL, the form a profile had before its first bio, not as "".
        mockMvc.perform(patch("/api/v1/users/{username}", SPEAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT bio IS NULL FROM user_profiles WHERE username = ?",
                Boolean.class, SPEAKER)).isTrue();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_return409_when_emailIsAnotherUsersAddress() throws Exception {
        // uq_user_profiles_email_lower: one address, one profile (case-insensitive). The 409 was
        // declared on the removed PATCH /users/me and must hold on PATCH /users/{username}.
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"John.Doe@Example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_DUPLICATE"));

        assertThat(stored(OTHER).getEmail()).isEqualTo(OTHER + "@example.com");
        assertThat(applicationEvents.stream(UserUpdatedEvent.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_updateAnotherProfile_when_organizerPatches() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Edited by the organizer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Edited by the organizer"));

        assertThat(stored(OTHER).getBio()).isEqualTo("Edited by the organizer");
        // The actor is the organizer's username, not a Cognito subject (ADR-003).
        assertThat(applicationEvents.stream(UserUpdatedEvent.class))
                .extracting(UserUpdatedEvent::getAggregateId, UserUpdatedEvent::getUserId)
                .containsExactly(tuple(OTHER, ORGANIZER));
    }

    @Test
    void should_recordOrganizerUsername_when_organizerJwtCarriesUsernameClaim() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .with(jwt().jwt(j -> j.subject("6f1c2d3e-0000-4000-8000-00000000a003")
                                        .claim("custom:username", ORGANIZER))
                                .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\": \"O’Neill\"}"))
                .andExpect(status().isOk());

        assertThat(stored(OTHER).getLastName()).isEqualTo("O’Neill");
        assertThat(applicationEvents.stream(UserUpdatedEvent.class))
                .extracting(UserUpdatedEvent::getUserId)
                .containsExactly(ORGANIZER);
    }

    @Test
    @WithMockUser(username = "admin.user", roles = "ADMIN")
    void should_updateAnotherProfile_when_adminPatches() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Edited by an admin\"}"))
                .andExpect(status().isOk());

        assertThat(stored(OTHER).getBio()).isEqualTo("Edited by an admin");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_return403_when_organizerGivesConsentForAnotherUser() throws Exception {
        // Positive control: the same organizer may edit that profile ...
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Allowed\"}"))
                .andExpect(status().isOk());

        // ... but consent is the user's own.
        mockMvc.perform(patch("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": true, \"bio\": \"Sneaked\"}"))
                .andExpect(status().isForbidden());

        User other = stored(OTHER);
        assertThat(other.getTermsAcceptedAt()).isNull();
        assertThat(other.getBio()).isEqualTo("Allowed");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_recordConsent_when_organizerAcceptsForThemself() throws Exception {
        Instant before = Instant.now().minusSeconds(2);
        mockMvc.perform(patch("/api/v1/users/{username}", ORGANIZER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": true}"))
                .andExpect(status().isOk());

        assertThat(stored(ORGANIZER).getTermsAcceptedAt()).isAfter(before);
    }

    // --- /users/{username}/picture* -----------------------------------------------------------

    // Uploads run on the upload contract, purpose profile-picture (Story 17.11a): the same ownership
    // rule, through the handler. Upload and attach once per test here; the rest of the matrix is in
    // ProfilePictureUploadIntegrationTest.

    private void uploadPicture(String username, int expectedCreate, int expectedConfirm) throws Exception {
        String body = "{\"fileName\": \"portrait.png\", \"contentType\": \"image/png\", \"size\": 2097152,"
                + " \"target\": {\"username\": \"" + username + "\"}}";
        String created = mockMvc.perform(post("/api/v1/uploads/profile-picture")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedCreate))
                .andReturn().getResponse().getContentAsString();
        if (expectedCreate != 201) {
            return;
        }
        String uploadId = JsonPath.read(created, "$.uploadId");
        mockMvc.perform(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\": {\"username\": \"" + username + "\"}}"))
                .andExpect(status().is(expectedConfirm));
    }

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_manageOwnPicture_when_speakerUsesOwnUsername() throws Exception {
        uploadPicture(SPEAKER, 201, 200);
        assertThat(stored(SPEAKER).getProfilePictureS3Key()).startsWith("profile-pictures/" + SPEAKER + "/profile-");

        mockMvc.perform(delete("/api/v1/users/{username}/picture", SPEAKER))
                .andExpect(status().isNoContent());
        assertThat(stored(SPEAKER).getProfilePictureUrl()).isNull();
        assertThat(stored(SPEAKER).getProfilePictureS3Key()).isNull();
    }

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_return403_when_speakerTouchesAnotherPicture() throws Exception {
        jdbcTemplate.update("UPDATE user_profiles SET profile_picture_url = ?, profile_picture_s3_key = ? "
                        + "WHERE username = ?",
                "https://cdn.batbern.ch/profile-pictures/2025/jane.smith/p.png",
                "profile-pictures/2025/jane.smith/p.png", OTHER);

        uploadPicture(OTHER, 403, 0);
        mockMvc.perform(delete("/api/v1/users/{username}/picture", OTHER))
                .andExpect(status().isForbidden());

        assertThat(stored(OTHER).getProfilePictureS3Key()).isEqualTo("profile-pictures/2025/jane.smith/p.png");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_manageAnotherPicture_when_organizer() throws Exception {
        uploadPicture(OTHER, 201, 200);
        assertThat(stored(OTHER).getProfilePictureS3Key()).startsWith("profile-pictures/" + OTHER + "/profile-");
        mockMvc.perform(delete("/api/v1/users/{username}/picture", OTHER))
                .andExpect(status().isNoContent());
        assertThat(stored(OTHER).getProfilePictureS3Key()).isNull();
    }

    @Test
    @WithMockUser(username = "admin.user", roles = "ADMIN")
    void should_return403_when_adminWithoutOrganizerTouchesAnotherPicture() throws Exception {
        // Pictures: ORGANIZER or self (unchanged from the former organizer endpoints).
        mockMvc.perform(delete("/api/v1/users/{username}/picture", OTHER))
                .andExpect(status().isForbidden());
    }

    // --- removed paths --------------------------------------------------------------------------

    /**
     * The removed {@code /users/me} write paths now match the {@code /users/{username}} templates
     * with the username "me", which no user has (chk_username_format). The ownership rule runs
     * first: a non-organizer gets 403; an organizer passes it and fails the username pattern, 400.
     * Either way nothing changes. (Not 404/405: the templates exist for real usernames.)
     */
    private void callRemovedSelfPaths(org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Via the old path\"}"))
                .andExpect(expected);
        // The /users/me/picture upload twins are gone with the upload operations (Story 17.11a): no
        // template matches, 404 for everyone.
        mockMvc.perform(post("/api/v1/users/me/picture/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"portrait.png\", \"fileSize\": 2097152, \"mimeType\": \"image/png\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/users/me/picture/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"old-path-file\", \"fileExtension\": \"png\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/users/me/picture"))
                .andExpect(expected);
    }

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_return403_when_speakerCallsRemovedSelfPaths() throws Exception {
        callRemovedSelfPaths(status().isForbidden());

        assertThat(stored(SPEAKER).getBio()).isEqualTo("Old bio");
        assertThat(stored(SPEAKER).getProfilePictureS3Key()).isNull();
        assertThat(applicationEvents.stream(UserUpdatedEvent.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_return400_when_organizerCallsRemovedSelfPaths() throws Exception {
        callRemovedSelfPaths(status().isBadRequest());

        assertThat(stored(ORGANIZER).getBio()).isEqualTo("Old bio");
        assertThat(stored(ORGANIZER).getProfilePictureS3Key()).isNull();
        assertThat(applicationEvents.stream(UserUpdatedEvent.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_return405_when_putUserIsCalled() throws Exception {
        mockMvc.perform(put("/api/v1/users/{username}", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Via PUT\"}"))
                .andExpect(status().isMethodNotAllowed());

        assertThat(stored(OTHER).getBio()).isEqualTo("Old bio");
    }
}
