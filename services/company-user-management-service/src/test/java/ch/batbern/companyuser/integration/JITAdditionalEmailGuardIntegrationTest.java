package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic 12 follow-up — JIT duplicate guard for verified additional emails.
 *
 * <p>The PreSignUp Lambda links a Google sign-in for a user's VERIFIED additional email into
 * the owning account. If that linking did not happen (e.g. the Lambda gate was not met at the
 * time, or a pre-existing federated identity), the first authenticated API call must NOT
 * JIT-create a duplicate user for that email. These Testcontainers tests drive a real
 * authenticated request through {@code /api/**} (so {@code JITUserProvisioningInterceptor}
 * actually runs) and prove, against real PostgreSQL:
 * <ul>
 *   <li>a JWT email matching a VERIFIED additional email of an existing Cognito-linked user X
 *       creates NO new {@code user_profiles} row and leaves X's {@code cognito_user_id}
 *       untouched;</li>
 *   <li>a JWT email matching an UNVERIFIED additional email still JIT-creates a new user
 *       (existing behaviour, unchanged).</li>
 * </ul>
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Epic 12 follow-up — JIT verified-additional-email duplicate guard")
class JITAdditionalEmailGuardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("JWT email = VERIFIED additional email of X → no duplicate row, X's sub untouched")
    void should_notCreateDuplicate_when_jwtEmailIsVerifiedAdditionalEmailOfExistingUser()
            throws Exception {
        String ownerSub = "owner-native-sub-111";
        String additionalEmail = "private.gmail@gmail.com";

        User owner = User.builder()
                .username("jane.owner")
                .email("jane.owner@company.ch")
                .firstName("Jane")
                .lastName("Owner")
                .cognitoUserId(ownerSub)
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
        owner.addAdditionalEmail(UserAdditionalEmail.builder()
                .email(additionalEmail)
                .createdAt(Instant.now())
                .verifiedAt(Instant.now())
                .build());
        userRepository.saveAndFlush(owner);

        long usersBefore = userRepository.count();

        // A federated identity that was NOT linked at PreSignUp time: unknown sub, JWT email
        // is the owner's verified additional email.
        String unlinkedSub = "unlinked-federated-sub-222";
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> j.subject(unlinkedSub)
                                        .claim("email", additionalEmail))
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());

        // No new row created.
        assertThat(userRepository.count()).isEqualTo(usersBefore);
        // No row carries the unlinked sub.
        assertThat(userRepository.findByCognitoUserId(unlinkedSub)).isEmpty();
        // No row exists for the additional email as a PRIMARY email.
        assertThat(userRepository.findByEmail(additionalEmail)).isEmpty();
        // The owner's cognito_user_id is untouched.
        User reloaded = userRepository.findByCognitoUserId(ownerSub).orElseThrow();
        assertThat(reloaded.getCognitoUserId()).isEqualTo(ownerSub);
        assertThat(reloaded.getUsername()).isEqualTo("jane.owner");
    }

    @Test
    @DisplayName("guard is transparent when no verified additional email matches → fresh user JIT-provisions")
    void should_jitCreateNewUser_when_noVerifiedAdditionalEmailMatches() throws Exception {
        // An existing owner with an UNVERIFIED additional email — findVerifiedByEmailIgnoreCase
        // returns nothing for it, so the guard is transparent. (Note: a brand-new user_profiles
        // row whose PRIMARY email equals an existing additional email is independently rejected
        // by the enforce_primary_email_not_other_additional DB trigger, so we provision a fresh
        // identity with its OWN distinct email to prove the guard does not interfere with the
        // unchanged JIT-create path.)
        String ownerSub = "owner-native-sub-333";
        User owner = User.builder()
                .username("bob.owner")
                .email("bob.owner@company.ch")
                .firstName("Bob")
                .lastName("Owner")
                .cognitoUserId(ownerSub)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();
        owner.addAdditionalEmail(UserAdditionalEmail.builder()
                .email("unverified.box@gmail.com")
                .createdAt(Instant.now())
                .verifiedAt(null) // UNVERIFIED → the verified-only guard never sees it.
                .build());
        userRepository.saveAndFlush(owner);

        String freshSub = "fresh-federated-sub-444";
        String freshEmail = "carla.fresh@example.ch";
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt()
                                .jwt(j -> j.subject(freshSub)
                                        .claim("email", freshEmail)
                                        .claim("given_name", "Carla")
                                        .claim("family_name", "Fresh"))
                                .authorities(Collections.emptyList())))
                .andExpect(status().isOk());

        // Existing behaviour preserved: a new user IS JIT-created.
        User created = userRepository.findByCognitoUserId(freshSub).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(freshEmail);
        assertThat(created.getRoles()).containsExactly(Role.ATTENDEE);
        // The owner's sub is untouched.
        assertThat(userRepository.findByCognitoUserId(ownerSub).orElseThrow().getCognitoUserId())
                .isEqualTo(ownerSub);
    }
}
