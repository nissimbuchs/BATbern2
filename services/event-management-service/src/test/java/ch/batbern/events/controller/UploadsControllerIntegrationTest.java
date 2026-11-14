package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.UploadExpiryJob;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.11: the upload contract for {@code event-theme-image} in EMS, its own table
 * ({@code event_management_uploads}) instead of the CUMS {@code logos} table. Real controller, upload
 * service, handler and event controller; S3 is the Mockito mock of {@link TestAwsConfig}.
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes the rows.
 */
@Import(TestAwsConfig.class)
@DisplayName("Uploads: event-theme-image (Story 17.11)")
class UploadsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern9917";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UploadExpiryJob expiryJob;

    @BeforeEach
    void setUp() {
        removeRows();
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS);
        eventRepository.saveAndFlush(Event.builder().eventCode(EVENT).title("Upload Event").eventNumber(9917)
                .date(date).registrationDeadline(date.minus(7, ChronoUnit.DAYS)).venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern").venueCapacity(100).organizerUsername("orga.nizer")
                .currentAttendeeCount(0).eventType(EventType.EVENING).workflowState(EventWorkflowState.CREATED)
                .build());
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM event_management_uploads WHERE created_by IN ('orga.nizer', 'anna.muster')"
                + " OR upload_id LIKE 'exp-%'");
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT);
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

    private String reserve() throws Exception {
        return json(post("/api/v1/uploads/event-theme-image"), organizer(),
                "{\"fileName\": \"theme.jpg\", \"contentType\": \"image/jpeg\", \"size\": 4096}", 201)
                .path("uploadId").asText();
    }

    @Test
    @DisplayName("existing event: confirm with target eventCode sets the theme image at once")
    void should_setThemeImage_when_confirmedWithEventTarget() throws Exception {
        String uploadId = reserve();

        JsonNode done = json(post("/api/v1/uploads/event-theme-image/{id}/confirm", uploadId), organizer(),
                "{\"target\": {\"eventCode\": \"" + EVENT + "\"}}", 200);

        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        Event event = eventRepository.findByEventCode(EVENT).orElseThrow();
        assertThat(event.getThemeImageUploadId()).isEqualTo(uploadId);
        assertThat(event.getThemeImageUrl()).isEqualTo(done.path("fileUrl").asText())
                .endsWith("/logos/events/" + EVENT + "/theme-" + uploadId + ".jpg");
    }

    @Test
    @DisplayName("edit: a confirmed upload without target, then PATCH themeImageUploadId")
    void should_setThemeImage_when_eventPatchedWithConfirmedUpload() throws Exception {
        String uploadId = reserve();
        json(post("/api/v1/uploads/event-theme-image/{id}/confirm", uploadId), organizer(), null, 200);

        json(patch("/api/v1/events/{code}", EVENT), organizer(), "{\"themeImageUploadId\": \"" + uploadId + "\"}",
                200);

        assertThat(eventRepository.findByEventCode(EVENT).orElseThrow().getThemeImageUrl())
                .contains("/events/" + EVENT + "/theme-" + uploadId + ".jpg");
    }

    @Test
    @DisplayName("an unknown event 404, an upload that is only PENDING 409 on PATCH, a non-organizer 403")
    void should_refuse_when_targetUnknownOrUploadNotReady() throws Exception {
        json(post("/api/v1/uploads/event-theme-image"), organizer(),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10,"
                        + " \"target\": {\"eventCode\": \"BATbern1\"}}", 404);
        json(post("/api/v1/uploads/event-theme-image"), user("anna.muster").roles("SPEAKER"),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10}", 403);

        String pending = reserve();
        json(patch("/api/v1/events/{code}", EVENT), organizer(), "{\"themeImageUploadId\": \"" + pending + "\"}",
                409);
        assertThat(eventRepository.findByEventCode(EVENT).orElseThrow().getThemeImageUploadId()).isNull();
    }

    @Test
    @DisplayName("company-logo reaching EMS: 404; and EMS has no import")
    void should_return404_when_purposeOrImportNotOwned() throws Exception {
        json(post("/api/v1/uploads/company-logo"), organizer(),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10}", 404);
        json(post("/api/v1/uploads/event-theme-image/import"), organizer(),
                "{\"url\": \"https://example.com/a.png\"}", 404);
    }

    @Test
    @DisplayName("expiry: a PENDING upload past its expiry is deleted, a fresh one stays")
    void should_deleteExpiredUploads_when_expiryJobRuns() {
        jdbc.update("INSERT INTO event_management_uploads (upload_id, purpose, s3_key, file_extension, file_size,"
                + " content_type, status, expires_at) VALUES ('exp-old', 'event-theme-image', 'logos/temp/x.png',"
                + " 'png', 10, 'image/png', 'pending', now() - interval '1 minute'), ('exp-new', 'event-theme-image',"
                + " 'logos/temp/y.png', 'png', 10, 'image/png', 'pending', now() + interval '1 hour')");

        assertThat(expiryJob.purgeExpired().deleted()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForList("SELECT upload_id FROM event_management_uploads WHERE upload_id LIKE 'exp-%'",
                String.class)).containsExactly("exp-new");
    }
}
