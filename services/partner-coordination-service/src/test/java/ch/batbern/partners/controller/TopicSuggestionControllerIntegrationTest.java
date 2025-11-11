package ch.batbern.partners.controller;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicSuggestionRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Topic Suggestion API endpoints.
 * Tests extend AbstractIntegrationTest for PostgreSQL Testcontainer.
 * Tests are written BEFORE implementation (TDD RED phase).
 *
 * Tests AC5: Topic Suggestions
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class TopicSuggestionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private TopicSuggestionRepository topicSuggestionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyServiceClient companyServiceClient;

    private Partner testPartner;

    @BeforeEach
    void setUp() {
        topicSuggestionRepository.deleteAll();
        partnerRepository.deleteAll();

        // Create test partner
        testPartner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);
    }

    @Test
    void should_listPartnerSuggestions_when_getEndpointCalled() throws Exception {
        // Given - partner has no suggestions initially

        // When/Then
        mockMvc.perform(get("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void should_submitTopicSuggestion_when_validDataProvided() throws Exception {
        // Given
        Map<String, Object> suggestionRequest = new HashMap<>();
        suggestionRequest.put("suggestedTopic", "Sustainable Architecture in Swiss Alpine Regions");
        suggestionRequest.put("description", "This topic would explore sustainable building practices specifically tailored for Swiss alpine environments, covering energy efficiency, local materials, and environmental impact minimization.");
        suggestionRequest.put("businessJustification", "High interest from clients in eco-friendly construction methods. This aligns with Switzerland's sustainability goals and growing market demand.");

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedTopic").value("Sustainable Architecture in Swiss Alpine Regions"))
                .andExpect(jsonPath("$.description").value(containsString("sustainable building practices")))
                .andExpect(jsonPath("$.businessJustification").value(containsString("High interest from clients")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.suggestedAt").exists())
                .andExpect(jsonPath("$.reviewedAt").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    @Test
    void should_setSuggestionStatusToSubmitted_when_initiallyCreated() throws Exception {
        // Given
        Map<String, Object> suggestionRequest = new HashMap<>();
        suggestionRequest.put("suggestedTopic", "Digital Transformation for Architecture Firms");
        suggestionRequest.put("description", "How architecture firms can leverage digital tools and BIM for competitive advantage");
        suggestionRequest.put("businessJustification", "Critical topic for industry evolution");

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.suggestedAt").exists());
    }

    @Test
    void should_return400_when_suggestionTitleExceedsMaxLength() throws Exception {
        // Given - 501 characters (exceeds 500 max)
        String longTitle = "A".repeat(501);
        Map<String, Object> suggestionRequest = new HashMap<>();
        suggestionRequest.put("suggestedTopic", longTitle);
        suggestionRequest.put("description", "Valid description");
        suggestionRequest.put("businessJustification", "Valid justification");

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Suggested topic must not exceed 500 characters")));
    }

    @Test
    void should_return400_when_descriptionExceedsMaxLength() throws Exception {
        // Given - 2001 characters (exceeds 2000 max)
        String longDescription = "A".repeat(2001);
        Map<String, Object> suggestionRequest = new HashMap<>();
        suggestionRequest.put("suggestedTopic", "Valid Topic");
        suggestionRequest.put("description", longDescription);
        suggestionRequest.put("businessJustification", "Valid justification");

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Description must not exceed 2000 characters")));
    }

    @Test
    void should_return404_when_partnerNotFound() throws Exception {
        // Given
        Map<String, Object> suggestionRequest = new HashMap<>();
        suggestionRequest.put("suggestedTopic", "Valid Topic");
        suggestionRequest.put("description", "Valid description");
        suggestionRequest.put("businessJustification", "Valid justification");

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", "NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Partner not found")));
    }

    @Test
    void should_returnSuggestionHistory_when_listSuggestionsCalledAfterSubmitting() throws Exception {
        // Given - submit multiple suggestions
        Map<String, Object> suggestion1 = new HashMap<>();
        suggestion1.put("suggestedTopic", "Topic 1");
        suggestion1.put("description", "Description 1");
        suggestion1.put("businessJustification", "Justification 1");

        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestion1)))
                .andExpect(status().isCreated());

        Map<String, Object> suggestion2 = new HashMap<>();
        suggestion2.put("suggestedTopic", "Topic 2");
        suggestion2.put("description", "Description 2");
        suggestion2.put("businessJustification", "Justification 2");

        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestion2)))
                .andExpect(status().isCreated());

        // When/Then - list suggestions
        mockMvc.perform(get("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].suggestedTopic").value(hasItems("Topic 1", "Topic 2")))
                .andExpect(jsonPath("$[*].status").value(everyItem(is("SUBMITTED"))))
                .andExpect(jsonPath("$[*].suggestedAt").exists());
    }

    @Test
    void should_allowOptionalBusinessJustification_when_notProvided() throws Exception {
        // Given - suggestion without business justification
        Map<String, Object> suggestionRequest = new HashMap<>();
        suggestionRequest.put("suggestedTopic", "Topic without Justification");
        suggestionRequest.put("description", "Description only");
        // businessJustification is optional

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suggestionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedTopic").value("Topic without Justification"))
                .andExpect(jsonPath("$.description").value("Description only"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void should_return400_when_requiredFieldsMissing() throws Exception {
        // Test missing suggestedTopic
        Map<String, Object> noTopicRequest = new HashMap<>();
        noTopicRequest.put("description", "Description");

        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noTopicRequest)))
                .andExpect(status().isBadRequest());

        // Test missing description
        Map<String, Object> noDescriptionRequest = new HashMap<>();
        noDescriptionRequest.put("suggestedTopic", "Topic");

        mockMvc.perform(post("/api/v1/partners/{companyName}/suggestions", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noDescriptionRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Helper method to create test partner.
     */
    private Partner createTestPartner(String companyName, PartnershipLevel level) {
        Partner partner = Partner.builder()
                .companyName(companyName)
                .partnershipLevel(level)
                .partnershipStartDate(LocalDate.now().minusMonths(6))
                .partnershipEndDate(LocalDate.now().plusYears(1))
                .build();
        return partnerRepository.save(partner);
    }
}
