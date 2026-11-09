package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI assist with AI switched off, the test default ({@code batbern.ai.enabled=false}, no key):
 * the real {@code BatbernAiService} builds no OpenAI client, and every generating endpoint
 * answers 503 instead of failing or calling out. With AI on: {@link AiAssistControllerIntegrationTest}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class AiAssistDisabledIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    SessionRepository sessionRepository;
    @Autowired
    SpeakerPoolRepository speakerPoolRepository;

    private Event event;
    private Session session;
    private SpeakerPool speaker;

    @BeforeEach
    void setUp() {
        String code = "BATaiOff" + UUID.randomUUID().toString().substring(0, 8);
        Event e = new Event();
        e.setEventCode(code);
        e.setEventNumber(ThreadLocalRandom.current().nextInt(800_000, 899_999));
        e.setTitle("BATbern " + code);
        e.setDate(Instant.now().plus(60, ChronoUnit.DAYS));
        e.setRegistrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS));
        e.setVenueName("Kornhausforum");
        e.setVenueAddress("Kornhausplatz 18, 3011 Bern");
        e.setVenueCapacity(200);
        e.setOrganizerUsername("carla.muster");
        e.setEventType(EventType.EVENING);
        e.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        event = eventRepository.save(e);

        session = sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(code)
                .title("Talk")
                .description("An abstract.")
                .sessionSlug("ai-off-" + UUID.randomUUID())
                .sessionType("presentation")
                .build());
        SpeakerPool s = new SpeakerPool();
        s.setEventId(event.getId());
        s.setSpeakerName("Brünnimann");
        s.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        s.setSessionId(session.getId());
        speaker = speakerPoolRepository.save(s);
    }

    @AfterEach
    void tearDown() {
        speakerPoolRepository.deleteById(speaker.getId());
        sessionRepository.deleteById(session.getId());
        eventRepository.deleteById(event.getId());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenAiIsOff_returns503() throws Exception {
        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/speakers/" + speaker.getId()
                        + "/abstract-analysis"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenAiIsOff_returns503() throws Exception {
        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/description"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_whenAiIsOff_returns503() throws Exception {
        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/theme-image"))
                .andExpect(status().isServiceUnavailable());
    }
}
