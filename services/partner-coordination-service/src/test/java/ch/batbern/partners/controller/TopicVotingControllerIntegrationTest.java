package ch.batbern.partners.controller;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicVoteRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Topic Voting API endpoints.
 * Tests extend AbstractIntegrationTest for PostgreSQL Testcontainer.
 * Tests are written BEFORE implementation (TDD RED phase).
 *
 * Tests AC4: Topic Voting
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class TopicVotingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private TopicVoteRepository topicVoteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyServiceClient companyServiceClient;

    private Partner testPartner;
    private UUID testTopicId;

    @BeforeEach
    void setUp() {
        topicVoteRepository.deleteAll();
        partnerRepository.deleteAll();

        // Create test partner
        testPartner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        testTopicId = UUID.randomUUID();
    }

    @Test
    void should_returnPartnerVotes_when_listVotesCalled() throws Exception {
        // Given - partner has no votes initially

        // When/Then
        mockMvc.perform(get("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void should_castVoteOnTopic_when_validVoteValueProvided() throws Exception {
        // Given
        Map<String, Object> castVoteRequest = new HashMap<>();
        castVoteRequest.put("topicId", testTopicId.toString());
        castVoteRequest.put("voteValue", 5);

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(castVoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicId").value(testTopicId.toString()))
                .andExpect(jsonPath("$.voteValue").value(5))
                .andExpect(jsonPath("$.voteWeight").value(3))  // GOLD = 3
                .andExpect(jsonPath("$.votedAt").exists());
    }

    @Test
    void should_applyVoteWeight_when_partnershipLevelChanges() throws Exception {
        // Test different partnership levels and their vote weights

        // Test BRONZE (weight 1)
        Partner bronzePartner = createTestPartner("BronzeCo", PartnershipLevel.BRONZE);
        UUID topicId1 = UUID.randomUUID();
        Map<String, Object> bronzeVoteRequest = new HashMap<>();
        bronzeVoteRequest.put("topicId", topicId1.toString());
        bronzeVoteRequest.put("voteValue", 5);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", bronzePartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bronzeVoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteWeight").value(1));

        // Test SILVER (weight 2)
        Partner silverPartner = createTestPartner("SilverCo", PartnershipLevel.SILVER);
        UUID topicId2 = UUID.randomUUID();
        Map<String, Object> silverVoteRequest = new HashMap<>();
        silverVoteRequest.put("topicId", topicId2.toString());
        silverVoteRequest.put("voteValue", 4);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", silverPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(silverVoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteWeight").value(2));

        // Test GOLD (weight 3)
        Partner goldPartner = createTestPartner("GoldCo", PartnershipLevel.GOLD);
        UUID topicId3 = UUID.randomUUID();
        Map<String, Object> goldVoteRequest = new HashMap<>();
        goldVoteRequest.put("topicId", topicId3.toString());
        goldVoteRequest.put("voteValue", 3);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", goldPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goldVoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteWeight").value(3));

        // Test PLATINUM (weight 4)
        Partner platinumPartner = createTestPartner("PlatinumCo", PartnershipLevel.PLATINUM);
        UUID topicId4 = UUID.randomUUID();
        Map<String, Object> platinumVoteRequest = new HashMap<>();
        platinumVoteRequest.put("topicId", topicId4.toString());
        platinumVoteRequest.put("voteValue", 5);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", platinumPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(platinumVoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteWeight").value(4));

        // Test STRATEGIC (weight 5)
        Partner strategicPartner = createTestPartner("StrategicCo", PartnershipLevel.STRATEGIC);
        UUID topicId5 = UUID.randomUUID();
        Map<String, Object> strategicVoteRequest = new HashMap<>();
        strategicVoteRequest.put("topicId", topicId5.toString());
        strategicVoteRequest.put("voteValue", 5);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", strategicPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(strategicVoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteWeight").value(5));
    }

    @Test
    void should_return400_when_voteValueOutOfRange() throws Exception {
        // Test vote value < 1
        Map<String, Object> invalidLowVoteRequest = new HashMap<>();
        invalidLowVoteRequest.put("topicId", testTopicId.toString());
        invalidLowVoteRequest.put("voteValue", 0);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLowVoteRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Vote value must be between 1 and 5")));

        // Test vote value > 5
        Map<String, Object> invalidHighVoteRequest = new HashMap<>();
        invalidHighVoteRequest.put("topicId", UUID.randomUUID().toString());
        invalidHighVoteRequest.put("voteValue", 6);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidHighVoteRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Vote value must be between 1 and 5")));
    }

    @Test
    void should_return409_when_partnerAlreadyVotedOnTopic() throws Exception {
        // Given - cast initial vote
        Map<String, Object> castVoteRequest = new HashMap<>();
        castVoteRequest.put("topicId", testTopicId.toString());
        castVoteRequest.put("voteValue", 5);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(castVoteRequest)))
                .andExpect(status().isCreated());

        // When/Then - attempt to vote again on same topic
        Map<String, Object> duplicateVoteRequest = new HashMap<>();
        duplicateVoteRequest.put("topicId", testTopicId.toString());
        duplicateVoteRequest.put("voteValue", 3);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateVoteRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already voted")));
    }

    @Test
    void should_return404_when_partnerNotFound() throws Exception {
        // Given
        Map<String, Object> castVoteRequest = new HashMap<>();
        castVoteRequest.put("topicId", testTopicId.toString());
        castVoteRequest.put("voteValue", 5);

        // When/Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", "NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(castVoteRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Partner not found")));
    }

    @Test
    void should_returnVoteHistory_when_listVotesCalledAfterVoting() throws Exception {
        // Given - cast multiple votes
        UUID topic1 = UUID.randomUUID();
        UUID topic2 = UUID.randomUUID();

        Map<String, Object> vote1Request = new HashMap<>();
        vote1Request.put("topicId", topic1.toString());
        vote1Request.put("voteValue", 5);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vote1Request)))
                .andExpect(status().isCreated());

        Map<String, Object> vote2Request = new HashMap<>();
        vote2Request.put("topicId", topic2.toString());
        vote2Request.put("voteValue", 3);

        mockMvc.perform(post("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vote2Request)))
                .andExpect(status().isCreated());

        // When/Then - list votes
        mockMvc.perform(get("/api/v1/partners/{companyName}/votes", testPartner.getCompanyName())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].topicId").value(hasItems(topic1.toString(), topic2.toString())))
                .andExpect(jsonPath("$[*].voteValue").exists())
                .andExpect(jsonPath("$[*].voteWeight").exists())
                .andExpect(jsonPath("$[*].votedAt").exists());
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
