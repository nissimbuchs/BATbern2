package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.event.SessionMaterialsUploadedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.11d: session materials on the upload contract, purpose {@code session-material}, target
 * {@code {eventCode, sessionSlug}}. Organizers 100 MB and many types on any session, speakers 50 MB
 * and slides on a session they are on. The confirm with the target creates the
 * {@code session_materials} row; an upload confirmed without one is taken by
 * {@code POST /events/{eventCode}/sessions/{sessionSlug}/materials}. Real controllers, upload
 * service, handler and materials service on PostgreSQL; S3 is the Mockito mock of
 * {@link TestAwsConfig} (every object exists, copies succeed).
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows.
 */
@Import(TestAwsConfig.class)
@RecordApplicationEvents
@DisplayName("Uploads: session-material (Story 17.11d)")
class SessionMaterialUploadIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9951";
    private static final String OTHER_EVENT = "BATbern9952";
    private static final String OWN = "it-17-11d-own-session";
    private static final String FOREIGN = "it-17-11d-foreign-session";
    private static final String OTHER_EVENT_SESSION = "it-17-11d-other-event-session";
    private static final String ORGANIZER = "it.orga.11d";
    private static final String SPEAKER = "it.speaker.11d";
    private static final String PDF = "application/pdf";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final long MB = 1024L * 1024;

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
    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void setUp() {
        removeRows();
        Event event = saveEvent(EVENT, 9951);
        Event other = saveEvent(OTHER_EVENT, 9952);
        Session own = saveSession(event, OWN);
        saveSession(event, FOREIGN);
        saveSession(other, OTHER_EVENT_SESSION);
        sessionUserRepository.saveAndFlush(SessionUser.builder().session(own).username(SPEAKER)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER).build());
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        String sessions = "SELECT id FROM sessions WHERE session_slug IN (?, ?, ?)";
        jdbc.update("DELETE FROM session_materials WHERE session_id IN (" + sessions + ")", OWN, FOREIGN,
                OTHER_EVENT_SESSION);
        jdbc.update("DELETE FROM session_users WHERE session_id IN (" + sessions + ")", OWN, FOREIGN,
                OTHER_EVENT_SESSION);
        jdbc.update("DELETE FROM sessions WHERE session_slug IN (?, ?, ?)", OWN, FOREIGN, OTHER_EVENT_SESSION);
        jdbc.update("DELETE FROM event_management_uploads WHERE created_by IN (?, ?)", ORGANIZER, SPEAKER);
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT, OTHER_EVENT);
    }

    private Event saveEvent(String code, int number) {
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        return eventRepository.saveAndFlush(Event.builder().eventCode(code).title("Materials Event " + number)
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

    private static String target(String eventCode, String sessionSlug) {
        StringBuilder keys = new StringBuilder();
        if (eventCode != null) {
            keys.append("\"eventCode\": \"").append(eventCode).append('"');
        }
        if (sessionSlug != null) {
            keys.append(keys.isEmpty() ? "" : ", ").append("\"sessionSlug\": \"").append(sessionSlug).append('"');
        }
        return "{" + keys + "}";
    }

    private static String create(String fileName, String type, long size, String eventCode, String sessionSlug) {
        String target = eventCode == null && sessionSlug == null ? ""
                : ", \"target\": " + target(eventCode, sessionSlug);
        return "{\"fileName\": \"" + fileName + "\", \"contentType\": \"" + type + "\", \"size\": " + size
                + target + "}";
    }

    private String reserve(RequestPostProcessor who, String fileName, String type, long size, String eventCode,
            String sessionSlug) throws Exception {
        return json(post("/api/v1/uploads/session-material"), who,
                create(fileName, type, size, eventCode, sessionSlug), 201).path("uploadId").asText();
    }

    private JsonNode confirm(RequestPostProcessor who, String uploadId, String eventCode, String sessionSlug,
            int expectedStatus) throws Exception {
        String body = eventCode == null && sessionSlug == null ? null
                : "{\"target\": " + target(eventCode, sessionSlug) + "}";
        return json(post("/api/v1/uploads/session-material/{id}/confirm", uploadId), who, body, expectedStatus);
    }

    private JsonNode materials(String eventCode, String sessionSlug) throws Exception {
        return json(get("/api/v1/events/{ec}/sessions/{slug}/materials", eventCode, sessionSlug), organizer(), null,
                200).path("materials");
    }

    private JsonNode associate(RequestPostProcessor who, String sessionSlug, String items, int expectedStatus)
            throws Exception {
        return json(post("/api/v1/events/{ec}/sessions/{slug}/materials", EVENT, sessionSlug), who,
                "{\"materials\": [" + items + "]}", expectedStatus);
    }

    private int materialRows() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM session_materials WHERE session_id IN"
                + " (SELECT id FROM sessions WHERE session_slug IN (?, ?, ?))", Integer.class, OWN, FOREIGN,
                OTHER_EVENT_SESSION);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("organizer, existing session: confirm with the target creates the material, listed and downloadable")
    void should_createMaterial_when_organizerConfirmsForExistingSession() throws Exception {
        String uploadId = reserve(organizer(), "Vortrag Müller.pdf", PDF, 2 * MB, EVENT, FOREIGN);

        JsonNode done = confirm(organizer(), uploadId, EVENT, FOREIGN, 200);

        String key = "materials/events/" + EVENT + "/sessions/" + FOREIGN + "/file-" + uploadId + ".pdf";
        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        assertThat(done.path("purpose").asText()).isEqualTo("session-material");
        assertThat(done.path("fileUrl").asText()).endsWith("/" + key);

        JsonNode list = materials(EVENT, FOREIGN);
        assertThat(list).hasSize(1);
        JsonNode material = list.get(0);
        assertThat(material.path("uploadId").asText()).isEqualTo(uploadId);
        assertThat(material.path("fileName").asText()).isEqualTo("Vortrag Müller.pdf");
        assertThat(material.path("fileExtension").asText()).isEqualTo("pdf");
        assertThat(material.path("fileSize").asLong()).isEqualTo(2 * MB);
        assertThat(material.path("mimeType").asText()).isEqualTo(PDF);
        assertThat(material.path("materialType").asText()).isEqualTo("DOCUMENT");
        assertThat(material.path("s3Key").asText()).isEqualTo(key);
        assertThat(material.path("uploadedBy").asText()).isEqualTo(ORGANIZER);

        JsonNode download = json(get("/api/v1/events/{ec}/sessions/{slug}/materials/{id}/download", EVENT, FOREIGN,
                material.path("id").asText()), organizer(), null, 200);
        // The download is saved under the original name (Content-Disposition in the presigned URL).
        String downloadUrl = URLDecoder.decode(download.path("downloadUrl").asText(), StandardCharsets.UTF_8);
        assertThat(downloadUrl).contains("response-content-disposition=attachment; filename*=UTF-8''"
                + "Vortrag%20M%C3%BCller.pdf");

        Map<String, Object> upload = jdbc.queryForMap(
                "SELECT status, target_type, target_id, file_name FROM event_management_uploads WHERE upload_id = ?",
                uploadId);
        assertThat(upload.get("status")).isEqualTo("associated");
        assertThat(upload.get("target_type")).isEqualTo("SESSION");
        assertThat(upload.get("target_id")).isEqualTo(EVENT + "/" + FOREIGN);
        assertThat(upload.get("file_name")).isEqualTo("Vortrag Müller.pdf");

        List<SessionMaterialsUploadedEvent> events = applicationEvents.stream(SessionMaterialsUploadedEvent.class)
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getSessionSlug()).isEqualTo(FOREIGN);
        assertThat(events.get(0).getEventCode()).isEqualTo(EVENT);
        assertThat(events.get(0).getUploadIds()).containsExactly(uploadId);
        assertThat(events.get(0).getMaterialTypes()).containsExactly("DOCUMENT");
    }

    @Test
    @DisplayName("organizer rules: 100 MB and the organizer type list; more or another type is 400")
    void should_applyOrganizerRules_when_organizerUploads() throws Exception {
        reserve(organizer(), "aufzeichnung.mp4", "video/mp4", 100 * MB, EVENT, FOREIGN);
        reserve(organizer(), "beispiele.zip", "application/zip", MB, EVENT, FOREIGN);

        json(post("/api/v1/uploads/session-material"), organizer(),
                create("aufzeichnung.mp4", "video/mp4", 100 * MB + 1, EVENT, FOREIGN), 400);
        json(post("/api/v1/uploads/session-material"), organizer(),
                create("tool.exe", "application/octet-stream", MB, EVENT, FOREIGN), 400);
    }

    @Test
    @DisplayName("organizer, new session: confirm without target, then the association takes uploadId and type")
    void should_attachConfirmedUploads_when_associatedAfterSessionSave() throws Exception {
        String slides = reserve(organizer(), "Folien.pptx", PPTX, 3 * MB, null, null);
        String handout = reserve(organizer(), "Handout.pdf", PDF, MB, null, null);
        assertThat(confirm(organizer(), slides, null, null, 200).path("status").asText()).isEqualTo("CONFIRMED");
        confirm(organizer(), handout, null, null, 200);
        assertThat(materialRows()).isZero();

        JsonNode result = associate(organizer(), FOREIGN, "{\"uploadId\": \"" + slides
                + "\", \"materialType\": \"OTHER\"}, {\"uploadId\": \"" + handout + "\"}", 201);

        assertThat(result.path("materials")).hasSize(2);
        JsonNode list = materials(EVENT, FOREIGN);
        assertThat(list).hasSize(2);
        assertThat(list.findValuesAsText("fileName")).containsExactlyInAnyOrder("Folien.pptx", "Handout.pdf");
        JsonNode first = list.get(0).path("uploadId").asText().equals(slides) ? list.get(0) : list.get(1);
        JsonNode second = first == list.get(0) ? list.get(1) : list.get(0);
        assertThat(first.path("materialType").asText()).isEqualTo("OTHER");
        assertThat(first.path("fileSize").asLong()).isEqualTo(3 * MB);
        assertThat(first.path("s3Key").asText())
                .isEqualTo("materials/events/" + EVENT + "/sessions/" + FOREIGN + "/file-" + slides + ".pptx");
        assertThat(second.path("materialType").asText()).isEqualTo("DOCUMENT");
        assertThat(second.path("mimeType").asText()).isEqualTo(PDF);

        // Attached: a second association of the same upload is 409.
        associate(organizer(), FOREIGN, "{\"uploadId\": \"" + slides + "\"}", 409);
    }

    @Test
    @DisplayName("association: an upload of another purpose, unknown or expired is 404, a pending one 409")
    void should_refuseAssociation_when_uploadIsNotAConfirmedSessionMaterial() throws Exception {
        String photo = json(post("/api/v1/uploads/event-photo"), organizer(),
                "{\"fileName\": \"a.jpg\", \"contentType\": \"image/jpeg\", \"size\": 10, \"target\":"
                        + " {\"eventCode\": \"" + EVENT + "\"}}", 201).path("uploadId").asText();
        associate(organizer(), FOREIGN, "{\"uploadId\": \"" + photo + "\"}", 404);
        associate(organizer(), FOREIGN, "{\"uploadId\": \"3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b\"}", 404);

        String expired = reserve(organizer(), "alt.pdf", PDF, MB, null, null);
        confirm(organizer(), expired, null, null, 200);
        jdbc.update("UPDATE event_management_uploads SET expires_at = now() - interval '1 minute'"
                + " WHERE upload_id = ?", expired);
        associate(organizer(), FOREIGN, "{\"uploadId\": \"" + expired + "\"}", 404);

        String pending = reserve(organizer(), "neu.pdf", PDF, MB, null, null);
        associate(organizer(), FOREIGN, "{\"uploadId\": \"" + pending + "\"}", 409);
        assertThat(materialRows()).isZero();
    }

    @Test
    @DisplayName("speaker, own session: slides up to 50 MB become a PRESENTATION; 60 MB or .mp4 is 400")
    void should_createPresentation_when_speakerUploadsToOwnSession() throws Exception {
        String uploadId = reserve(speaker(), "Meine Folien.pptx", PPTX, 50 * MB, EVENT, OWN);

        confirm(speaker(), uploadId, EVENT, OWN, 200);

        JsonNode list = materials(EVENT, OWN);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("materialType").asText()).isEqualTo("PRESENTATION");
        assertThat(list.get(0).path("fileName").asText()).isEqualTo("Meine Folien.pptx");
        assertThat(list.get(0).path("uploadedBy").asText()).isEqualTo(SPEAKER);

        // A PDF from a speaker is a PRESENTATION too (the speaker default), not a DOCUMENT.
        String pdf = reserve(speaker(), "slides.pdf", PDF, MB, EVENT, OWN);
        confirm(speaker(), pdf, EVENT, OWN, 200);
        assertThat(materials(EVENT, OWN).findValuesAsText("materialType")).containsOnly("PRESENTATION");

        json(post("/api/v1/uploads/session-material"), speaker(),
                create("gross.pptx", PPTX, 60 * MB, EVENT, OWN), 400);
        json(post("/api/v1/uploads/session-material"), speaker(),
                create("video.mp4", "video/mp4", MB, EVENT, OWN), 400);
    }

    @Test
    @DisplayName("a speaker's material is a PRESENTATION even when the association names VIDEO")
    void should_ignoreMaterialType_when_speakerAssociates() throws Exception {
        String uploadId = reserve(speaker(), "folien.pdf", PDF, MB, null, null);
        confirm(speaker(), uploadId, null, null, 200);

        associate(speaker(), OWN, "{\"uploadId\": \"" + uploadId + "\", \"materialType\": \"VIDEO\"}", 201);

        assertThat(materials(EVENT, OWN).get(0).path("materialType").asText()).isEqualTo("PRESENTATION");
    }

    @Test
    @DisplayName("delete removes the material and its upload row; a slug of another event is 404")
    void should_deleteMaterialAndUploadRow_when_deleted() throws Exception {
        String uploadId = reserve(organizer(), "weg.pdf", PDF, MB, EVENT, FOREIGN);
        confirm(organizer(), uploadId, EVENT, FOREIGN, 200);
        String materialId = materials(EVENT, FOREIGN).get(0).path("id").asText();

        // The session is not in OTHER_EVENT: 404, nothing deleted.
        json(delete("/api/v1/events/{ec}/sessions/{slug}/materials/{id}", OTHER_EVENT, FOREIGN, materialId),
                organizer(), null, 404);
        assertThat(materialRows()).isEqualTo(1);

        json(delete("/api/v1/events/{ec}/sessions/{slug}/materials/{id}", EVENT, FOREIGN, materialId),
                organizer(), null, 204);

        assertThat(materialRows()).isZero();
        Integer uploads = jdbc.queryForObject("SELECT count(*) FROM event_management_uploads WHERE upload_id = ?",
                Integer.class, uploadId);
        assertThat(uploads).isZero();
    }

    @Test
    @DisplayName("speaker, other session: 403 on reserve, on confirm and on association")
    void should_return403_when_speakerTargetsAnotherSession() throws Exception {
        json(post("/api/v1/uploads/session-material"), speaker(), create("a.pdf", PDF, MB, EVENT, FOREIGN), 403);

        String uploadId = reserve(speaker(), "a.pdf", PDF, MB, null, null);
        confirm(speaker(), uploadId, EVENT, FOREIGN, 403);
        confirm(speaker(), uploadId, null, null, 200);
        associate(speaker(), FOREIGN, "{\"uploadId\": \"" + uploadId + "\"}", 403);
        assertThat(materialRows()).isZero();
    }

    @Test
    @DisplayName("an attendee may not upload session materials at all (403)")
    void should_return403_when_callerIsNeitherOrganizerNorSpeaker() throws Exception {
        json(post("/api/v1/uploads/session-material"), user("it.attendee.11d").roles("ATTENDEE"),
                create("a.pdf", PDF, MB, null, null), 403);
    }

    @Test
    @DisplayName("reserved for session A, confirmed or associated for B: 404, nothing created")
    void should_return404_when_confirmedForAnotherSession() throws Exception {
        String uploadId = reserve(organizer(), "a.pdf", PDF, MB, EVENT, OWN);

        confirm(organizer(), uploadId, EVENT, FOREIGN, 404);
        confirm(organizer(), uploadId, OTHER_EVENT, OTHER_EVENT_SESSION, 404);

        // Confirmed without a target, it stays bound to A: the association for B is 404 too.
        String other = reserve(organizer(), "b.pdf", PDF, MB, EVENT, OWN);
        confirm(organizer(), other, null, null, 200);
        associate(organizer(), FOREIGN, "{\"uploadId\": \"" + other + "\"}", 404);
        assertThat(materialRows()).isZero();
        associate(organizer(), OWN, "{\"uploadId\": \"" + other + "\"}", 201);
    }

    @Test
    @DisplayName("a session of another event, or an unknown one, is 404")
    void should_return404_when_sessionIsNotInTheEvent() throws Exception {
        json(post("/api/v1/uploads/session-material"), organizer(),
                create("a.pdf", PDF, MB, EVENT, OTHER_EVENT_SESSION), 404);
        json(post("/api/v1/uploads/session-material"), organizer(),
                create("a.pdf", PDF, MB, EVENT, "it-17-11d-no-such-session"), 404);
    }

    @Test
    @DisplayName("a partial target (only sessionSlug, only eventCode) is 400")
    void should_return400_when_targetIsPartial() throws Exception {
        json(post("/api/v1/uploads/session-material"), organizer(), create("a.pdf", PDF, MB, null, OWN), 400);
        json(post("/api/v1/uploads/session-material"), organizer(), create("a.pdf", PDF, MB, EVENT, null), 400);

        String uploadId = reserve(organizer(), "a.pdf", PDF, MB, null, null);
        json(post("/api/v1/uploads/session-material/{id}/confirm", uploadId), organizer(),
                "{\"target\": {\"sessionSlug\": \"" + OWN + "\"}}", 400);
    }

    @Test
    @DisplayName("the removed material upload paths are gone (no alias, Story 17.11d)")
    void should_notServeOldMaterialUploadPaths() throws Exception {
        json(post("/api/v1/materials/presigned-url"), organizer(),
                "{\"fileName\": \"a.pdf\", \"fileSize\": 10, \"mimeType\": \"application/pdf\"}", 404);
        json(post("/api/v1/materials/{id}/confirm", "3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b"), organizer(),
                "{\"fileId\": \"x\"}", 404);
        json(post("/api/v1/speaker-portal/events/{ec}/materials/presigned-url", EVENT), speaker(),
                "{\"fileName\": \"a.pdf\", \"fileSize\": 10, \"mimeType\": \"application/pdf\"}", 404);
        json(post("/api/v1/speaker-portal/events/{ec}/materials/confirm", EVENT), speaker(),
                "{\"uploadId\": \"x\"}", 404);
    }
}
