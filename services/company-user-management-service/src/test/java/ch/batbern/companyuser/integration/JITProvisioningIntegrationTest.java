package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.event.UserCreatedEvent;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 12.3 (SSO PR 1 — Part B): the {@code JITUserProvisioningInterceptor} is the
 * canonical, provider-agnostic reconcile path. These Testcontainers tests prove — against
 * real PostgreSQL, driving a real authenticated request through {@code /api/**} so the
 * interceptor actually runs (per {@code WebMvcConfig} path mapping) — that:
 * <ul>
 *   <li>AC3 — a fresh identity self-provisions ONE row with names + ATTENDEE +
 *       {@code pref_language} from {@code custom:preferences}, and a {@code UserCreatedEvent}
 *       with {@code source == "JIT_PROVISIONING"} is published;</li>
 *   <li>AC4 — an existing anonymous-by-email row is LINKED (not duplicated) and publishes
 *       NO {@code UserCreatedEvent}.</li>
 * </ul>
 *
 * <p>Uses the {@code jwt()} request post-processor (NOT {@code @WithMockUser}) because the
 * interceptor only acts on a {@code JwtAuthenticationToken}. The endpoint hit is the
 * permitAll {@code GET /api/v1/users}; provisioning happens in {@code preHandle} before the
 * controller, so the list response is incidental.
 */
@Transactional
@Import(TestAwsConfig.class)
@RecordApplicationEvents
@DisplayName("Story 12.3 — canonical JIT provisioning (create + email-link) integration")
class JITProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicationEvents applicationEvents;

    // ============================================================
    // AC3 — fresh identity → canonical create (names + ATTENDEE + language + event)
    // ============================================================

    @Test
    @DisplayName("fresh identity → one row created with names, ATTENDEE, pref_language=fr + JIT event")
    void should_createCanonicalRow_when_freshIdentityHitsApi() throws Exception {
        String sub = "jit-fresh-sub-123";
        String email = "marie.favre@example.ch";
        String preferences = "{\"firstName\":\"Marie\",\"lastName\":\"Favre\",\"language\":\"fr\"}";

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> j.subject(sub)
                                        .claim("email", email)
                                        .claim("custom:preferences", preferences))
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());

        User created = userRepository.findByCognitoUserId(sub).orElseThrow();
        assertThat(created.getCognitoUserId()).isEqualTo(sub);
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getFirstName()).isEqualTo("Marie");
        assertThat(created.getLastName()).isEqualTo("Favre");
        assertThat(created.getUsername()).isEqualTo("marie.favre");
        assertThat(created.isActive()).isTrue();
        // The language gap this story closes — captured from custom:preferences, not defaulted.
        assertThat(created.getPreferences()).isNotNull();
        assertThat(created.getPreferences().getLanguage()).isEqualTo("fr");
        // Default ATTENDEE (no authorities on the JWT).
        assertThat(created.getRoles()).containsExactly(Role.ATTENDEE);

        // Story 12.11 (AC2): JIT provisioning records NO ToS consent — federated users
        // never saw the checkbox, so the onboarding gate must fire for them.
        assertThat(created.getTermsAcceptedAt()).isNull();

        // Exactly one row for this email (no duplicate).
        long rowsForEmail = userRepository.findAll().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .count();
        assertThat(rowsForEmail).isEqualTo(1);

        // A UserCreatedEvent with source JIT_PROVISIONING was published exactly once.
        long jitEvents = applicationEvents.stream(UserCreatedEvent.class)
                .filter(e -> "JIT_PROVISIONING".equals(e.getSource()))
                .count();
        assertThat(jitEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("fresh identity with no custom:preferences → row defaults to pref_language=de")
    void should_defaultLanguageToDe_when_freshIdentityHasNoPreferences() throws Exception {
        String sub = "jit-fresh-sub-de";
        String email = "hans.muster@example.ch";

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> j.subject(sub)
                                        .claim("email", email)
                                        .claim("given_name", "Hans")
                                        .claim("family_name", "Muster"))
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());

        User created = userRepository.findByCognitoUserId(sub).orElseThrow();
        // No preferences on the JWT → @PrePersist supplies the default "de" (behaviour unchanged).
        assertThat(created.getPreferences()).isNotNull();
        assertThat(created.getPreferences().getLanguage()).isEqualTo("de");
        assertThat(created.getRoles()).containsExactly(Role.ATTENDEE);
    }

    // ============================================================
    // AC4 — existing anonymous-by-email → linked, not duplicated, no event
    // ============================================================

    @Test
    @DisplayName("existing anonymous-by-email row → linked to fresh sub, not duplicated, no event")
    void should_linkExistingAnonymousUser_when_freshSubWithSameEmailHitsApi() throws Exception {
        String email = "pre.invited@example.ch";
        // Pre-invited / historical-participant shape: a row with NO cognito_user_id yet.
        User seeded = userRepository.save(User.builder()
                .username("pre.invited")
                .email(email)
                .firstName("Pre")
                .lastName("Invited")
                .roles(new HashSet<>(Set.of(Role.SPEAKER)))
                .build());
        assertThat(seeded.getCognitoUserId()).isNull();

        String freshSub = "fresh-federated-sub-999";
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> j.subject(freshSub)
                                        .claim("email", email))
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());

        // Same row now carries the Cognito sub — linked, not duplicated.
        User linked = userRepository.findByEmail(email).orElseThrow();
        assertThat(linked.getCognitoUserId()).isEqualTo(freshSub);
        // Existing username + roles preserved.
        assertThat(linked.getUsername()).isEqualTo("pre.invited");
        assertThat(linked.getRoles()).containsExactly(Role.SPEAKER);

        // No second row for this email.
        long rowsForEmail = userRepository.findAll().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .count();
        assertThat(rowsForEmail).isEqualTo(1);

        // The link path publishes NO UserCreatedEvent (only the create path does).
        long createdEvents = applicationEvents.stream(UserCreatedEvent.class).count();
        assertThat(createdEvents).isZero();
    }
}
