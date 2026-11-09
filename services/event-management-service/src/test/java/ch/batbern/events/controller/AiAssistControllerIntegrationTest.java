package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.service.BatbernAiService;
import ch.batbern.events.service.OpenAiTestServer;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI assist endpoints end to end (Story 4.9 moved them off own-code mocks): real controller,
 * repositories, prompts and {@link BatbernAiService}; OpenAI is answered at the wire by
 * {@link OpenAiTestServer} and S3 by the {@code TestAwsConfig} boundary mock.
 *
 * <p>The abstract analysis lives under its event, {@code POST /events/{eventCode}/speakers/
 * {speakerId}/abstract-analysis}, and loads the speaker pool entry scoped to that event: an entry
 * of another event answers 404 and never reaches OpenAI. With AI switched off the endpoints answer
 * 503 ({@code AiAssistDisabledIntegrationTest}).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@TestPropertySource(properties = {"batbern.ai.enabled=true", "openai.api-key=test-key"})
class AiAssistControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String IMAGE_URL = "https://api.openai.com/v1/images/generations";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    BatbernAiService aiService;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    TopicRepository topicRepository;
    @Autowired
    SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    SessionRepository sessionRepository;

    private MockRestServiceServer openAi;
    private final List<Runnable> cleanup = new ArrayList<>();
    private Event event;
    private Event otherEvent;

    @BeforeEach
    void setUp() {
        openAi = OpenAiTestServer.bindTo(aiService);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Topic topic = new Topic();
        topic.setTopicCode("cloud-native-" + suffix);
        topic.setTitle("Cloud Native");
        topic.setCategory("technical");
        topic.setDescription("Cloud native architectures and practices.");
        Topic savedTopic = topicRepository.save(topic);
        cleanup.add(() -> topicRepository.deleteById(savedTopic.getId()));

        event = saveEvent("BATai" + suffix, savedTopic.getTopicCode());
        otherEvent = saveEvent("BATaiX" + suffix, null);
    }

    @AfterEach
    void tearDown() {
        OpenAiTestServer.release(aiService);
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            cleanup.get(i).run();
        }
        cleanup.clear();
    }

    // ── event description ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenOpenAiAnswers_returns200WithTheText() throws Exception {
        openAi.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.messages[1].content").value(
                        containsString("Cloud Native")))
                .andRespond(withSuccess(chat("BATbern widmet sich dem Thema Cloud Native."),
                        MediaType.APPLICATION_JSON));

        String body = mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/description"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(MAPPER.readTree(body).path("description").asText())
                .isEqualTo("BATbern widmet sich dem Thema Cloud Native.");
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenOpenAiFails_returns503() throws Exception {
        openAi.expect(requestTo(CHAT_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/description"))
                .andExpect(status().isServiceUnavailable());
        openAi.verify();
    }

    @Test
    void generateDescription_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/description"))
                .andExpect(status().isUnauthorized());
    }

    // ── theme image: 202 + job, because an image takes longer than the 30 s API cut ─────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_returns202_andTheJobEndsDoneWithTheImage() throws Exception {
        String png = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        openAi.expect(requestTo(IMAGE_URL))
                .andRespond(withSuccess(MAPPER.writeValueAsString(
                        Map.of("data", List.of(Map.of("b64_json", png)))), MediaType.APPLICATION_JSON));

        JsonNode job = awaitJob(startJob());

        assertThat(job.path("status").asText()).isEqualTo("DONE");
        assertThat(job.path("s3Key").asText()).startsWith("ai-themes/img_").endsWith(".png");
        assertThat(job.path("imageUrl").asText()).endsWith("/" + job.path("s3Key").asText());
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_whenOpenAiReturnsNoImage_jobEndsFailed() throws Exception {
        openAi.expect(requestTo(IMAGE_URL))
                .andRespond(withSuccess("{\"data\": []}", MediaType.APPLICATION_JSON));

        JsonNode job = awaitJob(startJob());

        assertThat(job.path("status").asText()).isEqualTo("FAILED");
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void themeImageJob_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode()
                        + "/ai/theme-image/jobs/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    void themeImageJob_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + event.getEventCode()
                        + "/ai/theme-image/jobs/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void applyThemeImage_persistsTheUrlOnTheEvent() throws Exception {
        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/theme-image/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
                .andExpect(status().isOk());

        assertThat(eventRepository.findByEventCode(event.getEventCode()).orElseThrow().getThemeImageUrl())
                .isEqualTo("https://cdn.batbern.ch/ai-themes/abc.png");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void applyThemeImage_whenEventNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/events/NON_EXISTENT/ai/theme-image/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void applyThemeImage_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/theme-image/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── abstract analysis, nested under the event ──────────────────────────────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenOpenAiAnswers_returns200WithTheReview() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "Hông Ân",
                "Cloud Native in Practice", "My abstract about cloud native lessons learned.");
        openAi.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[1].content").value(
                        containsString("cloud native lessons learned")))
                .andRespond(withSuccess(chat("""
                        {"noPromotionScore": 8, "noPromotionFeedback": "Kein Produktmarketing erkennbar.",
                         "lessonsLearnedScore": 9, "lessonsLearnedFeedback": "Klare Praxisreferenz vorhanden.",
                         "wordCount": 7, "shortenedAbstract": null}"""), MediaType.APPLICATION_JSON));

        String body = mockMvc.perform(post(analysisPath(event, speaker.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode review = MAPPER.readTree(body);
        assertThat(review.path("noPromotionScore").asInt()).isEqualTo(8);
        assertThat(review.path("lessonsLearnedScore").asInt()).isEqualTo(9);
        assertThat(review.path("wordCount").asInt()).isEqualTo(7);
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenSpeakerBelongsToAnotherEvent_returns404WithoutCallingOpenAi() throws Exception {
        SpeakerPool foreign = saveSpeakerWithSession(otherEvent, "Brünnimann",
                "Other talk", "An abstract of another event.");
        // positive control: the same entry is found under its own event
        openAi.expect(requestTo(CHAT_URL)).andRespond(withSuccess(chat("{\"wordCount\": 6}"),
                MediaType.APPLICATION_JSON));
        mockMvc.perform(post(analysisPath(otherEvent, foreign.getId())))
                .andExpect(status().isOk());
        openAi.verify();

        // under another event it is not found, and no request reaches OpenAI (none is expected)
        openAi.reset();
        mockMvc.perform(post(analysisPath(event, foreign.getId())))
                .andExpect(status().isNotFound());
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenEventIsUnknown_returns404() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "O’Neill", "Talk", "Abstract.");

        mockMvc.perform(post("/api/v1/events/NON_EXISTENT/speakers/" + speaker.getId() + "/abstract-analysis"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenOpenAiFails_returns503() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "王", "Talk", "Another abstract.");
        openAi.expect(requestTo(CHAT_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        mockMvc.perform(post(analysisPath(event, speaker.getId())))
                .andExpect(status().isServiceUnavailable());
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenOpenAiReturnsNoCompletion_returns503() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "Matthias Keller", "Talk", "Empty answer abstract.");
        openAi.expect(requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post(analysisPath(event, speaker.getId())))
                .andExpect(status().isServiceUnavailable());
        openAi.verify();
    }

    /**
     * A completion body that is not JSON at all fails to bind and answers 503. (Malformed JSON
     * inside a well-formed completion's content is a different case: it falls back to the
     * neutral scores by Story 15.9 design, see BatbernAiSecurityTest.)
     */
    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenOpenAiCompletionIsUnparseable_returns503() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "Matthias Keller", "Talk", "Garbled answer abstract.");
        openAi.expect(requestTo(CHAT_URL))
                .andRespond(withSuccess("<html>upstream proxy error</html>", MediaType.APPLICATION_JSON));

        mockMvc.perform(post(analysisPath(event, speaker.getId())))
                .andExpect(status().isServiceUnavailable());
        openAi.verify();
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    void analyzeAbstract_asSpeaker_returns403() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "Speaker", "Talk", "Abstract.");

        mockMvc.perform(post(analysisPath(event, speaker.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void formerAnalyzeAbstractPath_isGone() throws Exception {
        SpeakerPool speaker = saveSpeakerWithSession(event, "Speaker", "Talk", "Abstract.");

        mockMvc.perform(post("/api/v1/speakers/" + speaker.getId() + "/ai/analyze-abstract"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String analysisPath(Event owner, UUID speakerId) {
        return "/api/v1/events/" + owner.getEventCode() + "/speakers/" + speakerId + "/abstract-analysis";
    }

    private static String chat(String content) throws Exception {
        return MAPPER.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private Event saveEvent(String eventCode, String topicCode) {
        Event e = new Event();
        e.setEventCode(eventCode);
        e.setEventNumber(ThreadLocalRandom.current().nextInt(700_000, 799_999));
        e.setTitle("BATbern " + eventCode);
        e.setDate(Instant.now().plus(60, ChronoUnit.DAYS));
        e.setRegistrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS));
        e.setVenueName("Kornhausforum");
        e.setVenueAddress("Kornhausplatz 18, 3011 Bern");
        e.setVenueCapacity(200);
        e.setOrganizerUsername("carla.muster");
        e.setEventType(EventType.EVENING);
        e.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        e.setTopicCode(topicCode);
        Event saved = eventRepository.save(e);
        cleanup.add(() -> eventRepository.deleteById(saved.getId()));
        return saved;
    }

    private SpeakerPool saveSpeakerWithSession(Event owner, String name, String title, String abstractText) {
        Session session = Session.builder()
                .eventId(owner.getId())
                .eventCode(owner.getEventCode())
                .title(title)
                .description(abstractText)
                .sessionSlug("ai-" + UUID.randomUUID())
                .sessionType("presentation")
                .build();
        Session savedSession = sessionRepository.save(session);
        cleanup.add(() -> sessionRepository.deleteById(savedSession.getId()));

        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(owner.getId());
        speaker.setSpeakerName(name);
        speaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        speaker.setSessionId(savedSession.getId());
        SpeakerPool saved = speakerPoolRepository.save(speaker);
        cleanup.add(() -> speakerPoolRepository.deleteById(saved.getId()));
        return saved;
    }

    private String startJob() throws Exception {
        String body = mockMvc.perform(post("/api/v1/events/" + event.getEventCode() + "/ai/theme-image"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode job = MAPPER.readTree(body);
        assertThat(job.path("status").asText()).isEqualTo("PENDING");
        return job.path("jobId").asText();
    }

    private JsonNode awaitJob(String jobId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mockMvc.perform(get("/api/v1/events/" + event.getEventCode()
                            + "/ai/theme-image/jobs/" + jobId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode job = MAPPER.readTree(body);
            if (!"PENDING".equals(job.path("status").asText())) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("theme image job still pending: " + jobId);
    }
}
