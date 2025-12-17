package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerOutreachController.
 * Story 5.3: Speaker Outreach Tracking
 *
 * Tests outreach recording and history retrieval with authorization and validation.
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class SpeakerOutreachControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private OutreachHistoryRepository outreachHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Event testEvent;
    private SpeakerPool testSpeaker;
    private String eventCode;
    private UUID speakerId;

    @BeforeEach
    void setUp() {
        // Clean database
        outreachHistoryRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        eventCode = "BATbern999";
        testEvent = Event.builder()
                .eventCode(eventCode)
                .eventNumber(999)
                .title("Test Event")
                .description("Test Description")
                .date(Instant.now().plusSeconds(86400))
                .registrationDeadline(Instant.now())
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Create test speaker in pool
        testSpeaker = new SpeakerPool();
        testSpeaker.setEventId(testEvent.getId());
        testSpeaker.setSpeakerName("Jane Smith");
        testSpeaker.setCompany("Tech Corp AG");
        testSpeaker.setExpertise("Cloud Architecture, Kubernetes");
        testSpeaker.setStatus(SpeakerWorkflowState.IDENTIFIED);
        testSpeaker.setAssignedOrganizerId("alice.mueller");
        testSpeaker.setNotes("Met at KubeCon 2024");
        testSpeaker = speakerPoolRepository.save(testSpeaker);
        speakerId = testSpeaker.getId();
    }

    /**
     * AC1-4: Record outreach attempt for speaker in IDENTIFIED state.
     * Should create outreach history record and transition speaker to CONTACTED.
     */
    @Test
    @WithMockUser(username = "alice.mueller", roles = "ORGANIZER")
    void should_recordOutreach_when_speakerIdentified() throws Exception {
        // Given: Request to record outreach
        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "email");
        request.put("notes", "Reached out about Kubernetes security talk. She's interested.");

        // When: POST outreach attempt
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Returns 201 CREATED
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.speakerPoolId").value(speakerId.toString()))
                .andExpect(jsonPath("$.contactMethod").value("email"))
                .andExpect(jsonPath("$.notes").value("Reached out about Kubernetes security talk. She's interested."))
                .andExpect(jsonPath("$.organizerUsername").value("alice.mueller"));

        // Verify: Outreach history record created
        List<OutreachHistory> history = outreachHistoryRepository.findBySpeakerPoolId(speakerId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getContactMethod()).isEqualTo("email");
        assertThat(history.get(0).getOrganizerUsername()).isEqualTo("alice.mueller");

        // Verify: Speaker state transitioned to CONTACTED
        SpeakerPool updated = speakerPoolRepository.findById(speakerId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
    }

    /**
     * AC5: Record additional outreach for already-contacted speaker.
     * Should create new outreach record without changing state.
     */
    @Test
    @WithMockUser(username = "bob.schmidt", roles = "ORGANIZER")
    void should_recordOutreach_when_speakerAlreadyContacted() throws Exception {
        // Given: Speaker already in CONTACTED state
        testSpeaker.setStatus(SpeakerWorkflowState.CONTACTED);
        speakerPoolRepository.save(testSpeaker);

        // Given: Request to record follow-up outreach
        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "phone");
        request.put("notes", "Follow-up call. She confirmed availability for March.");

        // When: POST follow-up outreach
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Returns 201 CREATED
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactMethod").value("phone"))
                .andExpect(jsonPath("$.organizerUsername").value("bob.schmidt"));

        // Verify: New outreach record created
        List<OutreachHistory> history = outreachHistoryRepository.findBySpeakerPoolId(speakerId);
        assertThat(history).hasSize(1);

        // Verify: Speaker state remains CONTACTED
        SpeakerPool updated = speakerPoolRepository.findById(speakerId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
    }

    /**
     * AC6: Get outreach history for speaker.
     * Should return all outreach attempts ordered by most recent first.
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnOutreachHistory_when_speakerHasHistory() throws Exception {
        // Given: Multiple outreach attempts
        OutreachHistory outreach1 = new OutreachHistory();
        outreach1.setSpeakerPoolId(speakerId);
        outreach1.setContactDate(Instant.now().minusSeconds(86400)); // 1 day ago
        outreach1.setContactMethod("email");
        outreach1.setNotes("Initial outreach");
        outreach1.setOrganizerUsername("alice.mueller");
        outreachHistoryRepository.save(outreach1);

        OutreachHistory outreach2 = new OutreachHistory();
        outreach2.setSpeakerPoolId(speakerId);
        outreach2.setContactDate(Instant.now().minusSeconds(3600)); // 1 hour ago
        outreach2.setContactMethod("phone");
        outreach2.setNotes("Follow-up call");
        outreach2.setOrganizerUsername("bob.schmidt");
        outreachHistoryRepository.save(outreach2);

        // When: GET outreach history
        mockMvc.perform(get("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId))
                // Then: Returns 200 OK with history (most recent first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].contactMethod").value("phone")) // Most recent
                .andExpect(jsonPath("$[0].organizerUsername").value("bob.schmidt"))
                .andExpect(jsonPath("$[1].contactMethod").value("email")) // Oldest
                .andExpect(jsonPath("$[1].organizerUsername").value("alice.mueller"));
    }

    /**
     * Error case: Record outreach for non-existent speaker.
     * Should return 404 NOT FOUND.
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_speakerNotFound() throws Exception {
        // Given: Non-existent speaker ID
        UUID nonExistentId = UUID.randomUUID();

        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "email");
        request.put("notes", "Test notes");

        // When/Then: POST returns 404
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * Error case: Record outreach with invalid contact method.
     * Should return 400 BAD REQUEST.
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_invalidContactMethod() throws Exception {
        // Given: Request with invalid contact method
        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "sms"); // Invalid - only email, phone, in_person allowed
        request.put("notes", "Test notes");

        // When/Then: POST returns 400
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Error case: Record outreach for speaker in DECLINED state.
     * Should return 409 CONFLICT.
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return409_when_speakerDeclined() throws Exception {
        // Given: Speaker in DECLINED state
        testSpeaker.setStatus(SpeakerWorkflowState.DECLINED);
        speakerPoolRepository.save(testSpeaker);

        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "email");
        request.put("notes", "Test notes");

        // When/Then: POST returns 409 CONFLICT
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    /**
     * Authorization test: Non-organizer cannot record outreach.
     * Should return 403 FORBIDDEN.
     */
    @Test
    @WithMockUser(username = "speaker.user", roles = "SPEAKER")
    void should_return403_when_notOrganizer() throws Exception {
        // Given: Request from non-organizer
        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "email");
        request.put("notes", "Test notes");

        // When/Then: POST returns 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    /**
     * Validation test: Record outreach with all contact methods.
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_recordOutreach_when_inPersonContactMethod() throws Exception {
        // Given: Request with in_person contact method
        Map<String, Object> request = new HashMap<>();
        request.put("contactDate", OffsetDateTime.now(ZoneOffset.UTC).toString());
        request.put("contactMethod", "in_person");
        request.put("notes", "Met at tech conference");

        // When: POST outreach attempt
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // Then: Returns 201 CREATED
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactMethod").value("in_person"));
    }

    /**
     * Empty history test: Get outreach history for speaker with no outreach.
     * Should return empty list.
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnEmptyList_when_noOutreachHistory() throws Exception {
        // Given: Speaker with no outreach history

        // When: GET outreach history
        mockMvc.perform(get("/api/v1/events/{eventCode}/speakers/{speakerId}/outreach",
                                eventCode, speakerId))
                // Then: Returns 200 OK with empty list
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
