package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.service.BatbernAiService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestSecurityConfig.class, TestAwsConfig.class})
class AiAssistControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BatbernAiService aiService;

    @MockitoBean
    EventRepository eventRepository;

    @MockitoBean
    TopicRepository topicRepository;

    @MockitoBean
    SpeakerPoolRepository speakerPoolRepository;

    @MockitoBean
    SessionRepository sessionRepository;

    private static final UUID SPEAKER_POOL_ID = UUID.randomUUID();

    private Event buildEvent() {
        Event event = new Event();
        event.setEventCode("BATbern99");
        event.setTitle("BATbern 99");
        event.setEventNumber(99);
        event.setDate(Instant.parse("2026-04-04T18:00:00Z"));
        event.setTopicCode("cloud-native");
        return event;
    }

    private Topic buildTopic() {
        Topic topic = new Topic();
        topic.setTitle("Cloud Native");
        topic.setCategory("DEVOPS");
        topic.setDescription("Cloud native architectures and practices.");
        return topic;
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenAiReturnsEmpty_returns503() throws Exception {
        when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(buildEvent()));
        when(topicRepository.findByTopicCode("cloud-native")).thenReturn(Optional.of(buildTopic()));
        when(aiService.generateEventDescription(anyString(), any()))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/description"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenAiReturnsResult_returns200() throws Exception {
        when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(buildEvent()));
        when(topicRepository.findByTopicCode("cloud-native")).thenReturn(Optional.of(buildTopic()));
        when(aiService.generateEventDescription(anyString(), any()))
            .thenReturn(Optional.of("BATbern#99 widmet sich dem Thema Cloud Native..."));

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/description"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").isNotEmpty());
    }

    // 2026-11-07: an image takes ~40 s, the AWS HTTP API cuts every request at 30 s, so the
    // generation runs in the background: POST answers 202 with a job, the page polls the job.

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_whenAiUnavailable_returns503() throws Exception {
        when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(buildEvent()));
        when(aiService.isAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/theme-image"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_returns202_andTheJobEndsDoneWithTheImage() throws Exception {
        when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(buildEvent()));
        when(topicRepository.findByTopicCode("cloud-native")).thenReturn(Optional.of(buildTopic()));
        when(aiService.isAvailable()).thenReturn(true);
        var result = new BatbernAiService.ThemeImageResult(
            "https://cdn.batbern.ch/ai-themes/abc.png", "ai-themes/abc.png");
        when(aiService.generateThemeImage(anyString(), any(), any()))
            .thenReturn(Optional.of(result));

        String jobId = startJob();
        JsonNode job = awaitJob(jobId);

        assertThat(job.path("status").asText()).isEqualTo("DONE");
        assertThat(job.path("imageUrl").asText()).isEqualTo("https://cdn.batbern.ch/ai-themes/abc.png");
        assertThat(job.path("s3Key").asText()).isEqualTo("ai-themes/abc.png");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_whenGenerationFails_jobEndsFailed() throws Exception {
        when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(buildEvent()));
        when(topicRepository.findByTopicCode("cloud-native")).thenReturn(Optional.of(buildTopic()));
        when(aiService.isAvailable()).thenReturn(true);
        when(aiService.generateThemeImage(anyString(), any(), any())).thenReturn(Optional.empty());

        JsonNode job = awaitJob(startJob());

        assertThat(job.path("status").asText()).isEqualTo("FAILED");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void themeImageJob_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern99/ai/theme-image/jobs/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    void themeImageJob_nonOrganizer_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern99/ai/theme-image/jobs/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isForbidden());
    }

    private String startJob() throws Exception {
        String body = mockMvc.perform(post("/api/v1/events/BATbern99/ai/theme-image"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).path("jobId").asText();
    }

    private JsonNode awaitJob(String jobId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mockMvc.perform(get("/api/v1/events/BATbern99/ai/theme-image/jobs/" + jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            JsonNode job = new ObjectMapper().readTree(body);
            if (!"PENDING".equals(job.path("status").asText())) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("theme image job still pending: " + jobId);
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenAiReturnsResult_returns200() throws Exception {
        SpeakerPool pool = new SpeakerPool();
        pool.setId(SPEAKER_POOL_ID);
        pool.setSpeakerName("Alice");
        UUID sessionId = UUID.randomUUID();
        pool.setSessionId(sessionId);

        Session session = new Session();
        session.setId(sessionId);
        session.setTitle("Cloud Native in Practice");
        session.setDescription("My abstract text about cloud native lessons learned.");

        when(speakerPoolRepository.findById(SPEAKER_POOL_ID)).thenReturn(Optional.of(pool));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        var result = new BatbernAiService.AbstractAnalysisResult(
            8, "Kein Produktmarketing erkennbar.", 9, "Klare Praxisreferenz vorhanden.", 120, null);
        when(aiService.analyzeAbstract(anyString(), anyString(), anyString())).thenReturn(Optional.of(result));

        mockMvc.perform(post("/api/v1/speakers/" + SPEAKER_POOL_ID + "/ai/analyze-abstract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noPromotionScore").value(8))
            .andExpect(jsonPath("$.lessonsLearnedScore").value(9))
            .andExpect(jsonPath("$.wordCount").value(120));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenAiReturnsEmpty_returns503() throws Exception {
        SpeakerPool pool = new SpeakerPool();
        pool.setId(SPEAKER_POOL_ID);
        pool.setSpeakerName("Alice");
        when(speakerPoolRepository.findById(SPEAKER_POOL_ID)).thenReturn(Optional.of(pool));
        when(aiService.analyzeAbstract(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/speakers/" + SPEAKER_POOL_ID + "/ai/analyze-abstract"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void generateDescription_withoutAuth_returns401or403() throws Exception {
        mockMvc.perform(post("/api/v1/events/BATbern99/ai/description"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void applyThemeImage_whenEventNotFound_returns404() throws Exception {
        when(eventRepository.findByEventCode("NON_EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/events/NON_EXISTENT/ai/theme-image/apply")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void applyThemeImage_withoutAuth_returns401or403() throws Exception {
        mockMvc.perform(post("/api/v1/events/BATbern99/ai/theme-image/apply")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
            .andExpect(status().is4xxClientError());
    }
}
