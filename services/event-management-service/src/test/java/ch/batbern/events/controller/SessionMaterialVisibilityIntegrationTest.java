package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1260: every session material carries {@code publiclyVisible}. A new one is public when it is a
 * PDF, on all three create paths (confirm with target, association, batch import from a URL); the
 * confirm and the association may say otherwise. Organizer and the session's speakers change it by
 * PATCH, which the cached event detail shows at once. Real controllers, services and security chain
 * on PostgreSQL; S3 is the Mockito mock of {@link TestAwsConfig}; the batch import fetches its file
 * from a local HTTP server (the wire).
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows.
 */
@Import(TestAwsConfig.class)
@DisplayName("Session materials: publiclyVisible (#1260)")
class SessionMaterialVisibilityIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9953";
    private static final String OTHER_EVENT = "BATbern9954";
    private static final String OWN = "it-1260-own-session";
    private static final String FOREIGN = "it-1260-foreign-session";
    private static final String OTHER_EVENT_SESSION = "it-1260-other-event-session";
    private static final String ORGANIZER = "it.orga.1260";
    private static final String SPEAKER = "it.speaker.1260";
    private static final String OTHER_SPEAKER = "other.speaker.1260";
    private static final String PDF = "application/pdf";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private HttpServer fileServer;

    @BeforeEach
    void setUp() {
        removeRows();
        Event event = saveEvent(EVENT, 9953);
        Event other = saveEvent(OTHER_EVENT, 9954);
        Session own = saveSession(event, OWN);
        Session foreign = saveSession(event, FOREIGN);
        saveSession(other, OTHER_EVENT_SESSION);
        sessionUserRepository.saveAndFlush(SessionUser.builder().session(own).username(SPEAKER)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).build());
        sessionUserRepository.saveAndFlush(SessionUser.builder().session(foreign).username(OTHER_SPEAKER)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).build());
    }

    @AfterEach
    void tearDown() {
        if (fileServer != null) {
            fileServer.stop(0);
        }
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM event_management_uploads WHERE created_by IN (?, ?)", ORGANIZER, SPEAKER);
        // Sessions, their speakers and materials cascade from the event (V2, V7, V41).
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT, OTHER_EVENT);
    }

    private Event saveEvent(String code, int number) {
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        return eventRepository.saveAndFlush(Event.builder().eventCode(code).title("Visibility Event " + number)
                .eventNumber(number).date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100)
                .organizerUsername(ORGANIZER).currentAttendeeCount(0).eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED).build());
    }

    private Session saveSession(Event event, String slug) {
        Instant start = event.getDate().plus(1, ChronoUnit.HOURS);
        return sessionRepository.saveAndFlush(Session.builder().sessionSlug(slug).eventId(event.getId())
                .eventCode(event.getEventCode()).title("Session " + slug).sessionType("presentation")
                .startTime(start).endTime(start.plus(45, ChronoUnit.MINUTES))
                .materialsCount(0).hasPresentation(false).materialsStatus("NONE").build());
    }

    private static RequestPostProcessor organizer() {
        return user(ORGANIZER).roles("ORGANIZER");
    }

    private static RequestPostProcessor speaker() {
        return user(SPEAKER).roles("SPEAKER");
    }

    private JsonNode json(MockHttpServletRequestBuilder request, RequestPostProcessor who, String body,
            int expectedStatus) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mockMvc.perform(request.with(who))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }

    private String reserve(RequestPostProcessor who, String fileName, String type, String sessionSlug)
            throws Exception {
        String target = sessionSlug == null ? ""
                : ", \"target\": {\"eventCode\": \"" + EVENT + "\", \"sessionSlug\": \"" + sessionSlug + "\"}";
        return json(post("/api/v1/uploads/session-material"), who, "{\"fileName\": \"" + fileName
                + "\", \"contentType\": \"" + type + "\", \"size\": 1024" + target + "}", 201)
                .path("uploadId").asText();
    }

    /** Confirms with the target; {@code visible} null leaves the flag out. */
    private void confirmFor(RequestPostProcessor who, String uploadId, String sessionSlug, Boolean visible)
            throws Exception {
        String flag = visible == null ? "" : ", \"publiclyVisible\": " + visible;
        json(post("/api/v1/uploads/session-material/{id}/confirm", uploadId), who,
                "{\"target\": {\"eventCode\": \"" + EVENT + "\", \"sessionSlug\": \"" + sessionSlug + "\"}"
                        + flag + "}", 200);
    }

    /** Uploads a file to the session through the confirm with the target; returns the material id. */
    private String material(RequestPostProcessor who, String fileName, String type, String sessionSlug,
            Boolean visible) throws Exception {
        String uploadId = reserve(who, fileName, type, sessionSlug);
        confirmFor(who, uploadId, sessionSlug, visible);
        return byUpload(sessionSlug, uploadId).path("id").asText();
    }

    private JsonNode materials(String sessionSlug) throws Exception {
        return json(get("/api/v1/events/{ec}/sessions/{slug}/materials", EVENT, sessionSlug), organizer(), null,
                200).path("materials");
    }

    private JsonNode byUpload(String sessionSlug, String uploadId) throws Exception {
        for (JsonNode m : materials(sessionSlug)) {
            if (m.path("uploadId").asText().equals(uploadId)) {
                return m;
            }
        }
        throw new AssertionError("no material for upload " + uploadId);
    }

    private JsonNode byFileName(String sessionSlug, String fileName) throws Exception {
        for (JsonNode m : materials(sessionSlug)) {
            if (m.path("fileName").asText().equals(fileName)) {
                return m;
            }
        }
        throw new AssertionError("no material " + fileName);
    }

    private JsonNode setVisible(RequestPostProcessor who, String sessionSlug, String materialId, String body,
            int expectedStatus) throws Exception {
        return json(patch("/api/v1/events/{ec}/sessions/{slug}/materials/{id}", EVENT, sessionSlug, materialId),
                who, body, expectedStatus);
    }

    private Boolean stored(String materialId) {
        return jdbc.queryForObject("SELECT publicly_visible FROM session_materials WHERE id = ?::uuid",
                Boolean.class, materialId);
    }

    @Test
    @DisplayName("confirm with target: a PDF is public, a PPTX is not")
    void should_defaultByType_when_confirmedWithTarget() throws Exception {
        String pdf = reserve(speaker(), "folien.pdf", PDF, OWN);
        String pptx = reserve(speaker(), "folien.pptx", PPTX, OWN);
        confirmFor(speaker(), pdf, OWN, null);
        confirmFor(speaker(), pptx, OWN, null);

        assertThat(byUpload(OWN, pdf).path("publiclyVisible").asBoolean()).isTrue();
        assertThat(byUpload(OWN, pptx).path("publiclyVisible").isBoolean()).isTrue();
        assertThat(byUpload(OWN, pptx).path("publiclyVisible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("confirm with target: an explicit value wins over the type default")
    void should_honourExplicitValue_when_confirmedWithTarget() throws Exception {
        String pptx = reserve(organizer(), "intern.pptx", PPTX, FOREIGN);
        String pdf = reserve(organizer(), "intern.pdf", PDF, FOREIGN);
        confirmFor(organizer(), pptx, FOREIGN, true);
        confirmFor(organizer(), pdf, FOREIGN, false);

        assertThat(byUpload(FOREIGN, pptx).path("publiclyVisible").asBoolean()).isTrue();
        assertThat(byUpload(FOREIGN, pdf).path("publiclyVisible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("association: default by type, an item's value wins")
    void should_defaultByType_andHonourItemValue_when_associated() throws Exception {
        String pdf = reserve(organizer(), "handout.pdf", PDF, null);
        String pptx = reserve(organizer(), "slides.pptx", PPTX, null);
        String shared = reserve(organizer(), "shared.pptx", PPTX, null);
        for (String id : new String[] {pdf, pptx, shared}) {
            json(post("/api/v1/uploads/session-material/{id}/confirm", id), organizer(), null, 200);
        }

        JsonNode created = json(post("/api/v1/events/{ec}/sessions/{slug}/materials", EVENT, FOREIGN), organizer(),
                "{\"materials\": [{\"uploadId\": \"" + pdf + "\"}, {\"uploadId\": \"" + pptx + "\"},"
                        + " {\"uploadId\": \"" + shared + "\", \"publiclyVisible\": true}]}", 201);

        assertThat(created.path("materials").findValuesAsText("publiclyVisible"))
                .containsExactly("true", "false", "true");
        assertThat(byUpload(FOREIGN, pdf).path("publiclyVisible").asBoolean()).isTrue();
        assertThat(byUpload(FOREIGN, pptx).path("publiclyVisible").asBoolean()).isFalse();
        assertThat(byUpload(FOREIGN, shared).path("publiclyVisible").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("batch import from a URL: a PDF is public, a PPTX is not")
    void should_defaultByType_when_importedFromUrl() throws Exception {
        fileServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fileServer.createContext("/", exchange -> {
            byte[] body = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fileServer.start();
        String base = "http://localhost:" + fileServer.getAddress().getPort() + "/";

        JsonNode result = json(post("/api/v1/events/{ec}/sessions", EVENT), organizer(),
                "{\"imports\": [{\"bat\": 9953, \"title\": \"Import PDF\", \"abstract\": \"a\", \"materialUrl\": \""
                        + base + "BAT9953_talk.pdf\", \"referenten\": []},"
                        + " {\"bat\": 9953, \"title\": \"Import PPTX\", \"abstract\": \"b\", \"materialUrl\": \""
                        + base + "BAT9953_talk.pptx\", \"referenten\": []}]}", 200);
        assertThat(result.path("successfullyCreated").asInt()).isEqualTo(2);

        Boolean pdf = jdbc.queryForObject("SELECT m.publicly_visible FROM session_materials m JOIN sessions s"
                + " ON s.id = m.session_id WHERE s.event_code = ? AND m.file_name = 'BAT9953_talk.pdf'",
                Boolean.class, EVENT);
        Boolean pptx = jdbc.queryForObject("SELECT m.publicly_visible FROM session_materials m JOIN sessions s"
                + " ON s.id = m.session_id WHERE s.event_code = ? AND m.file_name = 'BAT9953_talk.pptx'",
                Boolean.class, EVENT);
        assertThat(pdf).isTrue();
        assertThat(pptx).isFalse();
    }

    @Test
    @DisplayName("PATCH: organizer and the session's speaker change the flag; the list shows it")
    void should_changeFlag_when_organizerOrSessionSpeaker() throws Exception {
        String id = material(speaker(), "slides.pptx", PPTX, OWN, null);

        JsonNode byOrganizer = setVisible(organizer(), OWN, id, "{\"publiclyVisible\": true}", 200);
        assertThat(byOrganizer.path("id").asText()).isEqualTo(id);
        assertThat(byOrganizer.path("publiclyVisible").asBoolean()).isTrue();
        assertThat(byFileName(OWN, "slides.pptx").path("publiclyVisible").asBoolean()).isTrue();

        JsonNode bySpeaker = setVisible(speaker(), OWN, id, "{\"publiclyVisible\": false}", 200);
        assertThat(bySpeaker.path("publiclyVisible").asBoolean()).isFalse();
        assertThat(stored(id)).isFalse();
    }

    @Test
    @DisplayName("PATCH: another session's speaker and an attendee 403, anonymous 401; nothing changes")
    void should_refuse_when_callerMayNotChangeTheSession() throws Exception {
        String id = material(speaker(), "slides.pdf", PDF, OWN, null);

        setVisible(user(OTHER_SPEAKER).roles("SPEAKER"), OWN, id, "{\"publiclyVisible\": false}", 403);
        setVisible(user("it.attendee.1260").roles("ATTENDEE"), OWN, id, "{\"publiclyVisible\": false}", 403);
        setVisible(anonymous(), OWN, id, "{\"publiclyVisible\": false}", 401);

        assertThat(stored(id)).isTrue();
    }

    @Test
    @DisplayName("PATCH: a material of another session or event, or an unknown id, is 404")
    void should_return404_when_materialIsNotInThatSession() throws Exception {
        String id = material(organizer(), "slides.pptx", PPTX, FOREIGN, null);

        setVisible(organizer(), OWN, id, "{\"publiclyVisible\": true}", 404);
        json(patch("/api/v1/events/{ec}/sessions/{slug}/materials/{id}", OTHER_EVENT, FOREIGN, id), organizer(),
                "{\"publiclyVisible\": true}", 404);
        setVisible(organizer(), FOREIGN, UUID.randomUUID().toString(), "{\"publiclyVisible\": true}", 404);

        assertThat(stored(id)).isFalse();
    }

    @Test
    @DisplayName("PATCH: a body without the flag or with a non-boolean is 400")
    void should_return400_when_bodyIsInvalid() throws Exception {
        String id = material(organizer(), "slides.pdf", PDF, FOREIGN, null);

        setVisible(organizer(), FOREIGN, id, "{}", 400);
        setVisible(organizer(), FOREIGN, id, "{\"publiclyVisible\": \"maybe\"}", 400);

        assertThat(stored(id)).isTrue();
    }

    @Test
    @DisplayName("PATCH: the cached event detail shows the new value right away")
    void should_showNewValueInCachedEventDetail_when_patched() throws Exception {
        String id = material(organizer(), "slides.pdf", PDF, FOREIGN, null);
        detail();
        MvcResult cached = detail();
        // Positive control: the second read is served from the cache.
        assertThat(cached.getResponse().getHeader("X-Cache-Status")).isEqualTo("HIT");
        assertThat(flag(cached, id)).isTrue();

        setVisible(organizer(), FOREIGN, id, "{\"publiclyVisible\": false}", 200);

        assertThat(flag(detail(), id)).isFalse();
    }

    @Test
    @DisplayName("a deleted material leaves the cached event detail at once")
    void should_dropMaterialFromCachedEventDetail_when_deleted() throws Exception {
        String id = material(organizer(), "slides.pdf", PDF, FOREIGN, null);
        detail();
        MvcResult cached = detail();
        assertThat(cached.getResponse().getHeader("X-Cache-Status")).isEqualTo("HIT");
        assertThat(contains(cached, id)).isTrue();

        mockMvc.perform(delete("/api/v1/events/{ec}/sessions/{slug}/materials/{id}", EVENT, FOREIGN, id)
                        .with(organizer()))
                .andExpect(status().is2xxSuccessful());

        assertThat(contains(detail(), id)).isFalse();
    }

    private boolean contains(MvcResult detail, String materialId) throws Exception {
        for (JsonNode session : objectMapper.readTree(detail.getResponse().getContentAsString()).path("sessions")) {
            for (JsonNode m : session.path("materials")) {
                if (m.path("id").asText().equals(materialId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private MvcResult detail() throws Exception {
        return mockMvc.perform(get("/api/v1/events/{ec}", EVENT).param("include", "sessions").with(organizer()))
                .andExpect(status().isOk()).andReturn();
    }

    private boolean flag(MvcResult detail, String materialId) throws Exception {
        for (JsonNode session : objectMapper.readTree(detail.getResponse().getContentAsString()).path("sessions")) {
            for (JsonNode m : session.path("materials")) {
                if (m.path("id").asText().equals(materialId)) {
                    return m.path("publiclyVisible").asBoolean();
                }
            }
        }
        throw new AssertionError("material " + materialId + " not in the event detail");
    }
}
