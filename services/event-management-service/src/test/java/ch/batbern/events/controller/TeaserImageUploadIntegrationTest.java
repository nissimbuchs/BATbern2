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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.11c: teaser images on the upload contract, purpose {@code teaser-image}, target
 * {@code eventCode} (required; {@code _global} for the global set), organizers only. The confirm
 * with the target creates the {@code event_teaser_images} row; at most 10 per scope, refused with
 * 409 on create and on confirm. Real controllers, upload service, handler and teaser service on
 * PostgreSQL; S3 is the Mockito mock of {@link TestAwsConfig} (every object exists, copies succeed).
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows. The
 * global set is shared by the whole database, so this class removes only its own global rows: those
 * it filled ({@value #FILL_PREFIX}…) and those its uploads created.
 */
@Import(TestAwsConfig.class)
@DisplayName("Uploads: teaser-image (Story 17.11c)")
class TeaserImageUploadIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9931";
    private static final String OTHER_EVENT = "BATbern9932";
    private static final String GLOBAL = "_global";
    private static final String FILL_PREFIX = "global/teaser/it-17-11c-";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        removeRows();
        saveEvent(EVENT, 9931);
        saveEvent(OTHER_EVENT, 9932);
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM event_teaser_images WHERE event_code IS NULL AND (s3_key LIKE ?"
                + " OR s3_key IN (SELECT s3_key FROM event_management_uploads WHERE purpose = 'teaser-image'"
                + " AND created_by IN ('orga.nizer', 'anna.muster')))", FILL_PREFIX + "%");
        jdbc.update("DELETE FROM event_management_uploads WHERE purpose = 'teaser-image'"
                + " AND created_by IN ('orga.nizer', 'anna.muster')");
        jdbc.update("DELETE FROM event_teaser_images WHERE event_code IN (?, ?)", EVENT, OTHER_EVENT);
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT, OTHER_EVENT);
    }

    private void saveEvent(String code, int number) {
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        eventRepository.saveAndFlush(Event.builder().eventCode(code).title("Teaser Event " + number)
                .eventNumber(number).date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum").venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100)
                .organizerUsername("orga.nizer").currentAttendeeCount(0).eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED).build());
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
        return json(post("/api/v1/uploads/teaser-image"), organizer(),
                create("Sponsoren Übersicht.PNG", "image/png", 2_000_000, eventCode), 201)
                .path("uploadId").asText();
    }

    private JsonNode confirm(String uploadId, String eventCode, int expectedStatus) throws Exception {
        return json(post("/api/v1/uploads/teaser-image/{id}/confirm", uploadId), organizer(), target(eventCode),
                expectedStatus);
    }

    private int teaserRows(String eventCode) {
        Integer n = eventCode == null
                ? jdbc.queryForObject("SELECT count(*) FROM event_teaser_images WHERE event_code IS NULL",
                        Integer.class)
                : jdbc.queryForObject("SELECT count(*) FROM event_teaser_images WHERE event_code = ?",
                        Integer.class, eventCode);
        return n == null ? 0 : n;
    }

    /** Fills the scope up to {@code total} images (the global set may hold other classes' rows). */
    private void fillTo(String eventCode, int total) {
        int existing = teaserRows(eventCode);
        assertThat(existing).as("the scope must have room for the test").isLessThanOrEqualTo(total);
        for (int i = existing; i < total; i++) {
            String key = (eventCode == null ? FILL_PREFIX : "events/" + eventCode + "/teaser/fill-") + i + ".jpg";
            jdbc.update("INSERT INTO event_teaser_images (id, event_code, s3_key, image_url, display_order,"
                    + " presentation_position, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?,"
                    + " 'AFTER_TOPIC_REVEAL', now())", eventCode, key, "https://cdn.batbern.ch/" + key, i);
        }
    }

    @Test
    @DisplayName("event teaser: reserve and confirm for the event; the image is listed and on the event")
    void should_createEventTeaser_when_confirmedForTheReservedEvent() throws Exception {
        // The event is cached before the upload: the confirm must clear the cache.
        json(get("/api/v1/events/{code}", EVENT), organizer(), null, 200);
        String uploadId = reserve(EVENT);

        JsonNode done = confirm(uploadId, EVENT, 200);

        String key = "events/" + EVENT + "/teaser/" + uploadId + ".png";
        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        assertThat(done.path("purpose").asText()).isEqualTo("teaser-image");
        assertThat(done.path("fileUrl").asText()).endsWith("/" + key);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM event_teaser_images WHERE event_code = ?", EVENT);
        assertThat(row.get("s3_key")).isEqualTo(key);
        assertThat(row.get("image_url")).isEqualTo(done.path("fileUrl").asText());
        assertThat(row.get("display_order")).isEqualTo(0);
        assertThat(row.get("presentation_position")).isEqualTo("AFTER_TOPIC_REVEAL");

        JsonNode list = json(get("/api/v1/events/{code}/teaser-images", EVENT), organizer(), null, 200);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("imageUrl").asText()).endsWith("/" + key);

        JsonNode event = json(get("/api/v1/events/{code}", EVENT), organizer(), null, 200);
        assertThat(event.path("teaserImages")).hasSize(1);
        assertThat(event.path("teaserImages").get(0).path("imageUrl").asText()).endsWith("/" + key);
    }

    @Test
    @DisplayName("a second teaser comes last in display order; .jpeg is stored as .jpg")
    void should_appendInDisplayOrder_when_secondTeaserConfirmed() throws Exception {
        confirm(reserve(EVENT), EVENT, 200);
        String uploadId = json(post("/api/v1/uploads/teaser-image"), organizer(),
                create("bühne.jpeg", "image/jpeg", 2048, EVENT), 201).path("uploadId").asText();

        confirm(uploadId, EVENT, 200);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM event_teaser_images WHERE s3_key = ?",
                "events/" + EVENT + "/teaser/" + uploadId + ".jpg");
        assertThat(row.get("display_order")).isEqualTo(1);
    }

    @Test
    @DisplayName("global teaser: target _global gives a row without event code at global/teaser/")
    void should_createGlobalTeaser_when_targetIsGlobal() throws Exception {
        String uploadId = reserve(GLOBAL);

        JsonNode done = confirm(uploadId, GLOBAL, 200);

        String key = "global/teaser/" + uploadId + ".png";
        assertThat(done.path("fileUrl").asText()).endsWith("/" + key);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM event_teaser_images WHERE s3_key = ?", key);
        assertThat(row.get("event_code")).isNull();
        assertThat(teaserRows(EVENT)).isZero();

        JsonNode list = json(get("/api/v1/events/{code}/teaser-images", GLOBAL), organizer(), null, 200);
        assertThat(list.findValuesAsText("imageUrl")).anyMatch(url -> url.endsWith("/" + key));
    }

    @Test
    @DisplayName("jpeg, png, webp and svg up to 10 MB; another type or a larger file 400")
    void should_applyTeaserRules_when_reserving() throws Exception {
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.webp", "image/webp", 10, EVENT), 201);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.svg", "image/svg+xml", 10, EVENT), 201);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.png", "image/png",
                10L * 1024 * 1024, EVENT), 201);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.gif", "image/gif", 10, EVENT), 400);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.pdf", "application/pdf", 10, EVENT),
                400);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.jpg", "image/jpeg",
                10L * 1024 * 1024 + 1, EVENT), 400);
    }

    @Test
    @DisplayName("10 teasers in the event: reserve 409 with the limit message; the global set is apart")
    void should_return409OnReserve_when_eventHasTenTeasers() throws Exception {
        fillTo(EVENT, 10);

        JsonNode refused = json(post("/api/v1/uploads/teaser-image"), organizer(),
                create("a.png", "image/png", 10, EVENT), 409);

        assertThat(refused.path("message").asText())
                .isEqualTo("Maximum of 10 teaser images reached for event: " + EVENT);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.png", "image/png", 10, GLOBAL), 201);
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.png", "image/png", 10, OTHER_EVENT),
                201);
    }

    @Test
    @DisplayName("10 global teasers: reserve 409 with the global limit message")
    void should_return409OnReserve_when_globalSetHasTenTeasers() throws Exception {
        fillTo(null, 10);

        JsonNode refused = json(post("/api/v1/uploads/teaser-image"), organizer(),
                create("a.png", "image/png", 10, GLOBAL), 409);

        assertThat(refused.path("message").asText()).isEqualTo("Maximum of 10 global teaser images reached");
    }

    @Test
    @DisplayName("the scope fills up after the reserve: confirm 409, no 11th row, the upload stays PENDING")
    void should_return409OnConfirm_when_scopeFilledAfterReserve() throws Exception {
        fillTo(EVENT, 9);
        String uploadId = reserve(EVENT);
        jdbc.update("INSERT INTO event_teaser_images (id, event_code, s3_key, image_url, display_order,"
                + " presentation_position, created_at) VALUES (gen_random_uuid(), ?, ?, ?, 9,"
                + " 'AFTER_TOPIC_REVEAL', now())", EVENT, "events/" + EVENT + "/teaser/late.jpg",
                "https://cdn.batbern.ch/events/" + EVENT + "/teaser/late.jpg");

        JsonNode refused = confirm(uploadId, EVENT, 409);

        assertThat(refused.path("message").asText())
                .isEqualTo("Maximum of 10 teaser images reached for event: " + EVENT);
        assertThat(teaserRows(EVENT)).isEqualTo(10);
        assertThat(uploadStatus(uploadId)).isEqualTo("pending");
        // The confirm copied the file to its final key before attach refused: the rollback deletes it.
        String finalKey = "events/" + EVENT + "/teaser/" + uploadId + ".png";
        verify(s3Client, atLeastOnce()).deleteObject(
                argThat((DeleteObjectRequest r) -> finalKey.equals(r.key())));
    }

    @Test
    @DisplayName("scope full: the contract's answers come first (other target 404, unknown upload 404)")
    void should_answerContractFirst_when_scopeFullOnConfirm() throws Exception {
        String uploadId = reserve(EVENT);
        fillTo(EVENT, 10);
        fillTo(OTHER_EVENT, 10);

        confirm(uploadId, OTHER_EVENT, 404);
        confirm("3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b", EVENT, 404);
        assertThat(uploadStatus(uploadId)).isEqualTo("pending");
    }

    @Test
    @DisplayName("concurrent confirms into one scope: the advisory lock lets exactly one take the 10th place")
    void should_admitExactlyOne_when_confirmsRaceForTheLastPlace() throws Exception {
        fillTo(EVENT, 9);
        List<String> uploads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            uploads.add(reserve(EVENT));
        }

        ExecutorService pool = Executors.newFixedThreadPool(uploads.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (String uploadId : uploads) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/uploads/teaser-image/{id}/confirm", uploadId)
                                    .with(organizer()).contentType(MediaType.APPLICATION_JSON).content(target(EVENT)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409, 409, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(teaserRows(EVENT)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT display_order) FROM event_teaser_images"
                + " WHERE event_code = ?", Integer.class, EVENT)).isEqualTo(10);
    }

    @Test
    @DisplayName("concurrent confirms into an empty scope: distinct display orders (no rows to lock)")
    void should_giveDistinctDisplayOrders_when_confirmsRaceInEmptyScope() throws Exception {
        List<String> uploads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            uploads.add(reserve(OTHER_EVENT));
        }
        ExecutorService pool = Executors.newFixedThreadPool(uploads.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (String uploadId : uploads) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/uploads/teaser-image/{id}/confirm", uploadId)
                                    .with(organizer()).contentType(MediaType.APPLICATION_JSON)
                                    .content(target(OTHER_EVENT)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForList("SELECT display_order FROM event_teaser_images WHERE event_code = ?"
                + " ORDER BY display_order", Integer.class, OTHER_EVENT)).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("reserved for event A, confirmed for event B or the global set: 404, no row, still PENDING")
    void should_return404_when_confirmedForAnotherTarget() throws Exception {
        String uploadId = reserve(EVENT);

        confirm(uploadId, OTHER_EVENT, 404);
        confirm(uploadId, GLOBAL, 404);

        assertThat(teaserRows(EVENT) + teaserRows(OTHER_EVENT) + teaserRows(null)).isZero();
        assertThat(uploadStatus(uploadId)).isEqualTo("pending");
    }

    @Test
    @DisplayName("without a target: 400 on create and on confirm")
    void should_return400_when_targetMissing() throws Exception {
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.jpg", "image/jpeg", 10, null), 400);

        String uploadId = reserve(EVENT);
        json(post("/api/v1/uploads/teaser-image/{id}/confirm", uploadId), organizer(), null, 400);
        json(post("/api/v1/uploads/teaser-image/{id}/confirm", uploadId), organizer(), "{}", 400);
        assertThat(teaserRows(EVENT)).isZero();
    }

    @Test
    @DisplayName("a speaker 403, anonymous 401, an unknown event 404")
    void should_refuse_when_notOrganizerOrEventUnknown() throws Exception {
        json(post("/api/v1/uploads/teaser-image"), user("anna.muster").roles("SPEAKER"),
                create("a.jpg", "image/jpeg", 10, EVENT), 403);
        mockMvc.perform(post("/api/v1/uploads/teaser-image").contentType(MediaType.APPLICATION_JSON)
                .content(create("a.jpg", "image/jpeg", 10, EVENT))).andExpect(status().isUnauthorized());
        json(post("/api/v1/uploads/teaser-image"), organizer(), create("a.jpg", "image/jpeg", 10, "BATbern1"), 404);

        String uploadId = reserve(EVENT);
        json(post("/api/v1/uploads/teaser-image/{id}/confirm", uploadId), user("anna.muster").roles("SPEAKER"),
                target(EVENT), 403);
        assertThat(teaserRows(EVENT)).isZero();
    }

    @Test
    @DisplayName("deleting the teaser removes its upload row too (an attached upload never expires)")
    void should_removeUploadRow_when_teaserDeleted() throws Exception {
        String uploadId = reserve(EVENT);
        confirm(uploadId, EVENT, 200);
        String imageId = jdbc.queryForObject("SELECT id::text FROM event_teaser_images WHERE event_code = ?",
                String.class, EVENT);
        assertThat(uploadRows(uploadId)).isEqualTo(1);

        json(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT, imageId), organizer(), null, 204);

        assertThat(teaserRows(EVENT)).isZero();
        assertThat(uploadRows(uploadId)).isZero();
    }

    @Test
    @DisplayName("the removed teaser upload paths answer 405 (no alias, Story 17.11c)")
    void should_notServeOldTeaserUploadPaths() throws Exception {
        json(post("/api/v1/events/{code}/teaser-images/upload-url", EVENT), organizer(),
                "{\"contentType\": \"image/jpeg\", \"fileName\": \"a.jpg\"}", 405);
        json(post("/api/v1/events/{code}/teaser-images/confirm", EVENT), organizer(),
                "{\"s3Key\": \"events/" + EVENT + "/teaser/x.jpg\"}", 405);
        assertThat(teaserRows(EVENT)).isZero();
    }

    private String uploadStatus(String uploadId) {
        return jdbc.queryForObject("SELECT status FROM event_management_uploads WHERE upload_id = ?",
                String.class, uploadId);
    }

    private int uploadRows(String uploadId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM event_management_uploads WHERE upload_id = ?",
                Integer.class, uploadId);
        return n == null ? 0 : n;
    }
}
