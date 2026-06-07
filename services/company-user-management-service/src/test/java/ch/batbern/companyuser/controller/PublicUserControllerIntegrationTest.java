package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link PublicUserController}.
 *
 * Story 11.C.1 (AC7): public user-portrait endpoint replacing the deleted
 * GET /api/v1/speakers/{username}.
 *
 * Verifies:
 *  - Anonymous (unauthenticated) access succeeds (permitAll matcher in CUMS SecurityConfig).
 *  - Row scope is restricted to users with SPEAKER role (D2 resolution): non-speakers return 404
 *    to prevent mass-enumeration of attendee identities.
 *  - Response carries 24h public Cache-Control header (D3 / mitigation).
 *  - Projection is narrow: username, firstName, lastName, profilePictureUrl only — no email,
 *    no role list, no bio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("PublicUserController Integration Tests")
class PublicUserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private User speaker(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName("Bruno")
                .lastName("Speaker")
                .cognitoUserId(username)
                .companyId("GoogleZH")
                .bio("Should not appear in public projection")
                .profilePictureUrl("https://cdn.batbern.ch/portraits/" + username + ".jpg")
                .roles(new HashSet<>(Set.of(Role.SPEAKER)))
                .build();
    }

    private User attendeeOnly(String username) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName("Anon")
                .lastName("Attendee")
                .cognitoUserId(username)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
    }

    @Test
    @DisplayName("should_returnPublicProjection_when_userIsSpeaker (AC7)")
    void should_returnPublicProjection_when_userIsSpeaker() throws Exception {
        userRepository.save(speaker("john.doe"));

        mockMvc.perform(get("/api/v1/public/users/john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.firstName").value("Bruno"))
                .andExpect(jsonPath("$.lastName").value("Speaker"))
                .andExpect(jsonPath("$.profilePictureUrl",
                        containsString("/portraits/john.doe.jpg")))
                // Projection MUST NOT include any of the following:
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.companyId").doesNotExist());
    }

    @Test
    @DisplayName("should_set24hPublicCacheControl_when_userFound")
    void should_set24hPublicCacheControl_when_userFound() throws Exception {
        userRepository.save(speaker("jane.speaker"));

        mockMvc.perform(get("/api/v1/public/users/jane.speaker"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    @Test
    @DisplayName("should_return404_when_usernameUnknown")
    void should_return404_when_usernameUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/public/users/no.such.user"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return404_when_userExistsButLacksSpeakerRole (D2 row scope)")
    void should_return404_when_userExistsButLacksSpeakerRole() throws Exception {
        // Attendee-only user: discoverable via authenticated /api/v1/users/{username}
        // but NOT exposed publicly per Story 11.C.1 D2 — prevents enumeration of attendees.
        userRepository.save(attendeeOnly("anon.attendee"));

        mockMvc.perform(get("/api/v1/public/users/anon.attendee"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_acceptAnonymousAccess_permitAllMatcherRegression")
    void should_acceptAnonymousAccess_permitAllMatcherRegression() throws Exception {
        // Anonymous request (no Authorization header). If a future SecurityConfig
        // change accidentally removes the permitAll matcher for /api/v1/public/users/*,
        // this assertion will flip from 404 (unknown user) to 401 — surfacing the regression
        // before it reaches production.
        mockMvc.perform(get("/api/v1/public/users/some.unknown.user"))
                .andExpect(status().isNotFound());
    }
}
