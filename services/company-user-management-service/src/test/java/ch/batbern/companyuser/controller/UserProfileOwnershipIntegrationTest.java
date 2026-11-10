package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
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
 * ownership rule. The user themself may edit their own; an organizer (profile: also an admin) any;
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

    @Test
    @WithMockUser(username = SPEAKER, roles = "SPEAKER")
    void should_manageOwnPicture_when_speakerUsesOwnUsername() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/picture/presigned-url", SPEAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"portrait.png\", \"fileSize\": 2097152, \"mimeType\": \"image/png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s3Key").value(org.hamcrest.Matchers.containsString(SPEAKER)));

        mockMvc.perform(post("/api/v1/users/{username}/picture/confirm", SPEAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"own-file-1\", \"fileExtension\": \"png\"}"))
                .andExpect(status().isOk());
        assertThat(stored(SPEAKER).getProfilePictureS3Key()).contains("own-file-1");

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

        mockMvc.perform(post("/api/v1/users/{username}/picture/presigned-url", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"portrait.png\", \"fileSize\": 2097152, \"mimeType\": \"image/png\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/{username}/picture/confirm", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"foreign-file\", \"fileExtension\": \"png\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/{username}/picture", OTHER))
                .andExpect(status().isForbidden());

        assertThat(stored(OTHER).getProfilePictureS3Key()).isEqualTo("profile-pictures/2025/jane.smith/p.png");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    void should_manageAnotherPicture_when_organizer() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/picture/presigned-url", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"portrait.png\", \"fileSize\": 2097152, \"mimeType\": \"image/png\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/{username}/picture/confirm", OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"organizer-file\", \"fileExtension\": \"png\"}"))
                .andExpect(status().isOk());
        assertThat(stored(OTHER).getProfilePictureS3Key()).contains("organizer-file");
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
        mockMvc.perform(post("/api/v1/users/me/picture/presigned-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"portrait.png\", \"fileSize\": 2097152, \"mimeType\": \"image/png\"}"))
                .andExpect(expected);
        mockMvc.perform(post("/api/v1/users/me/picture/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"old-path-file\", \"fileExtension\": \"png\"}"))
                .andExpect(expected);
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
