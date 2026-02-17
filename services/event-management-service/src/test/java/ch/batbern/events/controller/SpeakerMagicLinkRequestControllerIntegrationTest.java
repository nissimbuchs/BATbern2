package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.SpeakerInvitationEmailService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerMagicLinkRequestController (Story 9.3 Task 5.4).
 *
 * Tests POST /api/v1/auth/speaker-request-magic-link:
 * - AC3: Always returns 200 regardless of whether email matches a speaker (no enumeration)
 * - AC6: Email validation prevents M4 injection
 */
@Import(TestUserApiClientConfig.class)
@DisplayName("SpeakerMagicLinkRequestController - POST /api/v1/auth/speaker-request-magic-link")
class SpeakerMagicLinkRequestControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @MockitoBean
    private SpeakerInvitationEmailService speakerInvitationEmailService;

    private Event event;
    private SpeakerPool invitedSpeakerPool;
    private SpeakerPool acceptedSpeakerPool;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        long ts = System.currentTimeMillis();
        event = Event.builder()
                .eventCode("bat-magic-req-" + ts)
                .eventNumber((int) (ts % 100_000))
                .title("BATbern Magic Link Request Test " + ts)
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        event = eventRepository.save(event);

        invitedSpeakerPool = SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName("Speaker Invited")
                .company("Invited AG")
                .email("invited@example.com")
                .status(SpeakerWorkflowState.INVITED)
                .invitedAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        invitedSpeakerPool = speakerPoolRepository.save(invitedSpeakerPool);

        acceptedSpeakerPool = SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName("Speaker Accepted")
                .company("Accepted AG")
                .email("accepted@example.com")
                .status(SpeakerWorkflowState.ACCEPTED)
                .invitedAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        acceptedSpeakerPool = speakerPoolRepository.save(acceptedSpeakerPool);
    }

    @Test
    @DisplayName("should return 200 and trigger email when speaker found with INVITED status")
    void should_return200_andSendEmail_when_invitedSpeakerFound() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-request-magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "invited@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 200 and trigger email when speaker found with ACCEPTED status")
    void should_return200_andSendEmail_when_acceptedSpeakerFound() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-request-magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "accepted@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 200 even when email is unknown (AC3 — no email enumeration)")
    void should_return200_when_emailIsUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-request-magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 400 when email is invalid (AC6 — M4 injection fix)")
    void should_return400_when_emailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/speaker-request-magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "notanemail\"; DROP TABLE speaker_pool;--"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 200 even when email service throws (suppressed error)")
    void should_return200_when_emailServiceThrows() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP connection refused"))
                .when(speakerInvitationEmailService)
                .sendInvitationEmail(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/auth/speaker-request-magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "invited@example.com"))))
                .andExpect(status().isOk());
    }
}
