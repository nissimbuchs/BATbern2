package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.11b: event photos on the upload contract, purpose {@code event-photo}, target
 * {@code eventCode} (required), organizers only. The confirm with the target creates the
 * {@code event_photos} row; a confirm for another event than the reserved one is 404. Real
 * controllers, upload service, handler and photo service on PostgreSQL; S3 is the Mockito mock of
 * {@link TestAwsConfig} (every object exists, copies succeed).
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows (the
 * photos cascade from the events).
 */
@Import(TestAwsConfig.class)
@DisplayName("Uploads: event-photo (Story 17.11b)")
class EventPhotoUploadIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9921";
    private static final String OTHER_EVENT = "BATbern9922";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        removeRows();
        // Photos come after the event: both took place minutes ago, so the homepage sample holds them.
        saveEvent(EVENT, 9921, Instant.now().minus(1, ChronoUnit.MINUTES));
        saveEvent(OTHER_EVENT, 9922, Instant.now().minus(2, ChronoUnit.MINUTES));
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM event_management_uploads WHERE purpose = 'event-photo'"
                + " AND created_by IN ('orga.nizer', 'anna.muster')");
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT, OTHER_EVENT);
    }

    private void saveEvent(String code, int number, Instant date) {
        eventRepository.saveAndFlush(Event.builder().eventCode(code).title("Photo Event " + number)
                .eventNumber(number).date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100)
                .organizerUsername("orga.nizer").currentAttendeeCount(0).eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED).build());
    }

    private static RequestPostProcessor organizer() {
        return user("orga.nizer").roles("ORGANIZER");
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

    private static String create(String fileName, String type, long size, String eventCode) {
        String target = eventCode == null ? "" : ", \"target\": {\"eventCode\": \"" + eventCode + "\"}";
        return "{\"fileName\": \"" + fileName + "\", \"contentType\": \"" + type + "\", \"size\": " + size
                + target + "}";
    }

    private static String target(String eventCode) {
        return "{\"target\": {\"eventCode\": \"" + eventCode + "\"}}";
    }

    private String reserve(String eventCode) throws Exception {
        return json(post("/api/v1/uploads/event-photo"), organizer(),
                create("Apéro im Kornhaus.JPG", "image/jpeg", 2_000_000, eventCode), 201)
                .path("uploadId").asText();
    }

    private int photoRows() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM event_photos WHERE event_code IN (?, ?)",
                Integer.class, EVENT, OTHER_EVENT);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("organizer: reserve for the event, confirm with it: the photo is in the gallery and on the homepage")
    void should_createPhoto_when_confirmedForTheReservedEvent() throws Exception {
        String uploadId = reserve(EVENT);

        JsonNode done = json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(),
                target(EVENT), 200);

        String key = "events/" + EVENT + "/photos/" + uploadId + ".jpg";
        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        assertThat(done.path("purpose").asText()).isEqualTo("event-photo");
        assertThat(done.path("fileUrl").asText()).endsWith("/" + key);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM event_photos WHERE event_code = ?", EVENT);
        assertThat(row.get("id").toString()).isEqualTo(uploadId);
        assertThat(row.get("s3_key")).isEqualTo(key);
        assertThat(row.get("display_url")).isEqualTo(done.path("fileUrl").asText());
        assertThat(row.get("filename")).isEqualTo("photo.jpg");
        assertThat(row.get("uploaded_by")).isEqualTo("orga.nizer");
        assertThat(row.get("sort_order")).isEqualTo(0);

        JsonNode gallery = json(get("/api/v1/events/{code}/photos", EVENT), organizer(), null, 200);
        assertThat(gallery).hasSize(1);
        assertThat(gallery.get(0).path("id").asText()).isEqualTo(uploadId);
        assertThat(gallery.get(0).path("displayUrl").asText()).endsWith("/" + key);

        String homepage = mockMvc.perform(get("/api/v1/public/event-photos").param("limit", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(homepage).contains(uploadId);
    }

    @Test
    @DisplayName("a .jpeg file is stored as .jpg, as before Story 17.11b")
    void should_storeJpegAsJpg_when_confirmed() throws Exception {
        String uploadId = json(post("/api/v1/uploads/event-photo"), organizer(),
                create("Bühne.jpeg", "image/jpeg", 2048, EVENT), 201).path("uploadId").asText();

        JsonNode done = json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(),
                target(EVENT), 200);

        String key = "events/" + EVENT + "/photos/" + uploadId + ".jpg";
        assertThat(done.path("fileUrl").asText()).endsWith("/" + key);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM event_photos WHERE id = ?::uuid", uploadId);
        assertThat(row.get("s3_key")).isEqualTo(key);
        assertThat(row.get("filename")).isEqualTo("photo.jpg");
    }

    @Test
    @DisplayName("deleting the photo removes its upload row too (an attached upload never expires)")
    void should_removeUploadRow_when_photoDeleted() throws Exception {
        String uploadId = reserve(EVENT);
        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), target(EVENT), 200);
        assertThat(uploadRows(uploadId)).isEqualTo(1);

        json(delete("/api/v1/events/{code}/photos/{id}", EVENT, uploadId), organizer(), null, 204);

        assertThat(photoRows()).isZero();
        assertThat(uploadRows(uploadId)).isZero();
    }

    @Test
    @DisplayName("an attached photo confirmed again: same event 409, another event 404")
    void should_return404ForOtherEventAnd409ForSame_when_alreadyAttached() throws Exception {
        String uploadId = reserve(EVENT);
        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), target(EVENT), 200);

        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), target(OTHER_EVENT), 404);
        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), target(EVENT), 409);
        assertThat(photoRows()).isEqualTo(1);
    }

    private int uploadRows(String uploadId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM event_management_uploads WHERE upload_id = ?",
                Integer.class, uploadId);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("jpeg, png and webp up to 10 MB; another type or a larger file 400")
    void should_applyPhotoRules_when_reserving() throws Exception {
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.webp", "image/webp", 10, EVENT), 201);
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.png", "image/png", 10L * 1024 * 1024,
                EVENT), 201);
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.gif", "image/gif", 10, EVENT), 400);
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.svg", "image/svg+xml", 10, EVENT), 400);
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.jpg", "image/jpeg",
                10L * 1024 * 1024 + 1, EVENT), 400);
    }

    @Test
    @DisplayName("reserved for event A, confirmed for event B: 404, no photo, the upload stays PENDING")
    void should_return404_when_confirmedForAnotherEvent() throws Exception {
        String uploadId = reserve(EVENT);

        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), target(OTHER_EVENT), 404);

        assertThat(photoRows()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM event_management_uploads WHERE upload_id = ?",
                String.class, uploadId)).isEqualTo("pending");
    }

    @Test
    @DisplayName("without a target: 400 on create and on confirm")
    void should_return400_when_targetMissing() throws Exception {
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.jpg", "image/jpeg", 10, null), 400);

        String uploadId = reserve(EVENT);
        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), null, 400);
        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), organizer(), "{}", 400);
        assertThat(photoRows()).isZero();
    }

    @Test
    @DisplayName("a speaker 403, anonymous 401, an unknown event 404")
    void should_refuse_when_notOrganizerOrEventUnknown() throws Exception {
        json(post("/api/v1/uploads/event-photo"), user("anna.muster").roles("SPEAKER"),
                create("a.jpg", "image/jpeg", 10, EVENT), 403);
        mockMvc.perform(post("/api/v1/uploads/event-photo").contentType(MediaType.APPLICATION_JSON)
                .content(create("a.jpg", "image/jpeg", 10, EVENT))).andExpect(status().isUnauthorized());
        json(post("/api/v1/uploads/event-photo"), organizer(), create("a.jpg", "image/jpeg", 10, "BATbern1"), 404);

        String uploadId = reserve(EVENT);
        json(post("/api/v1/uploads/event-photo/{id}/confirm", uploadId), user("anna.muster").roles("SPEAKER"),
                target(EVENT), 403);
        assertThat(photoRows()).isZero();
    }

    @Test
    @DisplayName("the removed photo upload paths answer 405 (no alias, Story 17.11b)")
    void should_notServeOldPhotoUploadPaths() throws Exception {
        json(post("/api/v1/events/{code}/photos/upload-url", EVENT), organizer(),
                "{\"filename\": \"a.jpg\", \"contentType\": \"image/jpeg\", \"fileSize\": 10}", 405);
        json(post("/api/v1/events/{code}/photos/confirm", EVENT), organizer(),
                "{\"photoId\": \"3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b\", \"s3Key\": \"events/" + EVENT
                        + "/photos/x.jpg\"}", 405);
        assertThat(photoRows()).isZero();
    }
}
