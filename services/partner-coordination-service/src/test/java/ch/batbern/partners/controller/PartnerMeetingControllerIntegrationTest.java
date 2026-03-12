package ch.batbern.partners.controller;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.partners.repository.PartnerRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Partner Meeting Coordination API — Story 8.3.
 * Tests cover AC1–6 (create, agenda, invite, notes, list, role-based access).
 *
 * Uses AbstractIntegrationTest (PostgreSQL Testcontainer).
 * Written with TDD: tests describe expected behavior precisely.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerMeetingControllerIntegrationTest extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/partner-meetings";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PartnerMeetingRepository meetingRepository;
    @Autowired
    PartnerRepository partnerRepository;

    @MockitoBean
    EventManagementClient eventManagementClient;
    @MockitoBean
    UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        meetingRepository.deleteAll();
        partnerRepository.deleteAll();

        // Default stub: any event code → a known event summary
        when(eventManagementClient.getEventSummary(anyString()))
                .thenReturn(new EventSummaryDTO(
                        "BATbern57",
                        "BATbern Spring 2026",
                        LocalDate.of(2026, 5, 14),
                        LocalTime.of(18, 0),
                        LocalTime.of(22, 0),
                        "Bern Congress Centre"
                ));

        // Stub getUsersByRole so sendInvite can collect partner emails
        UserResponse mockPartner = new UserResponse();
        mockPartner.setEmail("contact@partner.com");
        when(userServiceClient.getUsersByRole("PARTNER")).thenReturn(List.of(mockPartner));

        // Stub organizer role — empty by default; individual tests override when needed
        when(userServiceClient.getUsersByRole("ORGANIZER")).thenReturn(List.of());
    }

    // ─── AC1: Create meeting record ───────────────────────────────────────────

    @Test
    void should_createMeeting_when_validRequestProvided() throws Exception {
        Map<String, Object> req = createMeetingRequest("BATbern57", "SPRING", "12:00", "14:00", "Lunch Hall", null);

        mockMvc.perform(post(BASE).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventCode", is("BATbern57")))
                .andExpect(jsonPath("$.meetingType", is("SPRING")))
                .andExpect(jsonPath("$.meetingDate", is("2026-05-14")))  // auto-filled from event
                .andExpect(jsonPath("$.startTime", is("12:00:00")))
                .andExpect(jsonPath("$.endTime", is("14:00:00")))
                .andExpect(jsonPath("$.location", is("Lunch Hall")));
    }

    @Test
    void should_return400_when_eventCodeMissing() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("meetingType", "SPRING");
        req.put("startTime", "12:00");
        req.put("endTime", "14:00");

        mockMvc.perform(post(BASE).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_meetingTypeMissing() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("eventCode", "BATbern57");
        req.put("startTime", "12:00");
        req.put("endTime", "14:00");

        mockMvc.perform(post(BASE).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── AC2: Agenda update ───────────────────────────────────────────────────

    @Test
    void should_updateAgenda_when_patchedByOrganizer() throws Exception {
        String meetingId = createMeeting("BATbern57");

        Map<String, String> update = new HashMap<>();
        update.put("agenda", "1. Introductions\n2. Partnership review\n3. Next steps");

        mockMvc.perform(patch(BASE + "/" + meetingId).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agenda", is("1. Introductions\n2. Partnership review\n3. Next steps")));
    }

    // ─── AC3: Send calendar invite (202 + async) ──────────────────────────────

    @Test
    void should_return202_when_sendInviteCalled() throws Exception {
        String meetingId = createMeeting("BATbern57");

        mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.meetingId", is(meetingId)));
    }

    @Test
    void should_setInviteSentAt_when_sendInviteCalled() throws Exception {
        // Create partner contact so invite has recipients
        createPartnerWithContact("TestCo", "testcontact");

        String meetingId = createMeeting("BATbern57");

        mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isAccepted());

        // Verify invite_sent_at was persisted
        mockMvc.perform(get(BASE + "/" + meetingId).with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteSentAt", notNullValue()));
    }

    // ─── AC4: Meeting notes ───────────────────────────────────────────────────

    @Test
    void should_updateNotes_when_patchedByOrganizer() throws Exception {
        String meetingId = createMeeting("BATbern57");

        Map<String, String> update = new HashMap<>();
        update.put("notes", "Great discussion. AlphaCo presented roadmap.");

        mockMvc.perform(patch(BASE + "/" + meetingId).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes", is("Great discussion. AlphaCo presented roadmap.")));
    }

    @Test
    void should_updateAgendaAndNotes_independently() throws Exception {
        String meetingId = createMeeting("BATbern57");

        // Update agenda
        Map<String, String> agendaUpdate = new HashMap<>();
        agendaUpdate.put("agenda", "Agenda text");
        mockMvc.perform(patch(BASE + "/" + meetingId).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agendaUpdate)))
                .andExpect(status().isOk());

        // Update notes — agenda should remain
        Map<String, String> notesUpdate = new HashMap<>();
        notesUpdate.put("notes", "Notes text");
        mockMvc.perform(patch(BASE + "/" + meetingId).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notesUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agenda", is("Agenda text")))
                .andExpect(jsonPath("$.notes", is("Notes text")));
    }

    // ─── AC5: Meeting list ────────────────────────────────────────────────────

    @Test
    void should_returnEmptyList_when_noMeetingsExist() throws Exception {
        mockMvc.perform(get(BASE).with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void should_listMeetings_when_meetingsExist() throws Exception {
        createMeeting("BATbern57");
        createMeeting("BATbern56");

        mockMvc.perform(get(BASE).with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void should_return404_when_meetingNotFound() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
    }

    // ─── AC6: Role-based access (non-organizer → 403) ────────────────────────

    @Test
    void should_return403_when_partnerTriesToListMeetings() throws Exception {
        mockMvc.perform(get(BASE).with(user("alice").roles("PARTNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return403_when_partnerTriesToCreateMeeting() throws Exception {
        Map<String, Object> req = createMeetingRequest("BATbern57", "SPRING", "12:00", "14:00", null, null);

        mockMvc.perform(post(BASE).with(user("alice").roles("PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return403_when_partnerTriesToSendInvite() throws Exception {
        String meetingId = createMeeting("BATbern57");

        mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("alice").roles("PARTNER")))
                .andExpect(status().isForbidden());
    }

    // ─── SEQUENCE increment (RFC 5545 calendar update recognition) ───────────

    @Test
    void should_incrementInviteSequence_on_each_sendInvite() throws Exception {
        String meetingId = createMeeting("BATbern57");

        // First send → sequence becomes 1
        mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isAccepted());

        // Second send → sequence becomes 2
        mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isAccepted());

        PartnerMeeting meeting = meetingRepository.findById(UUID.fromString(meetingId)).orElseThrow();
        assertThat(meeting.getInviteSequence()).isEqualTo(2);
    }

    // ─── Organizer recipients ─────────────────────────────────────────────────

    @Test
    void should_includeOrganizers_in_recipientCount() throws Exception {
        UserResponse organizerUser = new UserResponse();
        organizerUser.setEmail("organizer@batbern.ch");
        when(userServiceClient.getUsersByRole("ORGANIZER")).thenReturn(List.of(organizerUser));

        String meetingId = createMeeting("BATbern57");

        MvcResult result = mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // 1 partner (setUp mock) + 1 organizer = 2
        assertThat(json.get("recipientCount").asInt()).isEqualTo(2);
    }

    @Test
    void should_deduplicateRecipients_when_userHasBothRoles() throws Exception {
        // Same email in both PARTNER and ORGANIZER roles
        String sharedEmail = "both@batbern.ch";
        UserResponse sharedUser = new UserResponse();
        sharedUser.setEmail(sharedEmail);
        when(userServiceClient.getUsersByRole("PARTNER")).thenReturn(List.of(sharedUser));
        when(userServiceClient.getUsersByRole("ORGANIZER")).thenReturn(List.of(sharedUser));

        String meetingId = createMeeting("BATbern57");

        MvcResult result = mockMvc.perform(post(BASE + "/" + meetingId + "/send-invite")
                        .with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("recipientCount").asInt()).isEqualTo(1);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String createMeeting(String eventCode) throws Exception {
        Map<String, Object> req = createMeetingRequest(eventCode, "SPRING", "12:00", "14:00", null, null);

        MvcResult result = mockMvc.perform(post(BASE).with(user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private Map<String, Object> createMeetingRequest(String eventCode, String meetingType,
            String startTime, String endTime, String location, String agenda) {
        Map<String, Object> req = new HashMap<>();
        req.put("eventCode", eventCode);
        req.put("meetingType", meetingType);
        req.put("startTime", startTime);
        req.put("endTime", endTime);
        if (location != null) {
            req.put("location", location);
        }
        if (agenda != null) {
            req.put("agenda", agenda);
        }
        return req;
    }

    private void createPartnerWithContact(String companyName, String username) {
        partnerRepository.save(Partner.builder()
                .companyName(companyName)
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.now().minusYears(1))
                .partnershipEndDate(LocalDate.now().plusYears(1))
                .build());
    }
}
