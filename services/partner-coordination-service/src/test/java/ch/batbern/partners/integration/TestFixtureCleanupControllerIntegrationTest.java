package ch.batbern.partners.integration;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.dto.TestFixtureCleanupRequest;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code TestFixtureCleanupController} on PCS.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authorization: 403 (non-organizer roles), 200 (organizer)</li>
 *   <li>Prefix validation: invalid prefix rejected with 400 (defense against {@code prefix=br}
 *       which would otherwise match the real {@code brtest*} entries — also intentionally
 *       defends against {@code prefix=B} matching nothing real but indicating loose intent)</li>
 *   <li>SQL-injection-shaped prefixes → 400 (regex enforcement at service layer)</li>
 *   <li>Unknown entityType → 400</li>
 *   <li>Empty / missing prefix → 400</li>
 *   <li>Successful deletion: only the matching test rows deleted; real-shaped rows survive</li>
 * </ul>
 *
 * <p>The 401-unauthenticated case is NOT testable here because PCS's
 * {@code SecurityConfig.testFilterChain} sets {@code .anyRequest().permitAll()} at the HTTP
 * layer (production auth is at the API Gateway layer per Story 1.2); method-level
 * {@code @PreAuthorize} returns 403 even without authentication, so 401 only manifests in
 * production. Same precedent as the EMS cleanup test class.
 *
 * <p>Defensive assertion: every successful-cleanup test seeds real-looking partners (ELCA,
 * Swisscom) and verifies they survive. This is the production-safety guarantee that
 * justifies running this endpoint against the production account — the staging audit
 * found 7 {@code brtest*} partners co-existing with real partners in the same table.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("TestFixtureCleanup REST API Integration Tests (PCS)")
class TestFixtureCleanupControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/admin/test-fixtures/pcs/cleanup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartnerRepository partnerRepository;

    @BeforeEach
    void cleanState() {
        // Tests start from a known-empty slate so deletion counts are deterministic.
        // Cascade deletes remove partner_meeting_attendance, partner_meeting_rsvps,
        // partner_notes, topic_suggestions (+ topic_votes).
        partnerRepository.deleteAll();
    }

    // ---------- Authorization ----------

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("returns 401 when caller is unauthenticated (HTTP-level disabled in test config)")
        @Disabled("HTTP-level auth disabled in SecurityConfig.testFilterChain — method-level "
                + "@PreAuthorize returns 403 without auth. 401 only manifests behind the API "
                + "Gateway in prod. Mirrors EMS/SlotAssignmentControllerIntegrationTest precedent.")
        void returns401_whenUnauthenticated() throws Exception {
            // Documented expectation; not exercisable in test config — see class javadoc.
        }

        @Test
        @DisplayName("returns 403 when caller is ATTENDEE (not ORGANIZER)")
        @WithMockUser(roles = {"ATTENDEE"})
        void returns403_whenAttendee() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("partners")
                    .prefix("brtest")
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
                    .entityType("partners")
                    .prefix("brtest")
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
                    .entityType("partners")
                    .prefix("brtest")
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
                    .prefix("brtest")
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
                    .entityType("partners")
                    .prefix("")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix is too short (e.g. 'br' — would match real partners)")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenPrefixTooShort() throws Exception {
            // "br" would match brtest* AND any other 2-char-starting company name.
            // Defense against operator typos that could nuke real partners.
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("partners")
                    .prefix("br")
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
                    .entityType("partners")
                    .prefix("brtest'; DROP TABLE partners; --")
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
                    .entityType("partners")
                    .prefix("brtest%")
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
        @DisplayName("deletes brtest* partners but leaves real partner companies alone")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestPartners_preservesRealPartners() throws Exception {
            // Given: two test partners (canonical brtest pattern) + two real-shaped partners.
            // company_name is VARCHAR(12) — real names must fit (ELCA=4, Swisscom=8).
            partnerRepository.save(buildPartner("brtest117"));
            partnerRepository.save(buildPartner("brtest150"));
            partnerRepository.save(buildPartner("ELCA"));
            partnerRepository.save(buildPartner("Swisscom"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("partners")
                    .prefix("brtest")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.partners").value(2))
                    .andExpect(jsonPath("$.entityType").value("partners"))
                    .andExpect(jsonPath("$.prefix").value("brtest"));

            assertThat(partnerRepository.findByCompanyName("brtest117")).isEmpty();
            assertThat(partnerRepository.findByCompanyName("brtest150")).isEmpty();
            assertThat(partnerRepository.findByCompanyName("ELCA")).isPresent();
            assertThat(partnerRepository.findByCompanyName("Swisscom")).isPresent();
        }

        @Test
        @DisplayName("is idempotent — re-running with nothing to delete returns 0 counts")
        @WithMockUser(roles = {"ORGANIZER"})
        void isIdempotent_whenNothingMatches() throws Exception {
            partnerRepository.save(buildPartner("ELCA"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("partners")
                    .prefix("brtest")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.partners").value(0));

            assertThat(partnerRepository.findByCompanyName("ELCA")).isPresent();
        }
    }

    // ---------- Test data builders ----------

    private Partner buildPartner(String companyName) {
        Instant now = Instant.now();
        return Partner.builder()
                .companyName(companyName)
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.of(2024, 1, 1))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
