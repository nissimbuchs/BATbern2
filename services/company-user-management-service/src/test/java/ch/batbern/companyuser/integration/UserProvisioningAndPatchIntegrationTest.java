package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.events.UserCreatedEvent;
import ch.batbern.companyuser.events.UserUpdatedEvent;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the three speaker writes EMS calls on CUMS, on their internal paths since
 * Epic 17 entry 6 (ADR-016):
 * <ul>
 *   <li>{@code POST /internal/users/provision} — Story 11.C.2 (AR13)</li>
 *   <li>{@code PATCH /internal/users/{username}/profile} — Story 11.C.2 (AR14)</li>
 *   <li>{@code POST /internal/users/{username}/issue-invitation-credentials} — Story 11.E.2</li>
 * </ul>
 *
 * <p>Runs against real PostgreSQL via Testcontainers and the production security chain. Every
 * operation runs once with a forwarded user JWT and once without any (scheduler, SQS, anonymous
 * flow): both succeed, and the write records the user or {@code system:<job>}. The operations
 * authorize by operation, not by role: provision grants SPEAKER only, whoever calls.
 */
@Transactional
@Import(TestAwsConfig.class)
@RecordApplicationEvents
@DisplayName("Internal provision, profile patch and invitation credentials")
class UserProvisioningAndPatchIntegrationTest extends AbstractIntegrationTest {

    private static final String PROVISION = "/internal/users/provision";
    private static final String PROFILE = "/internal/users/{username}/profile";
    private static final String CREDENTIALS = "/internal/users/{username}/issue-invitation-credentials";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;
    // The in-memory Cognito pool from TestAwsConfig; the real CognitoIntegrationServiceImpl runs
    // on it. Story 11.E.2 tests assert the login it holds after provisioning.
    @Autowired
    private InMemoryCognito cognito;
    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void resetCognito() {
        cognito.reset();
    }

    /** The organizer's JWT, forwarded by EMS from the request the organizer made there. */
    private static MockHttpServletRequestBuilder asOrganizer(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("organizer-sub").claim("custom:username", "organizer.alice")
                .claim("custom:role", "ORGANIZER")));
    }

    /** A JWT without any role, to prove the operation never looks at it. */
    private static MockHttpServletRequestBuilder asAttendee(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("attendee-sub").claim("custom:username", "attendee.bob")
                .claim("custom:role", "ATTENDEE")));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ============================================================
    // AC1 / AC9 #7 — provisionUserWithRole
    // ============================================================

    @Test
    @DisplayName("provision with the organizer's JWT: creates the user, grants SPEAKER, records the organizer")
    void should_createUserAndGrantRole_when_userDoesNotExist() throws Exception {
        String body = """
                {
                  "email": "newspeaker@example.com",
                  "firstName": "New",
                  "lastName": "Speaker",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", notNullValue()))
                .andExpect(jsonPath("$.created", is(true)))
                // Story 11.E.2: temporaryPassword field removed from ProvisionUserResponse
                // (credential issuance moved to the sibling /issue-invitation-credentials endpoint).
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        User created = userRepository.findByEmailIgnoreCase("newspeaker@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getRoles()).contains(Role.SPEAKER);
        org.assertj.core.api.Assertions.assertThat(applicationEvents.stream(UserCreatedEvent.class))
                .extracting(UserCreatedEvent::getUserId).containsExactly("organizer.alice");
    }

    @Test
    @DisplayName("provision without any JWT (scheduler, SQS, test thread): succeeds, records system:ems-speaker-workflow")
    void should_provision_when_noJwt() throws Exception {
        mockMvc.perform(json(post(PROVISION), """
                        {"email": "no.jwt@example.com", "firstName": "No", "lastName": "Jwt", "role": "SPEAKER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));

        User created = userRepository.findByEmailIgnoreCase("no.jwt@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getRoles()).contains(Role.SPEAKER);
        org.assertj.core.api.Assertions.assertThat(applicationEvents.stream(UserCreatedEvent.class))
                .extracting(UserCreatedEvent::getUserId).containsExactly("system:ems-speaker-workflow");
    }

    @Test
    @DisplayName("provision with a role-less JWT: the operation does not look at the caller's role")
    void should_provision_when_jwtCarriesNoOrganizerRole() throws Exception {
        mockMvc.perform(json(asAttendee(post(PROVISION)), """
                        {"email": "any.caller@example.com", "role": "SPEAKER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));
    }

    @Test
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

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(false)));

        // User row still exists exactly once; role granted exactly once (Set semantics).
        User user = userRepository.findByEmailIgnoreCase("idempotent@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getRoles()).contains(Role.SPEAKER);
    }

    @Test
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

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(existing.getUsername())))
                .andExpect(jsonPath("$.created", is(false)));

        User reloaded = userRepository.findByEmailIgnoreCase("jane@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getRoles()).contains(Role.SPEAKER, Role.ATTENDEE);
    }

    // ============================================================
    // AC2 / AC9 #8 — patchUserProfile
    // ============================================================

    @Test
    @DisplayName("patchProfile with the forwarded JWT: patches the bio, records that user")
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

        mockMvc.perform(asOrganizer(patch(PROFILE, target.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio", is("New bio for the next event.")));

        User reloaded = userRepository.findByUsername(target.getUsername()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getBio()).isEqualTo("New bio for the next event.");
        org.assertj.core.api.Assertions.assertThat(applicationEvents.stream(UserUpdatedEvent.class))
                .extracting(UserUpdatedEvent::getUserId).containsExactly("organizer.alice");
    }

    @Test
    @DisplayName("patchProfile without any JWT: succeeds, records system:ems-content-submission")
    void should_return200_when_patchedWithoutJwt() throws Exception {
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

        mockMvc.perform(patch(PROFILE, "speaker.self")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio", is("My own bio.")));

        org.assertj.core.api.Assertions.assertThat(applicationEvents.stream(UserUpdatedEvent.class))
                .extracting(UserUpdatedEvent::getUserId).containsExactly("system:ems-content-submission");
    }

    @Test
    @DisplayName("patchProfile: unknown properties — 400")
    void should_return400_when_patchRequestContainsUnknownFields() throws Exception {
        userRepository.save(User.builder()
                .username("speaker.self")
                .email("self@example.com")
                .firstName("Self")
                .lastName("Speaker")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());

        mockMvc.perform(json(patch(PROFILE, "speaker.self"), "{\"bio\": \"Bio\", \"department\": \"Engineering\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
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

        mockMvc.perform(asOrganizer(patch(PROFILE, "speaker.empty"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("patchProfile: non-existent username — 404")
    void should_return404_when_usernameDoesNotExist() throws Exception {
        String body = """
                {
                  "bio": "Will fail."
                }
                """;

        mockMvc.perform(asOrganizer(patch(PROFILE, "ghost.user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // Review patches (P1 idempotency, P0 SPEAKER only, P2 stale fields)
    // ============================================================

    @Test
    @DisplayName("provision: idempotent across email case variants (review patch P1)")
    void should_beIdempotent_when_emailCaseDiffers() throws Exception {
        String firstBody = """
                {
                  "email": "Jane.Mixed@Example.COM",
                  "firstName": "Jane",
                  "lastName": "Mixed",
                  "role": "SPEAKER"
                }
                """;
        String secondBody = """
                {
                  "email": "jane.mixed@example.com",
                  "firstName": "Jane",
                  "lastName": "Mixed",
                  "role": "SPEAKER"
                }
                """;

        // First call — creates user with normalized email.
        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));

        // Second call (different case) — must hit the same row.
        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(false)));

        // Exactly one row should exist, with the canonical lowercase email.
        long countLower = userRepository.findByEmailIgnoreCase("jane.mixed@example.com").stream().count();
        org.assertj.core.api.Assertions.assertThat(countLower).isEqualTo(1L);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}, admin JWT: {1}")
    @org.junit.jupiter.params.provider.CsvSource({
        "ORGANIZER, false", "PARTNER, false", "ATTENDEE, false", "ORGANIZER, true", "PARTNER, true"})
    @DisplayName("provision grants SPEAKER only — any other role is 400, whoever calls (no ADMIN branch)")
    void should_return400_when_roleIsNotSpeaker(String role, boolean adminJwt) throws Exception {
        MockHttpServletRequestBuilder request = post(PROVISION);
        if (adminJwt) {
            request = request.with(jwt().jwt(j -> j.subject("admin-sub").claim("custom:username", "admin.alice")
                    .claim("custom:role", "ADMIN")));
        }

        mockMvc.perform(json(request, """
                        {"email": "should.fail@example.com", "role": "%s"}""".formatted(role)))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmailIgnoreCase("should.fail@example.com"))
                .isEmpty();
    }

    // ============================================================
    // Story 11.E.2 — issue-invitation-credentials
    // ============================================================

    private User seedSpeaker(String username, String email) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .firstName("Cred")
                .lastName("Speaker")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());
    }

    @Test
    @DisplayName("credentials with the organizer's JWT: a FORCE_CHANGE_PASSWORD login gets a fresh password")
    void should_issueFreshPassword_when_calledWithJwt() throws Exception {
        seedSpeaker("cred.speaker", "cred.speaker@example.com");
        cognito.givenLogin("cred.speaker@example.com",
                software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType.FORCE_CHANGE_PASSWORD);

        String body = mockMvc.perform(asOrganizer(post(CREDENTIALS, "cred.speaker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action", is("FRESH_TEMP_PASSWORD")))
                .andExpect(jsonPath("$.temporaryPassword", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        // The password the speaker will receive is the one the pool now holds.
        org.assertj.core.api.Assertions.assertThat(cognito.login("cred.speaker@example.com").orElseThrow().password())
                .isEqualTo(objectMapper.readTree(body).get("temporaryPassword").asText());
    }

    @Test
    @DisplayName("credentials without any JWT: a CONFIRMED login keeps its password")
    void should_keepExistingPassword_when_calledWithoutJwt() throws Exception {
        seedSpeaker("cred.confirmed", "cred.confirmed@example.com");
        cognito.givenLogin("cred.confirmed@example.com",
                software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType.CONFIRMED);

        mockMvc.perform(post(CREDENTIALS, "cred.confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action", is("USE_EXISTING_PASSWORD")))
                .andExpect(jsonPath("$.temporaryPassword").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("credentials: unknown username — 404, with or without JWT")
    void should_return404_when_credentialsForUnknownUser() throws Exception {
        mockMvc.perform(post(CREDENTIALS, "ghost.user")).andExpect(status().isNotFound());
        mockMvc.perform(asOrganizer(post(CREDENTIALS, "ghost.user"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("provision: omitting firstName/lastName derives defaults from email (review patch P1)")
    void should_deriveDefaults_when_namesOmitted() throws Exception {
        String body = """
                {
                  "email": "kim.lee@example.com",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));

        User created = userRepository.findByEmailIgnoreCase("kim.lee@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getFirstName()).isEqualTo("Kim");
        org.assertj.core.api.Assertions.assertThat(created.getLastName()).isEqualTo("Lee");
    }

    @Test
    @DisplayName("provision: stale legacy fields rejected — 400 (review patch P2)")
    void should_return400_when_provisionRequestContainsUnknownFields() throws Exception {
        // Unknown field "department" is not in ProvisionUserRequest. Per Resolved Decision §3,
        // additionalProperties:false must yield 400.
        String body = """
                {
                  "email": "with.unknown@example.com",
                  "firstName": "With",
                  "lastName": "Unknown",
                  "role": "SPEAKER",
                  "department": "Engineering"
                }
                """;

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("patchProfile: blank-but-non-null bio rejected (review patch P2)")
    void should_return400_when_bioIsBlank() throws Exception {
        userRepository.save(User.builder()
                .username("speaker.self")
                .email("self@example.com")
                .firstName("Self")
                .lastName("User")
                .bio("Original bio")
                .roles(new java.util.HashSet<>(Set.of(Role.SPEAKER)))
                .build());

        String body = """
                {
                  "bio": "   "
                }
                """;

        mockMvc.perform(asOrganizer(patch(PROFILE, "speaker.self"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        User after = userRepository.findByUsername("speaker.self").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(after.getBio()).isEqualTo("Original bio");
    }

    // ============================================================
    // Story 11.E.2 AC11 #1, #2, #4 — Cognito wiring on provisionUserWithRole
    // (AC11 #3 — @Transactional rollback — lives in UserProvisioningCognitoRollbackIntegrationTest
    //  because it requires committed-or-rolledback semantics, not the test-managed transaction.)
    // ============================================================

    @Test
    @DisplayName("provision (11.E.2 AC11 #1): new-user branch calls adminCreateUserSilently")
    void should_callAdminCreateUserSilently_when_provisioningNewSpeaker() throws Exception {
        String body = """
                {
                  "email": "cognito.new@example.com",
                  "firstName": "Cognito",
                  "lastName": "New",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        // Story 11.E.2 review patch (P11 / B21): preferred_username is the actually-persisted
        // username, so a regression that passes the wrong identifier (e.g. the email) is caught.
        User created = userRepository.findByEmailIgnoreCase("cognito.new@example.com").orElseThrow();
        InMemoryCognito.Login login = cognito.login("cognito.new@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(login.attribute("preferred_username"))
                .isEqualTo(created.getUsername());
        org.assertj.core.api.Assertions.assertThat(login.attribute("email_verified")).isEqualTo("true");
        org.assertj.core.api.Assertions.assertThat(login.attribute("custom:role")).isEqualTo("UNUSED");
        // A throwaway temporary password the pool accepted (its policy runs in the fake).
        org.assertj.core.api.Assertions.assertThat(login.status())
                .isEqualTo(software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType
                        .FORCE_CHANGE_PASSWORD);
        org.assertj.core.api.Assertions.assertThat(login.password()).isNotNull().hasSizeGreaterThanOrEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(cognito.logins()).hasSize(1);
        // The profile is linked to the new login's sub, as the existing-user branch and the
        // issue-credentials self-heal already do, so the PreTokenGeneration Lambda finds it by its
        // primary key on the first sign-in instead of relying on the e-mail fallback.
        org.assertj.core.api.Assertions.assertThat(created.getCognitoUserId()).isEqualTo(login.sub());
    }

    @Test
    @DisplayName("provision (11.E.2 AC11 #2): existing-user branch self-heals via Cognito (idempotent)")
    void should_callCognitoIdempotently_when_provisioningExistingUser() throws Exception {
        // Seed an existing user without SPEAKER role.
        userRepository.save(User.builder()
                .username("existing.alice")
                .email("existing.alice@example.com")
                .firstName("Existing")
                .lastName("Alice")
                .roles(new java.util.HashSet<>(Set.of(Role.ATTENDEE)))
                .build());

        String body = """
                {
                  "email": "existing.alice@example.com",
                  "firstName": "Existing",
                  "lastName": "Alice",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(false)));

        // Role granted...
        User reloaded = userRepository.findByEmailIgnoreCase("existing.alice@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getRoles()).contains(Role.SPEAKER);

        // ... and the existing-user branch self-heals the Cognito shell via the idempotent
        // `adminCreateUserSilently` (Epic 11.E.7 fix 13a753e5): the pool had no login for the
        // address, so it has one now, linked to the profile. The User table still has exactly one
        // row for this email (no duplicate row created).
        InMemoryCognito.Login login = cognito.login("existing.alice@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(login.attribute("preferred_username")).isEqualTo("existing.alice");
        org.assertj.core.api.Assertions.assertThat(reloaded.getCognitoUserId()).isEqualTo(login.sub());
        long matchingRows = userRepository.findAll().stream()
                .filter(u -> "existing.alice@example.com".equalsIgnoreCase(u.getEmail()))
                .count();
        org.assertj.core.api.Assertions.assertThat(matchingRows).isEqualTo(1);
    }

    @Test
    @DisplayName("provision (11.E.2 AC11 #4): User + role persist when Cognito already has the login (idempotent)")
    void should_persistUserAndRole_when_cognitoAlreadyHasTheLogin() throws Exception {
        // Story 11.E.2 review patch (P7 / B9): the race in which Cognito already has a login for
        // the address (an organic sign-up, a lost create race). The real impl now runs on the
        // in-memory pool, so its UsernameExistsException swallow runs too: no new login, the
        // existing one untouched, and the User + role rows persist.
        InMemoryCognito.Login existing = cognito.givenLogin("race.bob@example.com",
                software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType.CONFIRMED);

        String body = """
                {
                  "email": "race.bob@example.com",
                  "firstName": "Race",
                  "lastName": "Bob",
                  "role": "SPEAKER"
                }
                """;

        mockMvc.perform(asOrganizer(post(PROVISION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(true)));

        User created = userRepository.findByEmailIgnoreCase("race.bob@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.getRoles()).contains(Role.SPEAKER);
        org.assertj.core.api.Assertions.assertThat(cognito.logins()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(cognito.login("race.bob@example.com").orElseThrow().status())
                .isEqualTo(existing.status());
    }
}
