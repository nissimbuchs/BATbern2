package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the two new Story 11.C.2 endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/users/provision} — Story 11.C.2 (AR13) — AC9 #7</li>
 *   <li>{@code PATCH /api/v1/users/{username}/profile} — Story 11.C.2 (AR14) — AC9 #8</li>
 * </ul>
 *
 * <p>Runs against real PostgreSQL via Testcontainers (per CLAUDE.md and project-context.md).
 * Covers idempotency, role-scope enforcement, and validation rejection.
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Story 11.C.2 — provision + patchProfile integration")
class UserProvisioningAndPatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    // AC1 / AC9 #7 — provisionUserWithRole
    // ============================================================

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("provision: creates new user + grants SPEAKER role when user does not exist")
    void should_createUserAndGrantRole_when_userDoesNotExist() throws Exception {
        String body = """
                {
                  "email": "newspeaker@example.com",
                  "firstName": "New",
                  "lastName": "Speaker",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(post("/api/v1/users/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", notNullValue()))
                .andExpect(jsonPath("$.created", is(true)))
                .andExpect(jsonPath("$.temporaryPassword", nullValue()));

        User created = userRepository.findByEmail("newspeaker@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getRoles()).contains(Role.SPEAKER);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("provision: idempotent — second call returns created=false, no duplicate role")
    void should_beIdempotent_when_provisionCalledTwice() throws Exception {
        String body = """
                {
                  "email": "idempotent@example.com",
                  "firstName": "Idem",
                  "lastName": "Potent",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(post("/api/v1/users/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));

        mockMvc.perform(post("/api/v1/users/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(false)));

        // User row still exists exactly once; role granted exactly once (Set semantics).
        User user = userRepository.findByEmail("idempotent@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getRoles()).contains(Role.SPEAKER);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("provision: grants additional role to existing user (idempotent role-add)")
    void should_grantRoleToExistingUser_when_alreadyExists() throws Exception {
        User existing = userRepository.save(User.builder()
                .username("jane.smith")
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .roles(new java.util.HashSet<>(Set.of(Role.ATTENDEE)))
                .build());

        String body = """
                {
                  "email": "jane@example.com",
                  "firstName": "Jane",
                  "lastName": "Smith",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(post("/api/v1/users/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(existing.getUsername())))
                .andExpect(jsonPath("$.created", is(false)));

        User reloaded = userRepository.findByEmail("jane@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getRoles()).contains(Role.SPEAKER, Role.ATTENDEE);
    }

    @Test
    @WithMockUser(username = "attendee.bob", roles = {"ATTENDEE"})
    @DisplayName("provision: rejects ATTENDEE caller (requires ORGANIZER/ADMIN)")
    void should_return403_when_callerLacksOrganizerOrAdminRole() throws Exception {
        String body = """
                {
                  "email": "rejected@example.com",
                  "firstName": "Re",
                  "lastName": "Jected",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(post("/api/v1/users/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // AC2 / AC9 #8 — patchUserProfile
    // ============================================================

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("patchProfile: ORGANIZER patches any user's bio")
    void should_return200_when_organizerPatchesBio() throws Exception {
        User target = userRepository.save(User.builder()
                .username("speaker.target")
                .email("target@example.com")
                .firstName("Tar")
                .lastName("Get")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());

        String body = """
                {
                  "bio": "New bio for the next event."
                }
                """;

        mockMvc.perform(patch("/api/v1/users/{username}/profile", target.getUsername())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio", is("New bio for the next event.")));

        User reloaded = userRepository.findByUsername(target.getUsername()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getBio()).isEqualTo("New bio for the next event.");
    }

    @Test
    @WithMockUser(username = "speaker.self", roles = {"SPEAKER"})
    @DisplayName("patchProfile: SPEAKER patches own profile — 200")
    void should_return200_when_speakerPatchesOwnProfile() throws Exception {
        userRepository.save(User.builder()
                .username("speaker.self")
                .email("self@example.com")
                .firstName("Self")
                .lastName("Speaker")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());

        String body = """
                {
                  "bio": "My own bio."
                }
                """;

        mockMvc.perform(patch("/api/v1/users/speaker.self/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio", is("My own bio.")));
    }

    @Test
    @WithMockUser(username = "speaker.self", roles = {"SPEAKER"})
    @DisplayName("patchProfile: SPEAKER patching another SPEAKER — 403")
    void should_return403_when_speakerPatchesAnotherSpeakerProfile() throws Exception {
        userRepository.save(User.builder()
                .username("speaker.other")
                .email("other@example.com")
                .firstName("Other")
                .lastName("Speaker")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());

        String body = """
                {
                  "bio": "Attempted cross-speaker patch."
                }
                """;

        mockMvc.perform(patch("/api/v1/users/speaker.other/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("patchProfile: empty patch (both fields null) — 400")
    void should_return400_when_bothFieldsAreNull() throws Exception {
        userRepository.save(User.builder()
                .username("speaker.empty")
                .email("empty@example.com")
                .firstName("Em")
                .lastName("Pty")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());

        String body = "{}";

        mockMvc.perform(patch("/api/v1/users/speaker.empty/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("patchProfile: non-existent username — 404")
    void should_return404_when_usernameDoesNotExist() throws Exception {
        String body = """
                {
                  "bio": "Will fail."
                }
                """;

        mockMvc.perform(patch("/api/v1/users/no.such.user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
