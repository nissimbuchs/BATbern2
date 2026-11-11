package ch.batbern.partners.controller;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Partner Meeting RSVP API — Story 10.27.
 * Tests cover AC5 (table), AC6 (the calendar reply contract EMS calls, Epic 17 entry 8b), AC7 (GET
 * RSVP list).
 *
 * Uses AbstractIntegrationTest (PostgreSQL Testcontainer).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerMeetingRsvpControllerIntegrationTest extends AbstractIntegrationTest {

    /** The calendar reply contract EMS owns (Epic 17 entry 8b), implemented here. */
    static final String INTERNAL_BASE = "/internal/calendar-replies";
    static final String API_BASE = "/api/v1/partner-meetings";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PartnerMeetingRepository meetingRepository;


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
    void should_recordAtOnce_when_validReplyPosted() throws Exception {
        Map<String, Object> req = rsvpRequest(savedMeetingId, "alice@partner.com", "ACCEPTED");

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvps[0].attendeeEmail").value("alice@partner.com"))
                .andExpect(jsonPath("$.rsvps[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.rsvps[0].respondedAt").exists());
    }

    @Test
    void should_upsertStatus_when_sameAttendeePatchesMultipleTimes() throws Exception {
        // First: ACCEPTED
        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "alice@partner.com", "ACCEPTED"))))
                .andExpect(status().isNoContent());

        // Second: TENTATIVE — should update, not create duplicate
        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "alice@partner.com", "TENTATIVE"))))
                .andExpect(status().isNoContent());

        // Verify only one record in the GET response
        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvps.length()").value(1))
                .andExpect(jsonPath("$.rsvps[0].status").value("TENTATIVE"));
    }

    @Test
    void should_updateSameRsvp_when_attendeeEmailDiffersOnlyInCase() throws Exception {
        // Mail clients may send the iMIP REPLY with a differently capitalised ATTENDEE (#1099)
        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "Alice@Partner.com", "ACCEPTED"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "alice@partner.com", "DECLINED"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rsvps.length()").value(1))
                .andExpect(jsonPath("$.rsvps[0].attendeeEmail").value("alice@partner.com"))
                .andExpect(jsonPath("$.rsvps[0].status").value("DECLINED"));
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

    @Test
    void should_acceptIdnDomain_when_attendeeEmailFollowsTheRule() throws Exception {
        // 4.9b: the #1102 rule (ASCII local part, any domain), as the contract spec (ems-internal) says
        Map<String, Object> req = rsvpRequest(savedMeetingId, "juerg.brunner@bücherstube.example", "ACCEPTED");

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API_BASE + "/" + savedMeetingId + "/rsvps")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(jsonPath("$.rsvps[0].attendeeEmail").value("juerg.brunner@bücherstube.example"));
    }

    @Test
    void should_return400_when_attendeeEmailLocalPartIsNotAscii() throws Exception {
        Map<String, Object> req = rsvpRequest(savedMeetingId, "jürg@bücherstube.example", "ACCEPTED");

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
                .andExpect(status().isNoContent());

        mockMvc.perform(post(INTERNAL_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rsvpRequest(savedMeetingId, "bob@partner.com", "DECLINED"))))
                .andExpect(status().isNoContent());

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
