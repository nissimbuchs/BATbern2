package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.dto.TestFixtureCleanupRequest;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code TestFixtureCleanupController} on CUMS.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authorization: 401 (unauthenticated), 403 (non-organizer), 200 (organizer)</li>
 *   <li>Prefix validation: invalid prefix rejected with 400 (defense against {@code prefix=BAT}
 *       which would otherwise match real companies)</li>
 *   <li>Cross-entity prefix rejection: company prefix supplied for users entityType → 400</li>
 *   <li>SQL-injection-shaped prefixes → 400 (regex enforcement at service layer)</li>
 *   <li>Unknown entityType → 400</li>
 *   <li>Empty / missing prefix → 400</li>
 *   <li>Successful deletion: only the matching test rows deleted; real-shaped rows survive</li>
 * </ul>
 *
 * <p>Defensive assertion: every successful-cleanup test seeds a "real-looking" row (e.g.,
 * a company named {@code "Swisscom"} or a user {@code bruno.linder}) and verifies it
 * survives. This is the production-safety guarantee that justifies running this endpoint
 * against the production account.
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("TestFixtureCleanup REST API Integration Tests")
class TestFixtureCleanupControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/admin/test-fixtures/cums/cleanup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAdditionalEmailRepository additionalEmailRepository;

    @BeforeEach
    void cleanState() {
        // Tests start from a known-empty slate so deletion counts are deterministic.
        additionalEmailRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    // ---------- Authorization ----------

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("returns 401 when caller is unauthenticated")
        void returns401_whenUnauthenticated() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 403 when caller is ATTENDEE (not ORGANIZER)")
        @WithMockUser(roles = {"ATTENDEE"})
        void returns403_whenAttendee() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 when caller is SPEAKER (not ORGANIZER)")
        @WithMockUser(roles = {"SPEAKER"})
        void returns403_whenSpeaker() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 when caller is PARTNER (not ORGANIZER)")
        @WithMockUser(roles = {"PARTNER"})
        void returns403_whenPartner() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------- Input validation ----------

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("returns 400 when entityType is unknown")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenUnknownEntityType() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("invoices")
                    .prefix("BRUNOTESTCO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix is empty")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenPrefixEmpty() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix would match real entities (e.g. BAT)")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenPrefixTooShort() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BAT")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when company prefix supplied for users entityType")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenCrossEntityPrefix() throws Exception {
            // BRUNOTESTCO is the company prefix; it doesn't match the user-prefix regex (^bruno\.test\.$)
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("users")
                    .prefix("BRUNOTESTCO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix contains SQL-injection-shaped chars")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenSqlInjectionShapedPrefix() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO'; DROP TABLE companies; --")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix contains wildcard chars")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenWildcardPrefix() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO%")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------- Successful cleanup ----------

    @Nested
    @DisplayName("Successful cleanup")
    class SuccessfulCleanup {

        @Test
        @DisplayName("deletes BRUNOTESTCO* companies but leaves real-shaped companies alone")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestCompanies_preservesRealCompanies() throws Exception {
            // Given: two test companies + two real-shaped companies
            companyRepository.save(buildCompany("BRUNOTESTCO1779647142000"));
            companyRepository.save(buildCompany("BRUNOTESTCO1779647142001"));
            companyRepository.save(buildCompany("Swisscom"));
            companyRepository.save(buildCompany("RUAG"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO")
                    .build();

            // When
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.companies").value(2))
                    .andExpect(jsonPath("$.entityType").value("companies"))
                    .andExpect(jsonPath("$.prefix").value("BRUNOTESTCO"));

            // Then: only the BRUNOTESTCO* companies are gone; real ones remain
            assertThat(companyRepository.findByName("BRUNOTESTCO1779647142000")).isEmpty();
            assertThat(companyRepository.findByName("BRUNOTESTCO1779647142001")).isEmpty();
            assertThat(companyRepository.findByName("Swisscom")).isPresent();
            assertThat(companyRepository.findByName("RUAG")).isPresent();
        }

        @Test
        @DisplayName("deletes bruno.test.* users but leaves real Bruno users alone")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestUsers_preservesRealBrunos() throws Exception {
            // Given: test users (canonical pattern) + real users named Bruno
            userRepository.save(buildUser("bruno.test.1779647142000", "bruno-test-1@e2e.batbern.invalid"));
            userRepository.save(buildUser("bruno.test.1779647142001", "bruno-test-2@e2e.batbern.invalid"));
            userRepository.save(buildUser("bruno.linder", "bruno.linder@sbb.ch"));
            userRepository.save(buildUser("bruno.frey", "bruno.frey@astra.admin.ch"));
            userRepository.save(buildUser("bruno.blumenthal", "bruno.blumenthal@ruag.com"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("users")
                    .prefix("bruno.test.")
                    .build();

            // When
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.user_profiles").value(2));

            // Then: real Bruno users survive
            assertThat(userRepository.findByUsername("bruno.test.1779647142000")).isEmpty();
            assertThat(userRepository.findByUsername("bruno.test.1779647142001")).isEmpty();
            assertThat(userRepository.findByUsername("bruno.linder")).isPresent();
            assertThat(userRepository.findByUsername("bruno.frey")).isPresent();
            assertThat(userRepository.findByUsername("bruno.blumenthal")).isPresent();
        }

        @Test
        @DisplayName("deletes bruno-test-* additional emails but leaves real ones alone (F4)")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestAdditionalEmails_preservesRealOnes() throws Exception {
            // Given: an auth-shaped user (e.g. batbern.organizer in prod) with both
            //        leaked test additional emails AND a real one
            User authUser = userRepository.save(buildUser("batbern.organizer", "batbern.organizer@example.ch"));
            additionalEmailRepository.save(buildAdditionalEmail(authUser, "bruno-test-1779647142000@e2e.batbern.invalid"));
            additionalEmailRepository.save(buildAdditionalEmail(authUser, "bruno-test-1779647142001@e2e.batbern.invalid"));
            additionalEmailRepository.save(buildAdditionalEmail(authUser, "personal-second-address@batbern.ch"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("additional_emails")
                    .prefix("bruno-test-")
                    .build();

            // When
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.user_additional_emails").value(2))
                    .andExpect(jsonPath("$.entityType").value("additional_emails"))
                    .andExpect(jsonPath("$.prefix").value("bruno-test-"));

            // Then: the real second-address survives; the auth user itself is untouched
            assertThat(additionalEmailRepository.findAll()).hasSize(1);
            assertThat(additionalEmailRepository.findAll().get(0).getEmail())
                    .isEqualTo("personal-second-address@batbern.ch");
            assertThat(userRepository.findByUsername("batbern.organizer")).isPresent();
        }

        @Test
        @DisplayName("sweeps legacy bruno-additional-NNN@example.com via the alternative prefix")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesLegacyAdditionalEmailPrefix() throws Exception {
            // F4 has a SECOND prefix branch for the historical leak shape;
            // this verifies it sweeps in addition to the canonical bruno-test- prefix.
            User authUser = userRepository.save(buildUser("batbern.organizer", "batbern.organizer@example.ch"));
            additionalEmailRepository.save(buildAdditionalEmail(authUser, "bruno-additional-139@example.com"));
            additionalEmailRepository.save(buildAdditionalEmail(authUser, "bruno-additional-845@example.com"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("additional_emails")
                    .prefix("bruno-additional-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.user_additional_emails").value(2));

            assertThat(additionalEmailRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("rejects an additional-emails prefix that is neither canonical nor legacy")
        @WithMockUser(roles = {"ORGANIZER"})
        void rejectsNonMatchingAdditionalEmailPrefix() throws Exception {
            // Defensive: prefix="bruno-" would otherwise sweep too broadly.
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("additional_emails")
                    .prefix("bruno-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("is idempotent — re-running with nothing to delete returns 0 counts")
        @WithMockUser(roles = {"ORGANIZER"})
        void isIdempotent_whenNothingMatches() throws Exception {
            // Given: only real companies exist
            companyRepository.save(buildCompany("Swisscom"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("companies")
                    .prefix("BRUNOTESTCO")
                    .build();

            // When
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.companies").value(0));

            // Then: real company survives
            assertThat(companyRepository.findByName("Swisscom")).isPresent();
        }
    }

    // ---------- Test data builders ----------

    private Company buildCompany(String name) {
        return Company.builder()
                .name(name)
                .displayName(name)
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-fixture-cleanup-test")
                .build();
    }

    private User buildUser(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .firstName("Test")
                .lastName("User")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private UserAdditionalEmail buildAdditionalEmail(User user, String email) {
        return UserAdditionalEmail.builder()
                .user(user)
                .email(email)
                .createdAt(Instant.now())
                .build();
    }
}
