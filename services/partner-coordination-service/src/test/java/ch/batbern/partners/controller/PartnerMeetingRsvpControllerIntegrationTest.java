package ch.batbern.partners.controller;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Partner Meeting RSVP API — Story 10.27.
 * Tests cover AC5 (table), AC6 (internal endpoint), AC7 (GET RSVP list).
 *
 * Uses AbstractIntegrationTest (PostgreSQL Testcontainer).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerMeetingRsvpControllerIntegrationTest extends AbstractIntegrationTest {

    static final String INTERNAL_BASE = "/internal/partner-meetings/rsvps";
    static final String API_BASE = "/api/v1/partner-meetings";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PartnerMeetingRepository meetingRepository;

    @MockitoBean
    EventManagementClient eventManagementClient;
    @MockitoBean
    UserServiceClient userServiceClient;

    private UUID savedMeetingId;

    @BeforeEach
    void setUp() {
        meetingRepository.deleteAll();
        PartnerMeeting meeting = meetingRepository.save(PartnerMeeting.builder()
                .eventCode("BATbern57")
                .meetingType(MeetingType.SPRING)
                .meetingDate(LocalDate.of(2026, 5, 14))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(14, 0))
                .location("Lunch Hall")
                .createdBy("organizer")
                .build());
        savedMeetingId = meeting.getId();
    }

    // ─── AC6: Internal upsert endpoint ────────────────────────────────────────

    @Test
    void should_return200_when_validRsvpPosted() throws Exception {
        Map<String, Object> req = rsvpRequest(savedMeetingId, "alice@partner.com", "ACCEPTED");

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeEmail").value("alice@partner.com"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").exists());
    }

    @Test
    void should_upsertStatus_when_sameAttendeePatchesMultipleTimes() throws Exception {
        // First: ACCEPTED
        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "alice@partner.com", "ACCEPTED"))))
                .andExpect(status().isOk());

        // Second: TENTATIVE — should update, not create duplicate
        MvcResult result = mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "alice@partner.com", "TENTATIVE"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("status").asText()).isEqualTo("TENTATIVE");

        // Verify only one record in the GET response
        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvps.length()").value(1))
                .andExpect(jsonPath("$.rsvps[0].status").value("TENTATIVE"));
    }

    @Test
    void should_return404_when_meetingIdNotFound() throws Exception {
        Map<String, Object> req = rsvpRequest(UUID.randomUUID(), "alice@partner.com", "ACCEPTED");

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return400_when_partStatUnknown() throws Exception {
        Map<String, Object> req = rsvpRequest(savedMeetingId, "alice@partner.com", "MAYBE");

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── AC7: GET RSVP list ────────────────────────────────────────────────────

    @Test
    void should_returnRsvpList_when_organizerRequests() throws Exception {
        // Seed two RSVPs
        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "alice@partner.com", "ACCEPTED"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "bob@partner.com", "DECLINED"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId").value(savedMeetingId.toString()))
                .andExpect(jsonPath("$.rsvps.length()").value(2))
                .andExpect(jsonPath("$.summary.accepted").value(1))
                .andExpect(jsonPath("$.summary.declined").value(1))
                .andExpect(jsonPath("$.summary.tentative").value(0));
    }

    @Test
    void should_return403_when_partnerRequestsRsvpList() throws Exception {
        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("partner-user").roles("PARTNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return404_when_meetingNotFoundOnGetRsvps() throws Exception {
        mockMvc.perform(get(API_BASE + "/" + UUID.randomUUID() + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> rsvpRequest(UUID meetingId, String attendeeEmail, String partStat) {
        Map<String, Object> req = new HashMap<>();
        req.put("meetingId", meetingId);
        req.put("attendeeEmail", attendeeEmail);
        req.put("partStat", partStat);
        return req;
    }
}
