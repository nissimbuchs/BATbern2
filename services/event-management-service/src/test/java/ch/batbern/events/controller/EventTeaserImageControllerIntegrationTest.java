package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTeaserImageRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventTeaserImageController.
 * Story 10.22: Event Teaser Images — AC8 (TDD)
 *
 * Uses real PostgreSQL via Testcontainers (AbstractIntegrationTest) with mocked S3 only.
 * Each test verifies actual DB state after HTTP calls (AC8: "integration test verifies DB state").
 * H2 fix: replaced @MockBean EventTeaserImageService with real service + @MockitoBean S3 beans.
 * M2 fix: added GET /events/{eventCode} test verifying teaserImages in EventResponse.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class EventTeaserImageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventTeaserImageRepository teaserImageRepository;

    @Autowired
    CacheManager cacheManager;

    @MockitoBean
    S3Presigner s3Presigner;

    @MockitoBean
    S3Client s3Client;

    private static final String EVENT_CODE = "BATbern997";

    @BeforeEach
    void setUp() {
        teaserImageRepository.deleteAll();
        eventRepository.deleteAll();
        createTestEvent();
        // Evict event cache so GET tests see fresh DB state (cache is shared across test methods)
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.evict(EVENT_CODE + "_none");
            }
        });
    }

    // ── upload-url ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST upload-url as ORGANIZER returns 200 with uploadUrl and s3Key - AC2")
    @WithMockUser(roles = "ORGANIZER")
    void generateUploadUrl_asOrganizer_returns200() throws Exception {
        PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
        when(mockPresigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/key?sig=x"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

        mockMvc.perform(post("/api/v1/events/{code}/teaser-images/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileName\":\"teaser.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST upload-url with disallowed content type returns 400 - H1")
    @WithMockUser(roles = "ORGANIZER")
    void generateUploadUrl_disallowedContentType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events/{code}/teaser-images/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\",\"fileName\":\"doc.pdf\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST upload-url as anonymous returns 403 - AC2")
    void generateUploadUrl_asAnonymous_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/{code}/teaser-images/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileName\":\"teaser.jpg\"}"))
                .andExpect(status().isForbidden());
    }

    // ── confirm ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST confirm persists row to DB and returns TeaserImageItem - AC2 + AC8 DB state")
    @WithMockUser(roles = "ORGANIZER")
    void confirmUpload_asOrganizer_persistsRowAndReturns200() throws Exception {
        String s3Key = "events/" + EVENT_CODE + "/teaser/" + UUID.randomUUID() + ".jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        mockMvc.perform(post("/api/v1/events/{code}/teaser-images/confirm", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"s3Key\":\"" + s3Key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.displayOrder").value(0))
                .andExpect(jsonPath("$.presentationPosition").value("AFTER_TOPIC_REVEAL"));

        // AC8: verify actual DB state
        assertThat(teaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(EVENT_CODE)).hasSize(1);
    }

    // ── delete ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE teaser image removes row from DB - AC3 + AC8 DB state")
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_asOrganizer_removesRowFromDb() throws Exception {
        EventTeaserImage image = createTeaserImage("events/" + EVENT_CODE + "/teaser/img.jpg", 0);

        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, image.getId()))
                .andExpect(status().isNoContent());

        // AC8: verify actual DB state
        assertThat(teaserImageRepository.findById(image.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE unknown imageId returns 404 - AC3")
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE as anonymous returns 403 - AC2")
    void deleteTeaserImage_asAnonymous_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID()))
                .andExpect(status().isForbidden());
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
    @DisplayName("PATCH as anonymous returns 403")
    void patchTeaserImage_asAnonymous_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{code}/teaser-images/{id}", EVENT_CODE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presentationPosition\":\"AFTER_WELCOME\"}"))
                .andExpect(status().isForbidden());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

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
