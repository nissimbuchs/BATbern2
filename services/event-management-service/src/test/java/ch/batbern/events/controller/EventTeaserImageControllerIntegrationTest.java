package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTeaserImageRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventTeaserImageController.
 * Story 10.22: Event Teaser Images — AC8 (TDD)
 *
 * Uses real PostgreSQL via Testcontainers (AbstractIntegrationTest); S3 is the Mockito mock of
 * {@link TestAwsConfig}. Each test verifies actual DB state after HTTP calls (AC8).
 * M2 fix: added GET /events/{eventCode} test verifying teaserImages in EventResponse.
 * Adding a teaser image is the upload contract since Story 17.11c ({@link TeaserImageUploadIntegrationTest}).
 *
 * <p>Not {@code @Transactional}: every request commits; {@code @AfterEach} removes this test's rows.
 */
@Import(TestAwsConfig.class)
class EventTeaserImageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventTeaserImageRepository teaserImageRepository;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    S3Client s3Client;

    private static final String EVENT_CODE = "BATbern997";

    @BeforeEach
    void setUp() {
        removeRows();
        createTestEvent();
        // Clear event caches so GET tests see fresh DB state (cache is shared across test methods).
        // Clear rather than evict one key: the event cache key carries an audience suffix
        // (organizer vs public), so a hand-built key no longer matches.
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    // ── delete ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE teaser image removes row from DB - AC3 + AC8 DB state")
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_asOrganizer_removesRowFromDb() throws Exception {
        EventTeaserImage image = createTeaserImage("events/" + EVENT_CODE + "/teaser/img.jpg", 0);

        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, image.getId()))
                .andExpect(status().isNoContent());

        // AC8: verify actual DB state, and the S3 object goes too
        assertThat(teaserImageRepository.findById(image.getId())).isEmpty();
        verify(s3Client, atLeastOnce()).deleteObject(
                argThat((DeleteObjectRequest r) -> ("events/" + EVENT_CODE + "/teaser/img.jpg").equals(r.key())));
    }

    @Test
    @DisplayName("DELETE a global image (_global) removes it, the event's images stay")
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_global_removesOnlyTheGlobalRow() throws Exception {
        EventTeaserImage eventImage = createTeaserImage("events/" + EVENT_CODE + "/teaser/keep.jpg", 0);
        EventTeaserImage global = new EventTeaserImage();
        global.setEventCode(null);
        global.setS3Key("global/teaser/it-controller-" + UUID.randomUUID() + ".jpg");
        global.setImageUrl("https://cdn.batbern.ch/" + global.getS3Key());
        global = teaserImageRepository.save(global);

        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", "_global", global.getId()))
                .andExpect(status().isNoContent());

        assertThat(teaserImageRepository.findById(global.getId())).isEmpty();
        assertThat(teaserImageRepository.findById(eventImage.getId())).isPresent();
    }

    @Test
    @DisplayName("DELETE an event's image through _global is 404: scopes do not mix")
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_eventImageViaGlobal_returns404() throws Exception {
        EventTeaserImage image = createTeaserImage("events/" + EVENT_CODE + "/teaser/img.jpg", 0);

        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", "_global", image.getId()))
                .andExpect(status().isNotFound());

        assertThat(teaserImageRepository.findById(image.getId())).isPresent();
    }

    @Test
    @DisplayName("DELETE unknown imageId returns 404 - AC3")
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE as anonymous returns 401 - AC2")
    void deleteTeaserImage_asAnonymous_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET event with teaserImages (M2) ─────────────────────────────────────────

    @Test
    @DisplayName("GET /events/{eventCode} returns teaserImages array in displayOrder - M2 + AC4")
    @WithMockUser(roles = "ORGANIZER")
    void getEvent_returnsTeaserImagesInDisplayOrder() throws Exception {
        // Given: two teaser images in reverse insertion order to verify sorting
        createTeaserImage("events/" + EVENT_CODE + "/teaser/b.jpg", 1);
        createTeaserImage("events/" + EVENT_CODE + "/teaser/a.jpg", 0);

        mockMvc.perform(get("/api/v1/events/{code}", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teaserImages").isArray())
                .andExpect(jsonPath("$.teaserImages.length()").value(2))
                .andExpect(jsonPath("$.teaserImages[0].displayOrder").value(0))
                .andExpect(jsonPath("$.teaserImages[0].presentationPosition").value("AFTER_TOPIC_REVEAL"))
                .andExpect(jsonPath("$.teaserImages[1].displayOrder").value(1));
    }

    @Test
    @DisplayName("GET /events/{eventCode} returns empty teaserImages when none uploaded - AC5")
    @WithMockUser(roles = "ORGANIZER")
    void getEvent_returnsEmptyTeaserImages_whenNoneUploaded() throws Exception {
        mockMvc.perform(get("/api/v1/events/{code}", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teaserImages").isEmpty());
    }

    // ── patch (presentation position) ────────────────────────────────────────────

    @Test
    @DisplayName("PATCH teaser image updates presentationPosition in DB and response - AC (position)")
    @WithMockUser(roles = "ORGANIZER")
    void patchTeaserImage_asOrganizer_updatesPresentationPosition() throws Exception {
        EventTeaserImage image = createTeaserImage("events/" + EVENT_CODE + "/teaser/img.jpg", 0);

        mockMvc.perform(patch("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, image.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presentationPosition\":\"AFTER_WELCOME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(image.getId().toString()))
                .andExpect(jsonPath("$.presentationPosition").value("AFTER_WELCOME"));

        // Verify DB state
        EventTeaserImage dbState = teaserImageRepository.findById(image.getId()).orElseThrow();
        assertThat(dbState.getPresentationPosition()).isEqualTo("AFTER_WELCOME");
    }

    @Test
    @DisplayName("PATCH unknown imageId returns 404")
    @WithMockUser(roles = "ORGANIZER")
    void patchTeaserImage_withUnknownId_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presentationPosition\":\"AFTER_WELCOME\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH as anonymous returns 401")
    void patchTeaserImage_asAnonymous_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presentationPosition\":\"AFTER_WELCOME\"}"))
                .andExpect(status().isUnauthorized());
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void removeRows() {
        jdbc.update("DELETE FROM event_teaser_images WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM event_teaser_images WHERE event_code IS NULL"
                + " AND s3_key LIKE 'global/teaser/it-controller-%'");
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
    }

    private void createTestEvent() {
        Event event = new Event();
        event.setEventNumber(997);
        event.setEventCode(EVENT_CODE);
        event.setTitle("BATbern 997 Teaser Image Test");
        event.setDescription("Integration test event for Story 10.22");
        event.setEventType(EventType.EVENING);
        event.setDate(Instant.parse("2026-09-15T17:00:00Z"));
        event.setRegistrationDeadline(Instant.parse("2026-09-10T23:59:59Z"));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Bern, Switzerland");
        event.setVenueCapacity(200);
        event.setOrganizerUsername("test.organizer");
        event.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        eventRepository.save(event);
    }

    private EventTeaserImage createTeaserImage(String s3Key, int displayOrder) {
        EventTeaserImage img = new EventTeaserImage();
        img.setEventCode(EVENT_CODE);
        img.setS3Key(s3Key);
        img.setImageUrl("https://cdn.batbern.ch/" + s3Key);
        img.setDisplayOrder(displayOrder);
        img.setCreatedAt(OffsetDateTime.now());
        return teaserImageRepository.save(img);
    }
}
