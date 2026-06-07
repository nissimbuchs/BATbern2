package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 11.F.1 (AC2): regression test guarding the magic-link teardown.
 *
 * <p>The two endpoints below were served by {@code SpeakerMagicLoginController} (deleted in
 * AC1, formerly {@code POST /api/v1/auth/speaker-magic-login}) and
 * {@code SpeakerPortalTokenController} (deleted in AC1, formerly returned {@code 410 Gone}
 * from {@code POST /api/v1/speaker-portal/validate-token}). With both controller classes
 * removed, Spring MVC has no {@code RequestMappingHandlerMapping} entry for either path
 * and the dispatcher returns {@code 404 Not Found}.
 *
 * <p>This test fails the build if a future commit reintroduces either controller, the
 * intermediate 410 response, or a magic-link auth bridge under a different package — i.e.
 * it locks in the AC1 deletion as the final state.
 *
 * <p>Auth matrix covered: anonymous + authenticated speaker, two endpoints — four cases.
 */
@Transactional
class MagicLinkEndpointsRemovedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/auth/speaker-magic-login returns 404 anonymously after AC1 deletion")
    void should_return404_when_postingToSpeakerMagicLoginAsAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"any-value\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "speaker.user", roles = {"SPEAKER"})
    @DisplayName("POST /api/v1/auth/speaker-magic-login returns 404 for authenticated SPEAKER after AC1 deletion")
    void should_return404_when_postingToSpeakerMagicLoginAsAuthenticatedSpeaker() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"any-value\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/speaker-portal/validate-token returns 404 anonymously after AC1 deletion")
    void should_return404_when_postingToValidateTokenAsAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"any-value\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "speaker.user", roles = {"SPEAKER"})
    @DisplayName("POST /api/v1/speaker-portal/validate-token returns 404 for authenticated SPEAKER after AC1 deletion")
    void should_return404_when_postingToValidateTokenAsAuthenticatedSpeaker() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"any-value\"}"))
                .andExpect(status().isNotFound());
    }
}
