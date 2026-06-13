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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 12.11 (AC4): write-once ToS/Privacy consent semantics on PUT /api/v1/users/me.
 *
 * <ul>
 *   <li>{@code termsAccepted=true} + no consent on record → server stamps
 *       {@code terms_accepted_at} with its own clock.</li>
 *   <li>{@code termsAccepted=true} + consent already recorded → no-op (timestamp
 *       does not move).</li>
 *   <li>{@code termsAccepted=false} or absent → NEVER changes/clears recorded consent.</li>
 * </ul>
 *
 * <p>Note: the service serializes with {@code default-property-inclusion: non_null},
 * so a null {@code termsAcceptedAt} is OMITTED from the JSON — assertions on the
 * "no consent" state therefore check the DB entity, and the frontend treats
 * "hydration succeeded + field absent" as consent-missing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Consent Write-Once Integration Tests (Story 12.11)")
class ConsentWriteOnceIntegrationTest extends AbstractIntegrationTest {

    private static final String USERNAME = "consent.user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username(USERNAME)
                .email("consent.user@example.com")
                .firstName("Consent")
                .lastName("User")
                .cognitoUserId(USERNAME) // matches @WithMockUser principal
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build());
    }

    @Test
    @WithMockUser(username = USERNAME)
    @DisplayName("should_setTermsAcceptedAtOnce_when_firstAccept")
    void should_setTermsAcceptedAtOnce_when_firstAccept() throws Exception {
        Instant before = Instant.now().minus(2, ChronoUnit.SECONDS);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAcceptedAt").exists());

        Instant accepted = loadConsent();
        assertThat(accepted).isNotNull();
        assertThat(accepted).isAfter(before);
        assertThat(accepted).isBefore(Instant.now().plus(2, ChronoUnit.SECONDS));

        // The gate flag is returned by a plain GET /users/me without ?include=
        mockMvc.perform(get("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAcceptedAt").exists());
    }

    @Test
    @WithMockUser(username = USERNAME)
    @DisplayName("should_notMoveTimestamp_when_secondAccept")
    void should_notMoveTimestamp_when_secondAccept() throws Exception {
        Instant original = Instant.parse("2026-01-15T10:00:00Z");
        presetConsent(original);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": true}"))
                .andExpect(status().isOk());

        assertThat(loadConsent()).isEqualTo(original);
    }

    @Test
    @WithMockUser(username = USERNAME)
    @DisplayName("should_neverClearConsent_when_falseOrAbsent")
    void should_neverClearConsent_when_falseOrAbsent() throws Exception {
        Instant original = Instant.parse("2026-01-15T10:00:00Z");
        presetConsent(original);

        // termsAccepted=false must not clear or change recorded consent
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": false}"))
                .andExpect(status().isOk());
        assertThat(loadConsent()).isEqualTo(original);

        // an unrelated profile update (termsAccepted absent) must not touch consent
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Updated bio\"}"))
                .andExpect(status().isOk());
        assertThat(loadConsent()).isEqualTo(original);

        // termsAccepted=false on a consent-less user must not set consent either
        presetConsent(null);
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAccepted\": false}"))
                .andExpect(status().isOk());
        assertThat(loadConsent()).isNull();
    }

    private void presetConsent(Instant consentAt) {
        User user = userRepository.findByUsername(USERNAME).orElseThrow();
        user.setTermsAcceptedAt(consentAt);
        userRepository.saveAndFlush(user);
    }

    private Instant loadConsent() {
        return userRepository.findByUsername(USERNAME).orElseThrow().getTermsAcceptedAt();
    }
}
