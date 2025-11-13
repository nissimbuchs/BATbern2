package ch.batbern.partners.performance;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerContact;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerContactRepository;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Performance tests for Partner Service.
 *
 * Verifies P95 latency targets:
 * - Partner list: <100ms P95
 * - Partner detail: <150ms P95
 * - Partner with contacts (HTTP enrichment): <300ms P95
 *
 * Note: These are baseline tests. Full performance testing with load generation
 * should be done in staging environment with realistic network latencies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerPerformanceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private PartnerContactRepository partnerContactRepository;

    @MockBean
    private CompanyServiceClient companyServiceClient;

    @MockBean
    private UserServiceClient userServiceClient;

    private Partner testPartner;

    @BeforeEach
    void setUp() {
        // Create test partner
        testPartner = new Partner();
        testPartner.setCompanyName("PerfTestCo");
        testPartner.setPartnershipLevel(PartnershipLevel.GOLD);
        testPartner.setPartnershipStartDate(LocalDate.now().minusMonths(6));
        testPartner.setPartnershipEndDate(LocalDate.now().plusMonths(6));
        testPartner.setCreatedAt(java.time.Instant.now());
        testPartner = partnerRepository.save(testPartner);

        // Create test contact
        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(testPartner.getId());
        contact.setUsername("perf.user");
        contact.setContactRole(ch.batbern.partners.domain.ContactRole.PRIMARY);
        contact.setPrimary(true);
        contact.setCreatedAt(java.time.Instant.now());
        partnerContactRepository.save(contact);

        // Mock HTTP clients
        CompanyResponse mockCompany = new CompanyResponse();
        mockCompany.setName("PerfTestCo");
        mockCompany.setDisplayName("Performance Test Company");
        when(companyServiceClient.getCompany(anyString())).thenReturn(mockCompany);

        UserResponse mockUser = new UserResponse();
        mockUser.setId("perf.user");  // Generated DTO uses setId() for username
        mockUser.setEmail("perf.user@example.com");
        mockUser.setFirstName("Performance");
        mockUser.setLastName("User");
        when(userServiceClient.getUserProfile(anyString())).thenReturn(mockUser);
    }

    @Test
    @WithMockUser(username = "test-organizer", roles = {"ORGANIZER"})
    void should_completePartnerList_within100ms_whenP95Measured() throws Exception {
        // Run 100 iterations to get P95 measurement
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();

            mockMvc.perform(get("/api/v1/partners"))
                    .andExpect(status().isOk());

            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000; // Convert to milliseconds
        }

        // Calculate P95
        java.util.Arrays.sort(latencies);
        long p95 = latencies[94]; // 95th percentile (0-indexed)

        System.out.println("Partner List P95 latency: " + p95 + "ms");

        // Assert P95 < 100ms
        assert p95 < 100 : "Partner list P95 latency (" + p95 + "ms) exceeds 100ms target";
    }

    @Test
    @WithMockUser(username = "test-organizer", roles = {"ORGANIZER"})
    void should_completePartnerDetail_within150ms_whenP95Measured() throws Exception {
        // Run 100 iterations to get P95 measurement
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();

            mockMvc.perform(get("/api/v1/partners/" + testPartner.getCompanyName()))
                    .andExpect(status().isOk());

            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000; // Convert to milliseconds
        }

        // Calculate P95
        java.util.Arrays.sort(latencies);
        long p95 = latencies[94]; // 95th percentile (0-indexed)

        System.out.println("Partner Detail P95 latency: " + p95 + "ms");

        // Assert P95 < 150ms
        assert p95 < 150 : "Partner detail P95 latency (" + p95 + "ms) exceeds 150ms target";
    }

    @Test
    @WithMockUser(username = "test-organizer", roles = {"ORGANIZER"})
    void should_completePartnerWithContactsHTTP_within300ms_whenP95Measured() throws Exception {
        // Run 100 iterations to get P95 measurement
        long[] latencies = new long[100];

        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();

            // Request with company and contacts includes (triggers HTTP calls)
            mockMvc.perform(get("/api/v1/partners/" + testPartner.getCompanyName())
                            .param("include", "company")
                            .param("include", "contacts"))
                    .andExpect(status().isOk());

            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000; // Convert to milliseconds
        }

        // Calculate P95
        java.util.Arrays.sort(latencies);
        long p95 = latencies[94]; // 95th percentile (0-indexed)

        System.out.println("Partner with HTTP Enrichment P95 latency: " + p95 + "ms");

        // Assert P95 < 300ms (allows for HTTP call overhead)
        assert p95 < 300 : "Partner with HTTP enrichment P95 latency (" + p95 + "ms) exceeds 300ms target";
    }
}
